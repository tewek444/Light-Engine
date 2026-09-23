package me.tewek.lightengine.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import me.tewek.lightengine.LightEngine;
import me.tewek.lightengine.LightProfileRegistry;
import me.tewek.lightengine.client.EditorSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-game light editor. Left: radius + ignore flag + values + change log.
 * Center: analytic diorama preview. Right: tile palette with search.
 * Top-right X asks for confirmation when dirty. No vanilla widgets used.
 */
public class LightEditorScreen extends Screen {
    private final Screen parent;

    private ResourceLocation activeId = ResourceLocation.parse("minecraft:torch");
    private int sliderValue = 14;
    private boolean showLevels = true;
    private boolean dirty;
    private final Map<ResourceLocation, LightProfileRegistry.Profile> dirtyProfiles = new LinkedHashMap<>();
    private final List<ResourceLocation> paletteIds = new ArrayList<>();
    private final List<String> changeLog = new ArrayList<>();

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

        this.addButton(new LeButton(w - 28, 8, 20, 20, Component.literal("X"), b -> this.onX()));
        this.widgets.add(new LeButton(w - 92, 8, 60, 20,
                Component.translatable("lightengine.editor.update"), b -> this.openUpdate()));

        int lx = 10;
        int lw = 150;
        this.radiusSlider = new LeSlider(lx + 8, 54, lw - 16, 20, Component.empty(),
                0, 255, this.sliderValue, this::onSlider, String::valueOf);
        this.widgets.add(this.radiusSlider);
        this.signalToggle = new LeToggle(lx + 8, 82, lw - 16, 20,
                Component.translatable("lightengine.editor.ignore_conditions"),
                false, this::onIgnore);
        this.widgets.add(this.signalToggle);
        this.showToggle = new LeToggle(lx + 8, 108, lw - 16, 20,
                Component.translatable("lightengine.editor.show_levels"),
                this.showLevels, v -> this.showLevels = v);
        this.widgets.add(this.showToggle);
        this.logX = lx + 8;
        this.logY = 136;
        this.logW = lw - 16;
        this.logH = Math.max(30, (h - 40 - this.logY) / 2);

        this.sceneX = 170;
        this.sceneY = 28;
        this.sceneW = Math.max(120, w - 340);
        this.sceneH = Math.max(120, h - 68);

        int rx = w - 160;
        int rw = 150;
        this.panelSearch = new LeTextField(rx + 5, 48, rw - 10, 20,
                Component.translatable("lightengine.editor.search_hint"), query -> this.refreshTiles());
        this.panelSearch.setPlaceholder(Component.translatable("lightengine.editor.search_hint").getString());
        this.widgets.add(this.panelSearch);
        this.tileGrid = new LeTileGrid(rx + 5, 72, rw - 10, h - 112, 24, 5, this::onTileSelect);
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
    private LeToggle showToggle;

    private void addButton(LeButton button) {
        this.widgets.add(button);
    }

    // ----- state -----

