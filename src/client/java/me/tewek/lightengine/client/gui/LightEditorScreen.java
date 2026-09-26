package me.tewek.lightengine.client.gui;

import me.tewek.lightengine.Config;
import me.tewek.lightengine.LightEngine;
import me.tewek.lightengine.LightProfileRegistry;
import me.tewek.lightengine.client.EditorSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.loader.api.FabricLoader;
import org.joml.Matrix3x2fStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-game light editor. Left: radius + ignore flag + values + change log.
 * Center: analytic diorama preview. Right: tile palette with search.
 * Top-right X asks for confirmation when dirty. No vanilla widgets used.
 *
 * <p>26.x notes: draws through {@link GuiGraphicsExtractor}
 * ({@code text}/{@code centeredText}/{@code item}); overlay layers use
 * {@code nextStratum()} instead of a z-translated pose; input arrives as
 * {@code MouseButtonEvent}/{@code KeyEvent}/{@code CharacterEvent} records
 * (SDL codes) and is unpacked to the internal {@link LeWidget} dispatch.
 *
 * <p>Alpha3 features: sectioned tile palette (per-mod buckets + trailing
 * "added" section), tile context menu (reset / copy /give), scene settings
 * popup, full update popup (changelog, installed version, restart-to-apply),
 * slider release logging with dedup, scene ctrl+wheel zoom and ctrl+drag pan.
 */
public class LightEditorScreen extends Screen {
    /** SDL left mouse button ({@code SDL_BUTTON_LEFT}). */
    public static final int LEFT_BUTTON = 1;

    private final Screen parent;

    private Identifier activeId = Identifier.parse("minecraft:torch");
    private int sliderValue = 14;
    private boolean showLevels = false;
    private boolean dirty;
    private final Map<Identifier, LightProfileRegistry.Profile> dirtyProfiles = new LinkedHashMap<>();
    private final List<Identifier> paletteIds = new ArrayList<>();
    /** Blocks added through the "+" search (shown in the trailing section). */
    private final Set<Identifier> addedIds = new LinkedHashSet<>();
    private final List<String> changeLog = new ArrayList<>();
    private String lastLogLine = "";

    private final List<LeWidget> widgets = new ArrayList<>();
    private final Deque<LePopup> popups = new ArrayDeque<>();
    private LeTextField focusedField;
    private LeSlider radiusSlider;
    private LeTileGrid tileGrid;
    private LeTextField panelSearch;

    private int sceneX;
    private int sceneY;
    private int sceneW;
    private int sceneH;
    private int logX;
    private int logY;
    private int logW;
    private int logH;
    private LePopup updatePopup;
    private LeIconButton updateButton;
    private LePopup settingsPopup;
    private LeIconButton sceneSettingsButton;
    private LePopup tileMenu;
    private double lastMouseX;
    private double lastMouseY;
    /** Tracked Ctrl state for ctrl+wheel zoom (scroll events carry no modifiers). SDL_KMOD_* mask. */
    private boolean ctrlDown;
    private static final int KMOD_LCTRL = 0x0040;
    private static final int KMOD_RCTRL = 0x0080;
    private UpdateChecker.State lastUpdateState = UpdateChecker.State.IDLE;
    private LeProgressBar updateBar;
    private LeLabel updateStatus;

    public LightEditorScreen(Screen parent) {
        super(Component.translatable("lightengine.configuration.title"));
        this.parent = parent;
        this.sliderValue = this.effectiveRadius(this.activeId);
    }

