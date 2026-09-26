package me.tewek.lightengine.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Custom virtualized scroll list: only visible rows are drawn, so thousands
 * of entries (full block search) stay cheap. Single selection + wheel scroll.
 */
public class LeScrollList implements LeWidget {
    public static final class Entry {
        public final ItemStack icon;
        public final String label;
        public final String sub;
        public final Object ref;
        public final boolean header;

        public Entry(ItemStack icon, String label, String sub, Object ref) {
            this(icon, label, sub, ref, false);
        }

        private Entry(ItemStack icon, String label, String sub, Object ref, boolean header) {
            this.icon = icon;
            this.label = label == null ? "" : label;
            this.sub = sub == null ? "" : sub;
            this.ref = ref;
            this.header = header;
        }

        public static Entry header(String title) {
            return new Entry(null, title, "", null, true);
        }
    }

    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected final int rowHeight;
    protected List<Entry> entries = new ArrayList<>();
    protected int scroll;
    protected int selected = -1;
    protected boolean draggingThumb;
    protected final Consumer<Integer> onSelect;
    /** Plain mode: no hover/selection highlights, clicks swallowed (read-only text). */
    protected boolean plain;

    public LeScrollList(int x, int y, int width, int height, int rowHeight, Consumer<Integer> onSelect) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.rowHeight = Math.max(12, rowHeight);
        this.onSelect = onSelect;
    }

    public void setEntries(List<Entry> entries) {
        this.entries = entries == null ? new ArrayList<>() : entries;
        this.scroll = Math.clamp(this.scroll, 0, this.maxScroll());
        this.selected = -1;
    }

    public void setPlain(boolean plain) {
        this.plain = plain;
    }

    public List<Entry> getEntries() {
        return this.entries;
    }

    public int getSelected() {
        return this.selected;
    }

    public void setSelected(int index) {
        this.selected = index >= 0 && index < this.entries.size() ? index : -1;
        this.ensureVisible(this.selected);
    }

    private int contentHeight() {
        return this.entries.size() * this.rowHeight;
    }

    private int maxScroll() {
        return Math.max(0, this.contentHeight() - (this.height - 4));
    }

    private void ensureVisible(int index) {
        if (index < 0) {
            return;
        }
        int top = index * this.rowHeight;
        if (top < this.scroll) {
            this.scroll = top;
        } else if (top + this.rowHeight > this.scroll + this.height - 4) {
            this.scroll = top + this.rowHeight - (this.height - 4);
        }
        this.scroll = Math.clamp(this.scroll, 0, this.maxScroll());
    }

    private String trimToWidth(Font font, String value, int maxWidth) {
        String out = value;
        while (!out.isEmpty() && font.width(out) > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        LeTheme.frame(graphics, this.x, this.y, this.width, this.height,
                LeTheme.FIELD_BG, LeTheme.FIELD_BORDER);
        Font font = Minecraft.getInstance().font;
        graphics.enableScissor(this.x + 1, this.y + 1, this.x + this.width - 1, this.y + this.height - 1);
        int first = Math.max(0, this.scroll / this.rowHeight - 1);
        int last = Math.min(this.entries.size() - 1, (this.scroll + this.height) / this.rowHeight + 1);
        for (int i = first; i <= last; i++) {
            Entry entry = this.entries.get(i);
            int rowY = this.y + 2 + i * this.rowHeight - this.scroll;
            boolean isHeader = entry.header;
            if (isHeader) {
                graphics.fill(this.x + 1, rowY, this.x + this.width - 1, rowY + this.rowHeight, 0xFF1E1E28);
                graphics.text(font, this.trimToWidth(font, entry.label, this.width - 16),
                        this.x + 8, rowY + 3, LeTheme.ACCENT);
                graphics.fill(this.x + 1, rowY + this.rowHeight - 1,
                        this.x + this.width - 1, rowY + this.rowHeight, LeTheme.PANEL_BORDER);
                continue;
            }
            if (!this.plain && i == this.selected) {
                graphics.fill(this.x + 1, rowY, this.x + this.width - 1, rowY + this.rowHeight, LeTheme.ACCENT_DARK);
            } else if (!this.plain && mouseX >= this.x && mouseX < this.x + this.width
                    && mouseY >= rowY && mouseY < rowY + this.rowHeight) {
                graphics.fill(this.x + 1, rowY, this.x + this.width - 1, rowY + this.rowHeight, LeTheme.ROW_HOVER);
            }
            if (entry.icon != null && !entry.icon.isEmpty()) {
                try {
                    graphics.item(entry.icon, this.x + 4, rowY + (this.rowHeight - 16) / 2);
                } catch (Exception ignored) {
                    // Item models may need world-bound state; never break the list.
                }
            }
            int labelX = this.x + 24;
            int labelW = this.width - 28 - (entry.sub.isEmpty() ? 0 : font.width(entry.sub) + 8);
            if (this.plain && entry.icon == null) {
                labelX = this.x + 8;
                labelW = this.width - 16;
            }
            graphics.text(font, this.trimToWidth(font, entry.label, Math.max(8, labelW)),
                    labelX, rowY + 3, i == this.selected ? LeTheme.ACCENT : LeTheme.TEXT);
            if (!entry.sub.isEmpty()) {
                graphics.text(font, entry.sub,
                        this.x + this.width - 4 - font.width(entry.sub), rowY + 3, LeTheme.TEXT_DIM);
            }
        }
        graphics.disableScissor();
        if (this.maxScroll() > 0) {
            int barX = this.x + this.width - 5;
            graphics.fill(barX, this.y + 1, barX + 4, this.y + this.height - 1, LeTheme.SCROLL_BG);
            double frac = (double) this.scroll / (double) this.maxScroll();
            int thumbH = Math.max(12, (this.height - 2) * (this.height - 4) / Math.max(1, this.contentHeight()));
            int thumbY = this.y + 1 + (int) (frac * ((this.height - 2) - thumbH));
            graphics.fill(barX, thumbY, barX + 4, thumbY + thumbH, LeTheme.SCROLL_THUMB);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // SDL button numbering (button == 1 is left).
        if (button != LightEditorScreen.LEFT_BUTTON || !this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        if (this.maxScroll() > 0 && mouseX >= this.x + this.width - 6) {
            this.draggingThumb = true;
            this.scrollTo(mouseY);
            return true;
        }
        int index = (int) ((mouseY - (this.y + 2) + this.scroll) / this.rowHeight);
        if (index >= 0 && index < this.entries.size()) {
            if (this.plain || this.entries.get(index).header) {
                return true;
            }
            int rowTop = index * this.rowHeight - this.scroll;
            if (rowTop + this.rowHeight <= 0 || rowTop >= this.height - 4) {
                return true;
            }
            this.selected = index;
            this.onSelect.accept(index);
        }
        return true;
    }

    private void scrollTo(double mouseY) {
        int trackH = this.height - 2;
        int thumbH = Math.max(12, trackH * (this.height - 4) / Math.max(1, this.contentHeight()));
        double frac = (mouseY - (this.y + 1) - thumbH / 2.0) / Math.max(1, trackH - thumbH);
        frac = Math.clamp(frac, 0.0, 1.0);
        this.scroll = (int) (frac * this.maxScroll());
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingThumb && button == LightEditorScreen.LEFT_BUTTON) {
            this.scrollTo(mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.draggingThumb && button == LightEditorScreen.LEFT_BUTTON) {
            this.draggingThumb = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        this.scroll = (int) Math.clamp(this.scroll - scrollY * this.rowHeight, 0, this.maxScroll());
        return true;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseX < this.x + this.width
                && mouseY >= this.y && mouseY < this.y + this.height;
    }
}