    private int vanillaOf(ResourceLocation id) {
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block == null) {
            return 0;
        }
        return block.defaultBlockState().getLightEmission();
    }

    private int effectiveRadius(ResourceLocation id) {
        LightProfileRegistry.Profile profile = LightProfileRegistry.get(id);
        if (profile != null) {
            return profile.lightRadius();
        }
        return this.vanillaOf(id);
    }

    private void setActive(ResourceLocation id) {
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
        this.logChange(this.activeId + " -> " + value + (this.signalToggle.isChecked() ? "*" : ""));
        this.refreshTiles();
    }

    private void onIgnore(boolean ignore) {
        this.dirty = true;
        this.dirtyProfiles.put(this.activeId,
                new LightProfileRegistry.Profile(this.sliderValue, ignore));
        this.logChange(this.activeId + (ignore ? " ignore+" : " ignore-"));
        this.refreshTiles();
    }

    private boolean ignoreOf(ResourceLocation id) {
        LightProfileRegistry.Profile profile = LightProfileRegistry.get(id);
        return profile != null && profile.ignoreConditions();
    }

    private void logChange(String line) {
        this.changeLog.add(line);
        while (this.changeLog.size() > 60) {
            this.changeLog.remove(0);
        }
    }

    private void ensurePalette(ResourceLocation id) {
        if (!this.paletteIds.contains(id)) {
            this.paletteIds.add(id);
            this.rebuildPalette();
        }
    }

    private void rebuildPalette() {
        List<ResourceLocation> ids = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null) {
                continue;
            }
            if (LightProfileRegistry.isProfiled(block) || block.defaultBlockState().getLightEmission() > 0) {
                ids.add(id);
            }
        }
        for (ResourceLocation extra : this.paletteIds) {
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

    private void refreshTiles() {
        String query = this.panelSearch == null ? "" : this.panelSearch.getValue();
        List<LeTileGrid.Cell> cells = new ArrayList<>();
        for (ResourceLocation id : this.paletteIds) {
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block == null) {
                continue;
            }
            String name = block.getName().getString();
            String en = SearchUtil.englishName(block.getDescriptionId());
            if (!SearchUtil.matches(query, id.toString(), name, en)) {
                continue;
            }
            ItemStack stack = new ItemStack(block.asItem());
            int shown = id.equals(this.activeId) ? this.sliderValue : this.effectiveRadius(id);
            cells.add(new LeTileGrid.Cell(stack.isEmpty() ? null : stack, name, id.toString(),
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
        } else if (ref instanceof ResourceLocation id) {
            this.setActive(id);
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
        this.minecraft.setScreen(this.parent);
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
        UpdateChecker.check(LightEngine.VERSION, LightEngine.LOADER);
        this.syncUpdatePopup();
    }

    @Override
    public void tick() {
        super.tick();
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
        popup.children.clear();
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
                popup.add(new LeLabel(inner, py + 40, innerW,
                        Component.translatable("lightengine.editor.up_to_date"), LeTheme.TEXT, true));
                popup.add(new LeButton(px + 90, py + 196, 150, 20,
                        Component.translatable("lightengine.editor.later"), b -> popup.close()));
            }
            case AVAILABLE -> {
                popup.add(new LeLabel(inner, py + 28, innerW,
                        Component.translatable("lightengine.editor.new_version", UpdateChecker.remoteVersion()),
                        LeTheme.ACCENT, true));
                String[] lines = UpdateChecker.changelog().replace("\r", "").split("\n");
                List<LeScrollList.Entry> rows = new ArrayList<>();
                for (String line : lines) {
                    if (rows.size() >= 150) {
                        break;
                    }
                    rows.add(new LeScrollList.Entry(null, line, "", null));
                }
                LeScrollList log = new LeScrollList(inner, py + 46, innerW, 124, 14, index -> {
                });
                log.setEntries(rows);
                popup.add(log);
                popup.add(new LeButton(px + 15, py + 196, 140, 20,
                        Component.translatable("lightengine.editor.later"), b -> popup.close()));
                popup.add(new LeButton(px + 175, py + 196, 140, 20,
                        Component.translatable("lightengine.editor.download"), b -> UpdateChecker.download()));
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
                popup.add(new LeButton(px + 15, py + 196, 140, 20,
                        Component.translatable("lightengine.editor.later"), b -> popup.close()));
                popup.add(new LeButton(px + 175, py + 196, 140, 20,
                        Component.translatable("lightengine.editor.restart"), b -> {
                            if (!UpdateChecker.restart()) {
                                this.updateStatus.setText(Component.translatable(
                                        "lightengine.editor.check_failed", UpdateChecker.error()));
                            }
                        }));
            }
            case FAILED -> {
                popup.add(new LeLabel(inner, py + 40, innerW,
                        Component.translatable("lightengine.editor.check_failed", UpdateChecker.error()),
                        LeTheme.BAD, true));
                popup.add(new LeButton(px + 90, py + 196, 150, 20,
                        Component.translatable("lightengine.editor.later"), b -> popup.close()));
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
                if (ref instanceof ResourceLocation id) {
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
        List<LeScrollList.Entry> rows = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null) {
                continue;
            }
            String name = block.getName().getString();
            String en = SearchUtil.englishName(block.getDescriptionId());
            if (!SearchUtil.matches(query, id.toString(), name, en)) {
                continue;
            }
            ItemStack stack = new ItemStack(block.asItem());
            rows.add(new LeScrollList.Entry(stack.isEmpty() ? null : stack, name, id.toString(), id));
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

    private void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.topPopup() != null || this.tileGrid == null) {
            return;
        }
        if (!this.tileGrid.isMouseOver(mouseX, mouseY)) {
            return;
        }
        LeTileGrid.Cell cell = this.tileGrid.hoveredCell();
        if (cell == null || !(cell.ref instanceof ResourceLocation id)) {
            return;
        }
        Font font = this.font;
        int w = Math.max(font.width(cell.name), font.width(id.toString())) + 10;
        int h = 26;
        int tx = Math.min(mouseX + 12, this.width - w - 4);
        int ty = Math.min(mouseY + 12, this.height - h - 4);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        LeTheme.frame(graphics, tx, ty, w, h, 0xF0101014, LeTheme.PANEL_BORDER);
        graphics.drawString(font, cell.name, tx + 5, ty + 4, LeTheme.TEXT);
        graphics.drawString(font, id.toString(), tx + 5, ty + 14, LeTheme.TEXT_DIM);
        graphics.pose().popPose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
        LeTheme.frame(graphics, 10, 28, 150, this.height - 68, LeTheme.PANEL_BG, LeTheme.PANEL_BORDER);
        graphics.drawString(this.font,
                Component.translatable("lightengine.editor.radius_line", this.sliderValue),
                18, 40, LeTheme.TEXT);
        int logBoxY = this.logY;
        LeTheme.frame(graphics, this.logX, logBoxY, this.logW, this.logH,
                LeTheme.FIELD_BG, LeTheme.FIELD_BORDER);
        graphics.drawString(this.font, Component.translatable("lightengine.editor.changes"),
                this.logX, logBoxY - 12, LeTheme.TEXT_DIM);
        int linesFit = Math.max(1, (this.logH - 8) / 10);
        int from = Math.max(0, this.changeLog.size() - linesFit);
        for (int i = from; i < this.changeLog.size(); i++) {
            graphics.drawString(this.font, this.changeLog.get(i),
                    this.logX + 4, logBoxY + 4 + (i - from) * 10, LeTheme.TEXT_DIM);
        }
        LeTheme.frame(graphics, this.width - 160, 28, 150, this.height - 68, LeTheme.PANEL_BG, LeTheme.PANEL_BORDER);
        graphics.drawString(this.font, Component.translatable("lightengine.editor.blocks"),
                this.width - 152, 32, LeTheme.TEXT_DIM);
        Block active = BuiltInRegistries.BLOCK.get(this.activeId);
        EditorScene.render(graphics, this.sceneX, this.sceneY, this.sceneW, this.sceneH,
                active, this.sliderValue, this.showLevels);
        for (LeWidget widget : this.widgets) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
        LePopup popup = this.topPopup();
        if (popup != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 400.0F);
            popup.renderBackground(graphics, this.width, this.height);
            popup.renderWidgets(graphics, mouseX, mouseY, partialTick);
            graphics.pose().popPose();
        }
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        LePopup popup = this.topPopup();
        if (popup != null) {
            return popup.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        for (int i = this.widgets.size() - 1; i >= 0; i--) {
            if (this.widgets.get(i).mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        LePopup popup = this.topPopup();
        if (popup != null) {
            return popup.mouseReleased(mouseX, mouseY, button);
        }
        for (int i = this.widgets.size() - 1; i >= 0; i--) {
            if (this.widgets.get(i).mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        LePopup popup = this.topPopup();
        if (popup != null) {
            return popup.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            LePopup popup = this.topPopup();
            if (popup != null) {
                popup.close();
                return true;
            }
            this.onX();
            return true;
        }
        if (this.focusedField != null && this.focusedField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.focusedField != null && this.focusedField.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