    @Override
    protected void init() {
        this.widgets.clear();
        this.popups.clear();
        this.focusedField = null;
        int w = this.width;
        int h = this.height;

        this.addButton(new LeIconButton(w - 28, 4, 20, LeIconButton::drawCross, b -> this.onX()));
        this.updateButton = new LeIconButton(w - 56, 4, 20, LeIconButton::drawDownload, b -> this.openUpdate());
        this.widgets.add(this.updateButton);
        if (UpdateChecker.getState() == UpdateChecker.State.IDLE && Config.isCheckUpdates()) {
            UpdateChecker.checkCurrent(LightEngine.LOADER);
        }

        int lx = 10;
        int lw = 150;
        this.radiusSlider = new LeSlider(lx + 8, 54, lw - 16, 20, Component.empty(),
                0, 255, this.sliderValue, this::onSlider, String::valueOf);
        this.radiusSlider.setOnRelease(this::logSliderRelease);
        this.widgets.add(this.radiusSlider);
        this.signalToggle = new LeToggle(lx + 8, 82, lw - 16, 20,
                Component.translatable("lightengine.editor.ignore_conditions"),
                false, this::onIgnore);
        this.widgets.add(this.signalToggle);
        this.logX = lx + 8;
        this.logW = lw - 16;
        this.logH = Math.max(30, (h - 40 - 112) / 2);
        this.logY = h - 40 - 6 - this.logH;

        this.sceneX = 170;
        this.sceneY = 28;
        this.sceneW = Math.max(120, w - 340);
        this.sceneH = Math.max(120, h - 68);
        this.sceneSettingsButton = new LeIconButton(this.sceneX + 5, this.sceneY + 5, 18,
                LeIconButton::drawGear, b -> this.openSettings());
        this.widgets.add(this.sceneSettingsButton);

        int rx = w - 160;
        int rw = 150;
        this.panelSearch = new LeTextField(rx + 5, 48, rw - 10, 20,
                Component.translatable("lightengine.editor.search_hint"), query -> this.refreshTiles());
        this.panelSearch.setPlaceholder(Component.translatable("lightengine.editor.search_hint").getString());
        this.widgets.add(this.panelSearch);
        this.tileGrid = new LeTileGrid(rx + 5, 72, rw - 10, h - 112, 24, 5, this::onTileSelect);
        this.tileGrid.setOnSecondary(this::openTileMenu);
        this.widgets.add(this.tileGrid);
        this.widgets.add(new LeButton(w / 2 - 75, h - 28, 150, 20,
                Component.translatable("lightengine.editor.apply"), b -> this.applyAll()));

        this.rebuildPalette();
        this.radiusSlider.setValue(this.effectiveRadius(this.activeId), false);
        this.sliderValue = this.radiusSlider.getValue();
        this.signalToggle.setChecked(this.ignoreOf(this.activeId));
        this.refreshTiles();
    }

    private LeToggle signalToggle;

    /**
     * Best-effort item icon. On the title screen (no world loaded) data
     * component bindings may be absent and {@code new ItemStack(...)} throws
     * "Components not bound yet". Worse, a stack bound to an empty map still
     * renders nothing: the item model resolver skips stacks without an
     * {@code ITEM_MODEL} component. So unbound holders are pre-bound with
     * just that component (model id = item id, the vanilla convention;
     * models bake from client resources at startup). Vanilla re-binds the
     * real components on datapack sync, overwriting this. {@code bindComponents}
     * is public in 26.x, so no mixin is needed. Falls back to no icon if
     * anything still fails (callers then draw a letter fallback).
     */
    static ItemStack safeIcon(Block block) {
        try {
            net.minecraft.world.item.Item item = block.asItem();
            net.minecraft.core.Holder.Reference<net.minecraft.world.item.Item> holder =
                    item.builtInRegistryHolder();
            if (!holder.areComponentsBound()) {
                net.minecraft.resources.Identifier itemId =
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
                net.minecraft.core.component.DataComponentMap prebound =
                        itemId == null ? net.minecraft.core.component.DataComponentMap.EMPTY
                                : net.minecraft.core.component.DataComponentMap.builder()
                                        .set(net.minecraft.core.component.DataComponents.ITEM_MODEL, itemId)
                                        .build();
                holder.bindComponents(prebound);
            }
            ItemStack stack = new ItemStack(item);
            return stack.isEmpty() ? null : stack;
        } catch (Exception ex) {
            return null;
        }
    }

    private void addButton(LeButton button) {
        this.widgets.add(button);
    }

    // ----- state -----

    private int vanillaOf(Identifier id) {
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        if (block == null) {
            return 0;
        }
        return block.defaultBlockState().getLightEmission();
    }

    private int effectiveRadius(Identifier id) {
        LightProfileRegistry.Profile profile = LightProfileRegistry.get(id);
        if (profile != null) {
            return profile.lightRadius();
        }
        return this.vanillaOf(id);
    }

    private void setActive(Identifier id) {
        if (id == null) {
            return;
        }
        this.activeId = id;
        this.ensurePalette(id);
        this.radiusSlider.setValue(this.effectiveRadius(id), false);
        this.sliderValue = this.radiusSlider.getValue();
        this.signalToggle.setChecked(this.ignoreOf(id));
        this.refreshTiles();
    }

    private void onSlider(int value) {
        this.sliderValue = value;
        this.dirty = true;
        this.dirtyProfiles.put(this.activeId,
                new LightProfileRegistry.Profile(value, this.signalToggle.isChecked()));
        this.refreshTiles();
    }

    private void logSliderRelease() {
        String line = this.activeId + " -> " + this.sliderValue
                + (this.signalToggle.isChecked() ? "*" : "");
        if (!line.equals(this.lastLogLine)) {
            this.lastLogLine = line;
            this.logChange(line);
        }
    }

    private void onIgnore(boolean ignore) {
        this.dirty = true;
        this.dirtyProfiles.put(this.activeId,
                new LightProfileRegistry.Profile(this.sliderValue, ignore));
        this.logChange(this.activeId + (ignore ? " ignore+" : " ignore-"));
        this.refreshTiles();
    }

