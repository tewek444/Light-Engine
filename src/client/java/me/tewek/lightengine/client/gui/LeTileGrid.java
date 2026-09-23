package me.tewek.lightengine.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Custom tile grid with a fixed column count and even padding on all sides.
 * Square cells hold a centered icon and a bottom strip (sun pixel icon +
 * left text, torch pixel art + right text); long texts shrink to fit.
 * Hover, selection, wheel scroll and a draggable scrollbar thumb. Virtualized.
 */
public class LeTileGrid implements LeWidget {
    public static final class Cell {
        public final ItemStack icon;
        public final String name;
        public final String idLine;
        public final String left;
        public final boolean sun;
        public final String right;
        public final boolean torch;
        public final Object ref;

        public Cell(ItemStack icon, String name, String idLine,
                    String left, boolean sun, String right, boolean torch, Object ref) {
            this.icon = icon;
            this.name = name == null ? "" : name;
            this.idLine = idLine == null ? "" : idLine;
            this.left = left == null ? "" : left;
            this.sun = sun;
            this.right = right == null ? "" : right;
            this.torch = torch;
            this.ref = ref;
        }
    }

    public static final Object ADD = new Object();

    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected final int baseCell;
    protected final int fixedCols;
    protected final int gap = 4;
    protected final int pad = 6;
    protected final int scrollBar = 6;
    protected List<Cell> cells = new ArrayList<>();
    protected int scroll;
    protected int hovered = -1;
    protected int selected = -1;
    protected boolean draggingThumb;
    protected final Consumer<Integer> onSelect;