    private boolean ignoreOf(Identifier id) {
        LightProfileRegistry.Profile profile = LightProfileRegistry.get(id);
        return profile != null && profile.ignoreConditions();
    }

    private void logChange(String line) {
        this.changeLog.add(line);
        while (this.changeLog.size() > 60) {
            this.changeLog.remove(0);
        }
    }

    private void ensurePalette(Identifier id) {
        if (!this.paletteIds.contains(id)) {
            this.paletteIds.add(id);
            this.rebuildPalette();
        }
    }

    private void rebuildPalette() {
        List<Identifier> ids = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null) {
                continue;
            }
            if (LightProfileRegistry.isProfiled(block) || block.defaultBlockState().getLightEmission() > 0) {
                ids.add(id);
            }
        }
        for (Identifier extra : this.paletteIds) {
            if (!ids.contains(extra)) {
                ids.add(extra);
            }
        }
        ids.sort((a, b) -> {
            boolean pa = LightProfileRegistry.get(a) != null;
            boolean pb = LightProfileRegistry.get(b) != null;
            if (pa != pb) {
                return pa ? -1 : 1;
            }
            return a.toString().compareTo(b.toString());
        });
        this.paletteIds.clear();
        this.paletteIds.addAll(ids);
        if (!this.paletteIds.contains(this.activeId) && !this.paletteIds.isEmpty()) {
            this.activeId = this.paletteIds.get(0);
        }
        this.refreshTiles();
    }

    private static String modTitle(String namespace) {
        if ("minecraft".equals(namespace)) {
            return "Minecraft";
        }
        try {
            return FabricLoader.getInstance().getModContainer(namespace)
                    .map(c -> c.getMetadata().getName()).orElse(namespace);
        } catch (Exception ex) {
            return namespace;
        }
    }

    private static int compareNamespaces(String a, String b) {
        boolean ma = "minecraft".equals(a);
        boolean mb = "minecraft".equals(b);
        if (ma != mb) {
            return ma ? -1 : 1;
        }
        return modTitle(a).compareToIgnoreCase(modTitle(b));
    }

    private void refreshTiles() {
        String query = this.panelSearch == null ? "" : this.panelSearch.getValue();
        Map<String, List<Identifier>> buckets = new LinkedHashMap<>();
        for (Identifier id : this.paletteIds) {
            Block block = BuiltInRegistries.BLOCK.getValue(id);
            if (block == null) {
                continue;
            }
            String name = block.getName().getString();
            String en = SearchUtil.englishName(block.getDescriptionId());
            if (!SearchUtil.matches(query, id.toString(), name, en)) {
                continue;
            }
            buckets.computeIfAbsent(id.getNamespace(), k -> new ArrayList<>()).add(id);
        }
        List<String> namespaces = new ArrayList<>(buckets.keySet());
        namespaces.sort(LightEditorScreen::compareNamespaces);
        List<LeTileGrid.Cell> cells = new ArrayList<>();
        for (String ns : namespaces) {
            List<Identifier> ids = buckets.get(ns);
            ids.sort((a, b) -> {
                boolean pa = LightProfileRegistry.get(a) != null;
                boolean pb = LightProfileRegistry.get(b) != null;
                if (pa != pb) {
                    return pa ? -1 : 1;
                }
                return a.toString().compareTo(b.toString());
            });
            cells.add(LeTileGrid.Cell.header(modTitle(ns)));
            for (Identifier id : ids) {
                if (this.addedIds.contains(id)) {
                    continue;
                }
                Block block = BuiltInRegistries.BLOCK.getValue(id);
                if (block == null) {
                    continue;
                }
                String name = block.getName().getString();
                ItemStack stack = safeIcon(block);
                int shown = id.equals(this.activeId) ? this.sliderValue : this.effectiveRadius(id);
                cells.add(new LeTileGrid.Cell(stack, name, id.toString(),
                        String.valueOf(shown), true, "", false, id));
            }
        }
        List<Identifier> added = new ArrayList<>();
        for (Identifier id : this.addedIds) {
            Block block = BuiltInRegistries.BLOCK.getValue(id);
            if (block == null) {
                continue;
            }
            String name = block.getName().getString();
            String en = SearchUtil.englishName(block.getDescriptionId());
            if (!SearchUtil.matches(query, id.toString(), name, en)) {
                continue;
            }
            added.add(id);
        }
        if (!added.isEmpty()) {
            added.sort((a, b) -> a.toString().compareTo(b.toString()));
        }
        cells.add(LeTileGrid.Cell.header(
                Component.translatable("lightengine.editor.section_added").getString()));
        for (Identifier id : added) {
            Block block = BuiltInRegistries.BLOCK.getValue(id);
            if (block == null) {
                continue;
            }
            String name = block.getName().getString();
            ItemStack stack = safeIcon(block);
            int shown = id.equals(this.activeId) ? this.sliderValue : this.effectiveRadius(id);
            cells.add(new LeTileGrid.Cell(stack, name, id.toString(),
                    String.valueOf(shown), true, "", false, id));
        }
        cells.add(new LeTileGrid.Cell(null, "+", "", "", false, "", false, LeTileGrid.ADD));
        this.tileGrid.setCells(cells);
        for (int i = 0; i < cells.size(); i++) {
            if (this.activeId.equals(cells.get(i).ref)) {
                this.tileGrid.setSelected(i);
                break;
            }
        }
        LightProfileRegistry.Profile activeProfile = LightProfileRegistry.get(this.activeId);
        this.radiusSlider.setMarkedValue(activeProfile == null ? null : activeProfile.lightRadius());
    }

    private void onTileSelect(int index) {
        if (index < 0 || index >= this.tileGrid.getCells().size()) {
            return;
        }
        Object ref = this.tileGrid.getCells().get(index).ref;
        if (ref == LeTileGrid.ADD) {
            this.openSearch();
        } else if (ref instanceof Identifier id) {
            this.setActive(id);
        }
    }

    private void openTileMenu(int index) {
        if (index < 0 || index >= this.tileGrid.getCells().size()) {
            return;
        }
        Object ref = this.tileGrid.getCells().get(index).ref;
        if (!(ref instanceof Identifier id)) {
            return;
        }
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        if (block == null) {
            return;
        }
        int menuW = 120;
        int menuH = 64;
        int mx = (int) Math.clamp(this.lastMouseX, 4, this.width - menuW - 4);
        int my = (int) Math.clamp(this.lastMouseY - 8, 4, this.height - menuH - 4);
        String rawTitle = block.getName().getString();
        int maxTitle = menuW - 20;
        while (!rawTitle.isEmpty() && this.font.width(rawTitle) > maxTitle) {
            rawTitle = rawTitle.substring(0, rawTitle.length() - 1);
        }
        LePopup menu = new LePopup(menuW, menuH, Component.literal(rawTitle));
        menu.setPosition(mx, my);
        menu.setDim(false);
        menu.setCloseOnOutsideClick(true);
        menu.add(new LeButton(mx + 10, my + 22, 100, 16,
                Component.translatable("lightengine.editor.reset"), b -> {
                    menu.close();
                    this.doReset(id);
                }));
        menu.add(new LeButton(mx + 10, my + 42, 100, 16,
                Component.translatable("lightengine.editor.copy_give"), b -> {
                    menu.close();
                    this.doCopyGive(id);
                }));
        this.tileMenu = menu;
        this.popups.push(menu);
    }

    private void doReset(Identifier id) {
        if (id == null) {
            return;
        }
        this.dirtyProfiles.remove(id);
        if (this.dirtyProfiles.isEmpty()) {
            this.dirty = false;
        }
        EditorSync.removeProfile(id);
        this.logChange(id + " reset");
        this.rebuildPalette();
        this.radiusSlider.setValue(this.effectiveRadius(this.activeId), false);
        this.sliderValue = this.radiusSlider.getValue();
        this.signalToggle.setChecked(this.ignoreOf(this.activeId));
        this.refreshTiles();
    }

    private void doCopyGive(Identifier id) {
        if (id == null) {
            return;
        }
        String command = "/give @p " + id;
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(command);
        } catch (Exception ignored) {
        }
        this.logChange("copied " + command);
        try {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal(command));
            }
        } catch (Exception ignored) {
        }
    }

    private void applyAll() {
        if (this.dirtyProfiles.isEmpty()) {
            return;
        }
        int count = this.dirtyProfiles.size();
        EditorSync.pushDirty(this.dirtyProfiles);
        this.dirtyProfiles.clear();
        this.dirty = false;
        this.logChange("applied " + count);
        this.rebuildPalette();
        this.radiusSlider.setValue(this.effectiveRadius(this.activeId), false);
        this.sliderValue = this.radiusSlider.getValue();
        this.signalToggle.setChecked(this.ignoreOf(this.activeId));
        this.refreshTiles();
    }

    private void onX() {
        if (this.dirty || !this.dirtyProfiles.isEmpty()) {
            LePopup confirm = new LePopup(240, 120, Component.translatable("lightengine.editor.confirm_title"));
            int px = confirm.panelX(this.width);
            int py = confirm.panelY(this.height);
            confirm.add(new LeButton(px + 15, py + 62, 100, 20,
                    Component.translatable("lightengine.editor.cancel"), b -> this.closeAll()));
            confirm.add(new LeButton(px + 125, py + 62, 100, 20,
                    Component.translatable("lightengine.editor.apply"), b -> {
                        this.applyAll();
                        this.closeAll();
                    }));
            this.popups.push(confirm);
        } else {
            this.closeScreen();
        }
    }

    private void closeAll() {
        this.popups.clear();
        this.closeScreen();
    }

    private void closeScreen() {
        this.minecraft.gui.setScreen(this.parent);
    }

    // ----- scene settings -----

    private void openSettings() {
        if (this.settingsPopup != null && this.settingsPopup.isOpen()) {
            return;
        }
        LePopup popup = new LePopup(220, 112, Component.translatable("lightengine.editor.settings"));
        this.settingsPopup = popup;
        int px = popup.panelX(this.width);
        int py = popup.panelY(this.height);
        popup.add(new LeIconButton(px + 220 - 26, py + 6, 18, LeIconButton::drawCross, b -> popup.close()));
        popup.add(new LeToggle(px + 15, py + 38, 190, 20,
                Component.translatable("lightengine.editor.show_levels"),
                this.showLevels, v -> this.showLevels = v));
        popup.add(new LeToggle(px + 15, py + 62, 190, 20,
                Component.translatable("lightengine.editor.settings_animation"),
                EditorScene.isAnimationEnabled(), v -> EditorScene.setAnimationEnabled(v)));
        this.popups.push(popup);
    }

    // ----- update checker -----

    private void openUpdate() {
        if (this.updatePopup != null && this.updatePopup.isOpen()) {
            return;
        }
        LePopup popup = new LePopup(360, 250, Component.translatable("lightengine.editor.update"));
        this.updatePopup = popup;
        this.lastUpdateState = UpdateChecker.State.IDLE;
        this.updateBar = null;
        this.updateStatus = null;
        this.popups.push(popup);
        UpdateChecker.State st = UpdateChecker.getState();
        if (st == UpdateChecker.State.IDLE || st == UpdateChecker.State.FAILED) {
            UpdateChecker.checkCurrent(LightEngine.LOADER);
        }
        this.syncUpdatePopup();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.updateButton != null) {
            this.updateButton.setDot(UpdateChecker.getState() == UpdateChecker.State.AVAILABLE);
        }
        if (this.updatePopup == null) {
            return;
        }
        if (this.topPopup() != this.updatePopup) {
            this.updatePopup = null;
            return;
        }
        this.syncUpdatePopup();
    }

    private void syncUpdatePopup() {
        LePopup popup = this.updatePopup;
        if (popup == null) {
            return;
        }
        UpdateChecker.State state = UpdateChecker.getState();
        if (state == UpdateChecker.State.DOWNLOADING) {
            if (this.updateBar != null) {
                double frac = UpdateChecker.fraction();
                this.updateBar.setFraction(frac);
                long total = UpdateChecker.assetSize();
                if (total > 0) {
                    int pct = (int) (frac * 100.0);
                    this.updateBar.setText(pct + "%");
                    if (this.updateStatus != null) {
                        this.updateStatus.setText(Component.translatable(
                                "lightengine.editor.downloading", pct + "%"));
                    }
                } else {
                    this.updateBar.setText((UpdateChecker.downloaded() / 1024) + " KB");
                }
            }
            return;
        }
        if (state == this.lastUpdateState) {
            return;
        }
        this.lastUpdateState = state;
        this.fillUpdatePopup();
    }

    /** Changelog list: plain wrapped text, always fully shown. */
    private void addUpdateLog(LePopup popup, int inner, int py, int innerW, int px) {
        Font font = Minecraft.getInstance().font;
        List<String> wrapped = wrapLines(UpdateChecker.changelog(), font, innerW - 16);
        List<LeScrollList.Entry> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(wrapped.size(), 150); i++) {
            rows.add(new LeScrollList.Entry(null, wrapped.get(i), "", null));
        }
        LeScrollList log = new LeScrollList(inner, py + 46, innerW, 168, 14, index -> {
        });
        log.setPlain(true);
        log.setEntries(rows);
        popup.add(log);
    }

    /** Word-wrap, preserving blank lines; overlong single words break by chars. */
    private static List<String> wrapLines(String text, Font font, int maxW) {
        List<String> out = new ArrayList<>();
        for (String raw : text.replace("\r", "").split("\n", -1)) {
            StringBuilder cur = new StringBuilder();
            for (String word : raw.split(" ")) {
                if (word.isEmpty()) {
                    continue;
                }
                String trial = cur.length() == 0 ? word : cur + " " + word;
                if (font.width(trial) <= maxW) {
                    cur = new StringBuilder(trial);
                } else {
                    if (cur.length() > 0) {
                        out.add(cur.toString());
                    }
                    String rest = word;
                    while (!rest.isEmpty() && font.width(rest) > maxW) {
                        int k = rest.length() - 1;
                        while (k > 1 && font.width(rest.substring(0, k)) > maxW) {
                            k--;
                        }
                        out.add(rest.substring(0, k));
                        rest = rest.substring(k);
                    }
                    cur = new StringBuilder(rest);
                }
            }
            out.add(cur.toString());
        }
        return out;
    }

    private void fillUpdatePopup() {
        LePopup popup = this.updatePopup;
        if (popup == null) {
            return;
        }
        UpdateChecker.State state = UpdateChecker.getState();
        popup.children.clear();
        popup.add(new LeIconButton(popup.panelX(this.width) + 360 - 26,
                popup.panelY(this.height) + 6, 18, LeIconButton::drawCross, b -> popup.close()));
        this.updateBar = null;
        this.updateStatus = null;
        int px = popup.panelX(this.width);
        int py = popup.panelY(this.height);
        int inner = px + 15;
        int innerW = 330;
        switch (state) {
            case CHECKING, IDLE -> popup.add(new LeLabel(inner, py + 40, innerW,
                    Component.translatable("lightengine.editor.checking"), LeTheme.TEXT, true));
            case UPTODATE -> {
                String installed = UpdateChecker.remoteVersion();
                if (installed.isEmpty()) {
                    installed = LightEngine.VERSION;
                }
                popup.add(new LeLabel(inner, py + 28, innerW,
                        Component.translatable("lightengine.editor.installed_version", installed),
                        LeTheme.ACCENT, true));
                this.addUpdateLog(popup, inner, py, innerW, px);
                LeButton updateBtn = new LeButton(px + 105, py + 222, 150, 20,
                        Component.translatable("lightengine.editor.update_now"), b -> UpdateChecker.download());
                updateBtn.setActive(false);
                popup.add(updateBtn);
            }
            case AVAILABLE -> {
                popup.add(new LeLabel(inner, py + 28, innerW,
                        Component.translatable("lightengine.editor.new_version", UpdateChecker.remoteVersion()),
                        LeTheme.ACCENT, true));
                this.addUpdateLog(popup, inner, py, innerW, px);
                popup.add(new LeButton(px + 105, py + 222, 150, 20,
                        Component.translatable("lightengine.editor.update_now"), b -> UpdateChecker.download()));
            }
            case DOWNLOADING -> {
                this.updateStatus = new LeLabel(inner, py + 40, innerW,
                        Component.translatable("lightengine.editor.downloading", "0%"), LeTheme.TEXT, true);
                popup.add(this.updateStatus);
                this.updateBar = new LeProgressBar(inner, py + 60, innerW, 20);
                this.updateBar.setFraction(-1.0);
                popup.add(this.updateBar);
            }
            case DONE -> {
                popup.add(new LeLabel(inner, py + 40, innerW,
                        Component.translatable("lightengine.editor.need_restart"), LeTheme.WARN, true));
                this.updateStatus = new LeLabel(inner, py + 62, innerW, Component.literal(""), LeTheme.BAD, true);
                popup.add(this.updateStatus);
                popup.add(new LeButton(px + 105, py + 196, 150, 20,
                        Component.translatable("lightengine.editor.restart"), b -> {
                            if (!UpdateChecker.restartToApplyUpdate()) {
                                this.updateStatus.setText(Component.translatable(
                                        "lightengine.editor.check_failed", UpdateChecker.error()));
                            }
                        }));
            }
            case FAILED -> {
                popup.add(new LeLabel(inner, py + 40, innerW,
                        Component.translatable("lightengine.editor.check_failed", UpdateChecker.error()),
                        LeTheme.BAD, true));
            }
        }
    }

    // ----- search popup (all blocks, list rows) -----

    private void openSearch() {
        LePopup popup = new LePopup(330, 240, Component.translatable("lightengine.editor.search_title"));
        int px = popup.panelX(this.width);
        int py = popup.panelY(this.height);
        LeScrollList[] listHolder = new LeScrollList[1];
        LeTextField field = new LeTextField(px + 15, py + 30, 300, 20,
                Component.translatable("lightengine.editor.search_title"),
                query -> this.rebuildSearch(listHolder[0], query));
        field.setPlaceholder(Component.translatable("lightengine.editor.search_hint").getString());
        popup.add(field);
        LeScrollList list = new LeScrollList(px + 15, py + 56, 300, 128, 22, index -> {
            if (index >= 0 && index < listHolder[0].getEntries().size()) {
                Object ref = listHolder[0].getEntries().get(index).ref;
                if (ref instanceof Identifier id) {
                    if (!this.paletteIds.contains(id)) {
                        this.addedIds.add(id);
                    }
                    this.setActive(id);
                }
            }
        });
        listHolder[0] = list;
        popup.add(list);
        popup.add(new LeButton(px + 90, py + 196, 150, 20,
                Component.translatable("lightengine.editor.apply"), b -> {
                    popup.close();
                    this.popups.remove(popup);
                }));
        this.rebuildSearch(list, field.getValue());
        this.popups.push(popup);
        this.focusedField = field;
        field.setFocused(true);
    }

    private void rebuildSearch(LeScrollList list, String query) {
        if (list == null) {
            return;
        }
        Map<String, List<Identifier>> buckets = new LinkedHashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null) {
                continue;
            }
            String name = block.getName().getString();
            String en = SearchUtil.englishName(block.getDescriptionId());
            if (!SearchUtil.matches(query, id.toString(), name, en)) {
                continue;
            }
            buckets.computeIfAbsent(id.getNamespace(), k -> new ArrayList<>()).add(id);
        }
        List<String> namespaces = new ArrayList<>(buckets.keySet());
        namespaces.sort(LightEditorScreen::compareNamespaces);
        List<LeScrollList.Entry> rows = new ArrayList<>();
        for (String ns : namespaces) {
            List<Identifier> ids = buckets.get(ns);
            ids.sort((a, b) -> a.toString().compareTo(b.toString()));
            rows.add(LeScrollList.Entry.header(modTitle(ns)));
            for (Identifier id : ids) {
                Block block = BuiltInRegistries.BLOCK.getValue(id);
                if (block == null) {
                    continue;
                }
                String name = block.getName().getString();
                ItemStack stack = safeIcon(block);
                rows.add(new LeScrollList.Entry(stack, name, id.toString(), id));
                if (rows.size() >= 1500) {
                    break;
                }
            }
            if (rows.size() >= 1500) {
                break;
            }
        }
        list.setEntries(rows);
    }

    // ----- event dispatch -----

    private LePopup topPopup() {
        while (!this.popups.isEmpty() && !this.popups.peek().isOpen()) {
            this.popups.pop();
        }
        return this.popups.peek();
    }

    private void syncFocused(LeWidget clicked) {
        this.focusedField = null;
        for (LeWidget widget : this.widgets) {
            if (widget instanceof LeTextField field) {
                if (widget != clicked) {
                    field.setFocused(false);
                } else if (field.isFocused()) {
                    this.focusedField = field;
                }
            }
        }
        LePopup popup = this.topPopup();
        if (popup != null) {
            for (LeWidget widget : popup.children) {
                if (widget instanceof LeTextField field) {
                    if (widget != clicked) {
                        field.setFocused(false);
                    } else if (field.isFocused()) {
                        this.focusedField = field;
                    }
                }
            }
        }
    }

    private void renderTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.updateButton != null && this.topPopup() == null && this.updateButton.isMouseOver(mouseX, mouseY)) {
            LeIconButton.renderTooltip(graphics, this.font, mouseX, mouseY,
                    this.width, this.height, LeIconButton.dotTooltip());
            return;
        }
        if (this.topPopup() != null || this.tileGrid == null) {
            return;
        }
        if (!this.tileGrid.isMouseOver(mouseX, mouseY)) {
            return;
        }
        LeTileGrid.Cell cell = this.tileGrid.hoveredCell();
        if (cell == null || !(cell.ref instanceof Identifier id)) {
            return;
        }
        Font font = this.font;
        int w = Math.max(font.width(cell.name), font.width(id.toString())) + 10;
        int h = 26;
        int tx = Math.min(mouseX + 12, this.width - w - 4);
        int ty = Math.min(mouseY + 12, this.height - h - 4);
        graphics.nextStratum();
        LeTheme.frame(graphics, tx, ty, w, h, 0xF0101014, LeTheme.PANEL_BORDER);
        graphics.text(font, cell.name, tx + 5, ty + 4, LeTheme.TEXT);
        graphics.text(font, id.toString(), tx + 5, ty + 14, LeTheme.TEXT_DIM);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // NOTE: no extractBackground() here — extractRenderStateWithTooltipAndSubtitles
        // already extracts it (with its own strata) before calling us; calling it
        // again would blur twice per frame and crash.
        graphics.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
        LeTheme.frame(graphics, 10, 28, 150, this.height - 68, LeTheme.PANEL_BG, LeTheme.PANEL_BORDER);
        graphics.text(this.font,
                Component.translatable("lightengine.editor.radius_line", this.sliderValue),
                18, 40, LeTheme.TEXT);
        int logBoxY = this.logY;
        LeTheme.frame(graphics, this.logX, logBoxY, this.logW, this.logH,
                LeTheme.FIELD_BG, LeTheme.FIELD_BORDER);
        graphics.text(this.font, Component.translatable("lightengine.editor.changes"),
                this.logX, logBoxY - 12, LeTheme.TEXT_DIM);
        int linesFit = Math.max(1, (this.logH - 8) / 6);
        int from = Math.max(0, this.changeLog.size() - linesFit);
        for (int i = from; i < this.changeLog.size(); i++) {
            String line = this.changeLog.get(i);
            int maxW = this.logW - 10;
            while (!line.isEmpty() && this.font.width(line) * 0.5F > maxW) {
                line = line.substring(0, line.length() - 1);
            }
            if (!line.equals(this.changeLog.get(i))) {
                String dots = line + "…";
                while (!line.isEmpty() && this.font.width(dots) * 0.5F > maxW) {
                    line = line.substring(0, line.length() - 1);
                    dots = line + "…";
                }
                line = dots;
            }
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(this.logX + 4, logBoxY + 4 + (i - from) * 6);
            pose.scale(0.5F, 0.5F);
            graphics.text(this.font, line, 0, 0, LeTheme.TEXT_DIM);
            pose.popMatrix();
        }
        LeTheme.frame(graphics, this.width - 160, 28, 150, this.height - 68, LeTheme.PANEL_BG, LeTheme.PANEL_BORDER);
        graphics.text(this.font, Component.translatable("lightengine.editor.blocks"),
                this.width - 152, 32, LeTheme.TEXT_DIM);
        Block active = BuiltInRegistries.BLOCK.getValue(this.activeId);
        LePopup top = this.topPopup();
        int[] avoid = null;
        if (top != null) {
            avoid = new int[]{top.panelX(this.width), top.panelY(this.height), top.width, top.height};
        }
        EditorScene.render(graphics, this.sceneX, this.sceneY, this.sceneW, this.sceneH,
                active, this.sliderValue, this.effectiveRadius(this.activeId),
                this.showLevels, avoid);
        for (LeWidget widget : this.widgets) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
        LePopup popup = this.topPopup();
        if (popup != null) {
            graphics.nextStratum();
            popup.renderBackground(graphics, this.width, this.height);
            popup.renderWidgets(graphics, mouseX, mouseY, partialTick);
        }
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        LePopup popup = this.topPopup();
        if (popup != null) {
            boolean handled = popup.mouseClicked(mouseX, mouseY, button);
            this.focusedField = null;
            for (LeWidget widget : popup.children) {
                if (widget instanceof LeTextField field && field.isFocused()) {
                    this.focusedField = field;
                }
            }
            for (LeWidget widget : this.widgets) {
                if (widget instanceof LeTextField field) {
                    field.setFocused(false);
                }
            }
            return handled;
        }
        for (int i = this.widgets.size() - 1; i >= 0; i--) {
            LeWidget widget = this.widgets.get(i);
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                this.syncFocused(widget);
                return true;
            }
        }
        this.syncFocused(null);
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        LePopup popup = this.topPopup();
        if (popup != null) {
            return popup.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        if (button == LEFT_BUTTON && event.hasControlDown()
                && mouseX >= this.sceneX && mouseX < this.sceneX + this.sceneW
                && mouseY >= this.sceneY && mouseY < this.sceneY + this.sceneH) {
            EditorScene.panBy(dragX, dragY, this.sceneW, this.sceneH);
            return true;
        }
        for (int i = this.widgets.size() - 1; i >= 0; i--) {
            if (this.widgets.get(i).mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        LePopup popup = this.topPopup();
        if (popup != null) {
            return popup.mouseReleased(mouseX, mouseY, button);
        }
        for (int i = this.widgets.size() - 1; i >= 0; i--) {
            if (this.widgets.get(i).mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        LePopup popup = this.topPopup();
        if (popup != null) {
            return popup.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (this.ctrlDown
                && mouseX >= this.sceneX && mouseX < this.sceneX + this.sceneW
                && mouseY >= this.sceneY && mouseY < this.sceneY + this.sceneH) {
            EditorScene.zoomAt(mouseX, mouseY, scrollY != 0.0 ? scrollY : scrollX,
                    this.sceneX, this.sceneY, this.sceneW, this.sceneH);
            return true;
        }
        for (int i = this.widgets.size() - 1; i >= 0; i--) {
            if (this.widgets.get(i).isMouseOver(mouseX, mouseY)
                    && this.widgets.get(i).mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        this.ctrlDown = (event.modifiers() & (KMOD_LCTRL | KMOD_RCTRL)) != 0;
        if (event.isEscape()) {
            LePopup popup = this.topPopup();
            if (popup != null) {
                popup.close();
                return true;
            }
            this.onX();
            return true;
        }
        int keyCode = event.key();
        int scanCode = event.keycode();
        int modifiers = event.modifiers();
        LePopup popup = this.topPopup();
        if (popup != null) {
            return popup.keyPressed(keyCode, scanCode, modifiers);
        }
        if (this.focusedField != null && this.focusedField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        this.ctrlDown = (event.modifiers() & (KMOD_LCTRL | KMOD_RCTRL)) != 0;
        int keyCode = event.key();
        int scanCode = event.keycode();
        int modifiers = event.modifiers();
        LePopup popup = this.topPopup();
        if (popup != null) {
            return popup.keyReleased(keyCode, scanCode, modifiers);
        }
        if (this.focusedField != null && this.focusedField.keyReleased(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        char codePoint = (char) event.codepoint();
        LePopup popup = this.topPopup();
        if (popup != null) {
            return popup.charTyped(codePoint, 0);
        }
        if (this.focusedField != null && this.focusedField.charTyped(codePoint, 0)) {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