    public LeTileGrid(int x, int y, int width, int height, int baseCell, int fixedCols, Consumer<Integer> onSelect) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.baseCell = Math.max(24, baseCell);
        this.fixedCols = fixedCols;
        this.onSelect = onSelect;
    }

    public void setCells(List<Cell> cells) {
        this.cells = cells == null ? new ArrayList<>() : cells;
        this.scroll = 0;
        this.hovered = -1;
        this.selected = -1;
    }

    public List<Cell> getCells() {
        return this.cells;
    }

    public int getSelected() {
        return this.selected;
    }

    public void setSelected(int index) {
        this.selected = index >= 0 && index < this.cells.size() ? index : -1;
        this.ensureVisible(this.selected);
    }

    public Cell hoveredCell() {
        return this.hovered >= 0 && this.hovered < this.cells.size() ? this.cells.get(this.hovered) : null;
    }

    private int cols() {
        if (this.fixedCols > 0) {
            return this.fixedCols;
        }
        return Math.max(1, (this.width - this.pad * 2 + this.gap) / (this.baseCell + this.gap));
    }

    private int cell() {
        if (this.fixedCols > 0) {
            int avail = this.width - this.pad * 2 - this.gap * (this.fixedCols - 1) - this.scrollBar;
            return Math.max(16, avail / this.fixedCols);
        }
        return this.baseCell;
    }

    private int xOffset() {
        int used = this.cols() * this.cell() + (this.cols() - 1) * this.gap;
        int free = this.width - this.pad * 2 - this.scrollBar - used;
        return this.pad + Math.max(0, free / 2);
    }

    private int rowHeight() {
        return this.cell() + this.gap;
    }

    private int stripHeight() {
        return Math.max(7, this.cell() / 6);
    }

    private int visibleHeight() {
        return Math.max(1, this.height - this.pad * 2);
    }

    private int contentHeight() {
        int rows = (this.cells.size() + this.cols() - 1) / this.cols();
        return Math.max(0, rows * this.rowHeight() - this.gap);
    }

    private int maxScroll() {
        return Math.max(0, this.contentHeight() - this.visibleHeight());
    }

    private void ensureVisible(int index) {
        if (index < 0) {
            return;
        }
        int row = index / this.cols();
        int top = row * this.rowHeight();
        if (top < this.scroll) {
            this.scroll = top;
        } else if (top + this.rowHeight() > this.scroll + this.visibleHeight()) {
            this.scroll = top + this.rowHeight() - this.visibleHeight();
        }
        this.scroll = Math.clamp(this.scroll, 0, this.maxScroll());
    }

    private int indexAt(double mouseX, double mouseY) {
        int cell = this.cell();
        int step = cell + this.gap;
        int lx = (int) (mouseX - (this.x + this.xOffset()));
        int ly = (int) (mouseY - (this.y + this.pad)) + this.scroll;
        if (lx < 0 || ly < 0) {
            return -1;
        }
        int col = lx / step;
        int row = ly / this.rowHeight();
        if (col >= this.cols()) {
            return -1;
        }
        int index = row * this.cols() + col;
        if (index < 0 || index >= this.cells.size()) {
            return -1;
        }
        int cellTop = row * this.rowHeight() - this.scroll;
        if (cellTop + this.cell() <= 0 || cellTop >= this.visibleHeight()) {
            return -1;
        }
        if (lx - col * step > cell || ly - row * this.rowHeight() > cell) {
            return -1;
        }
        return index;
    }

    private void drawSun(GuiGraphics graphics, int sx, int sy) {
        graphics.fill(sx, sy, sx + 4, sy + 4, 0xFFFFD866);
        graphics.fill(sx + 1, sy + 1, sx + 3, sy + 3, 0xFFFFF0B0);
    }

    private void drawTorch(GuiGraphics graphics, int tx, int ty) {
        graphics.fill(tx + 1, ty + 2, tx + 3, ty + 7, 0xFF6B4423);
        graphics.fill(tx, ty, tx + 4, ty + 2, 0xFFFF3B30);
    }

    private void drawSoloStrip(GuiGraphics graphics, Font font, MultiBufferSource buffer,
                               Cell entry, int bx, int stripY, int cell, int stripH) {
        float need = font.width(entry.left) + (entry.sun ? 7.0F : 0.0F);
        float scale = need <= 0.5F ? 1.0F : Math.clamp((cell - 6.0F) / need, 0.4F, 0.6F);
        String shown = entry.left;
        while (shown.length() > 1 && font.width(shown) * scale > cell - 6.0F) {
            shown = shown.substring(0, shown.length() - 1);
        }
        float groupW = (font.width(shown) + (entry.sun ? 7.0F : 0.0F)) * scale;
        float gx = bx + (cell - groupW) / 2.0F;
        float gy = stripY + (stripH - 8.0F * scale) / 2.0F;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(gx, gy, 0.0F);
        pose.scale(scale, scale, 1.0F);
        float cx = 0.0F;
        if (entry.sun) {
            graphics.fill((int) cx, 2, (int) cx + 4, 6, 0xFFFFD866);
            graphics.fill((int) cx + 1, 3, (int) cx + 3, 5, 0xFFFFF0B0);
            cx += 7.0F;
        }
        font.drawInBatch(shown, cx, 0.0F, LeTheme.TEXT, false,
                pose.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        pose.popPose();
    }

    private void drawFittedText(GuiGraphics graphics, Font font, MultiBufferSource buffer,
                                String text, float x, float y, float maxWidth, int color) {
        float need = font.width(text);
        float scale = need <= 0.5F ? 1.0F : Math.clamp(maxWidth / need, 0.4F, 0.6F);
        String shown = text;
        while (shown.length() > 1 && font.width(shown) * scale > maxWidth) {
            shown = shown.substring(0, shown.length() - 1);
        }
        float drawY = y + (8.0F - 8.0F * scale) / 2.0F;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, drawY, 0.0F);
        pose.scale(scale, scale, 1.0F);
        font.drawInBatch(shown, 0.0F, 0.0F, color, false,
                pose.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        pose.popPose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        LeTheme.frame(graphics, this.x, this.y, this.width, this.height,
                LeTheme.FIELD_BG, LeTheme.FIELD_BORDER);
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        MultiBufferSource buffer = mc.renderBuffers().bufferSource();
        int cell = this.cell();
        int cols = this.cols();
        int stripH = this.stripHeight();
        this.hovered = this.isMouseOver(mouseX, mouseY) ? this.indexAt(mouseX, mouseY) : -1;
        graphics.enableScissor(this.x + 1, this.y + 1, this.x + this.width - 1, this.y + this.height - 1);
        int firstRow = Math.max(0, this.scroll / this.rowHeight() - 1);
        int lastRow = (this.scroll + this.visibleHeight()) / this.rowHeight() + 1;
        for (int row = firstRow; row <= lastRow; row++) {
            int rowTop = row * this.rowHeight() - this.scroll;
            if (rowTop + this.rowHeight() <= 0 || rowTop >= this.visibleHeight()) {
                continue;
            }
            for (int col = 0; col < cols; col++) {
                int index = row * cols + col;
                if (index < 0 || index >= this.cells.size()) {
                    continue;
                }
                Cell entry = this.cells.get(index);
                int bx = this.x + this.xOffset() + col * (cell + this.gap);
                int by = this.y + this.pad + row * this.rowHeight() - this.scroll;
                if (index == this.selected) {
                    LeTheme.frame(graphics, bx, by, cell, cell, LeTheme.ACCENT_DARK, LeTheme.ACCENT);
                } else {
                    graphics.fill(bx, by, bx + cell, by + cell, 0xFF23232C);
                    if (index == this.hovered) {
                        graphics.fill(bx, by, bx + cell, by + cell, 0x402C2C36);
                    }
                }
                if ("+".equals(entry.name) && entry.icon == null) {
                    int cx = bx + cell / 2;
                    int cy = by + cell / 2;
                    int arm = Math.max(4, cell / 3);
                    int thick = Math.max(2, cell / 12);
                    int tx = cx - thick / 2;
                    int ty = cy - thick / 2;
                    graphics.fill(tx, cy - arm, tx + thick, cy + arm, LeTheme.TEXT);
                    graphics.fill(cx - arm, ty, cx + arm, ty + thick, LeTheme.TEXT);
                } else if (entry.icon != null && !entry.icon.isEmpty()) {
                    float iconAreaH = cell - stripH;
                    float iconPx = Math.round(Math.min(24.0F, iconAreaH));
                    float iconScale = iconPx / 16.0F;
                    float cx = bx + cell / 2.0F;
                    float cy = by + iconAreaH / 2.0F;
                    PoseStack pose = graphics.pose();
                    pose.pushPose();
                    pose.translate(Math.round(cx), Math.round(cy), 0.0F);
                    pose.scale(iconScale, iconScale, 1.0F);
                    graphics.renderItem(entry.icon, -8, -8);
                    pose.popPose();
                }
                if (entry.ref == ADD) {
                    continue;
                }
                int stripY = by + cell - stripH;
                graphics.fill(bx, stripY, bx + cell, stripY + stripH, 0xCC0C0C10);
                float half = cell / 2.0F;
                float textY = stripY + 1;
                if (entry.right.isEmpty() && !entry.torch) {
                    this.drawSoloStrip(graphics, font, buffer, entry, bx, stripY, cell, stripH);
                    continue;
                }
                float leftX = bx + 3;
                if (entry.sun) {
                    this.drawSun(graphics, (int) leftX, (int) (stripY + (stripH - 4) / 2.0F));
                    leftX += 7;
                }
                this.drawFittedText(graphics, font, buffer, entry.left,
                        leftX, textY, bx + half - 2 - leftX, LeTheme.TEXT);
                float rightNeed = font.width(entry.right) + (entry.torch ? 6.0F : 0.0F);
                float rightAvail = half - 5.0F;
                float rightScale = rightNeed <= 0.5F ? 1.0F : Math.clamp(rightAvail / rightNeed, 0.4F, 0.6F);
                float groupW = rightNeed * rightScale;
                float gx = bx + cell - 3 - groupW;
                float gy = stripY + (stripH - 8.0F * rightScale) / 2.0F;
                PoseStack pose = graphics.pose();
                pose.pushPose();
                pose.translate(gx, gy, 0.0F);
                pose.scale(rightScale, rightScale, 1.0F);
                float cx = 0.0F;
                if (entry.torch) {
                    graphics.fill((int) (cx + 1), 2, (int) (cx + 3), 7, 0xFF6B4423);
                    graphics.fill((int) cx, 0, (int) cx + 4, 2, 0xFFFF3B30);
                    cx += 6.0F;
                }
                font.drawInBatch(entry.right, cx, 0.0F, LeTheme.TEXT_DIM, false,
                        pose.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, 0xF000F0);
                pose.popPose();
            }
        }
        graphics.flush();
        graphics.disableScissor();
        if (this.maxScroll() > 0) {
            int barX = this.x + this.width - 5;
            graphics.fill(barX, this.y + 1, barX + 4, this.y + this.height - 1, LeTheme.SCROLL_BG);
            double frac = (double) this.scroll / (double) this.maxScroll();
            int thumbH = Math.max(14, (this.height - 2) * this.visibleHeight() / Math.max(1, this.contentHeight()));
            int thumbY = this.y + 1 + (int) (frac * ((this.height - 2) - thumbH));
            graphics.fill(barX, thumbY, barX + 4, thumbY + thumbH, LeTheme.SCROLL_THUMB);
        }
    }

    private void scrollTo(double mouseY) {
        int trackH = this.height - 2;
        int thumbH = Math.max(14, trackH * this.visibleHeight() / Math.max(1, this.contentHeight()));
        double frac = (mouseY - (this.y + 1) - thumbH / 2.0) / Math.max(1, trackH - thumbH);
        frac = Math.clamp(frac, 0.0, 1.0);
        this.scroll = (int) (frac * this.maxScroll());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        if (this.maxScroll() > 0 && mouseX >= this.x + this.width - 6) {
            this.draggingThumb = true;
            this.scrollTo(mouseY);
            return true;
        }
        int index = this.indexAt(mouseX, mouseY);
        if (index >= 0) {
            this.selected = index;
            this.onSelect.accept(index);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingThumb && button == 0) {
            this.scrollTo(mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.draggingThumb && button == 0) {
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
        this.scroll = (int) Math.clamp(this.scroll - scrollY * this.rowHeight(), 0, this.maxScroll());
        return true;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseX < this.x + this.width
                && mouseY >= this.y && mouseY < this.y + this.height;
    }
}
