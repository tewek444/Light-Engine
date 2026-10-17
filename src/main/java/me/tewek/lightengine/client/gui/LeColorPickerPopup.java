package me.tewek.lightengine.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * HSV color picker popup (modal, closes on outside click / Escape):
 * saturation-value square, rainbow hue bar, preview + hex field + copy
 * button + Hex mode label. Reports picked {@code 0xRRGGBB} colors live
 * through {@code onColor}; the owner decides what to do with them.
 */
public class LeColorPickerPopup extends LePopup {
    public static final int WIDTH = 232;
    public static final int HEIGHT = 194;

    private float hue;
    private float sat;
    private float val;
    private final Consumer<Integer> onColor;
    private LeTextField hexField;
    private boolean syncingHex;

    public LeColorPickerPopup(int x, int y, int initialRgb, Consumer<Integer> onColor) {
        super(WIDTH, HEIGHT, Component.translatable("lightengine.editor.light_color"));
        this.onColor = onColor;
        float[] hsv = rgbToHsv(initialRgb);
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = hsv[2];
        this.setPosition(x, y);
        this.setCloseOnOutsideClick(true);
        this.add(new SvArea(this, x + 12, y + 30, 208, 104));
        this.add(new HueBar(this, x + 12, y + 142, 208, 12));
        this.add(new LeColorSwatch(x + 12, y + 162, 20, this::currentRgb, () -> true, () -> {
        }));
        this.hexField = new LeTextField(x + 36, y + 162, 104, 20, Component.empty(), this::onHex);
        this.hexField.setPlaceholder(Component.translatable("lightengine.editor.light_color_hint").getString());
        this.add(this.hexField);
        this.add(new LeIconButton(x + 144, y + 162, 20, LeIconButton::drawCopy, b -> this.copyHex()));
        this.add(new LeLabel(x + 168, y + 162, 52, Component.literal("Hex"), LeTheme.TEXT_DIM, true));
        this.syncHexField();
    }

    public int currentRgb() {
        return hsvToRgb(this.hue, this.sat, this.val);
    }

    private void commit() {
        int rgb = this.currentRgb();
        this.syncHexField();
        if (this.onColor != null) {
            this.onColor.accept(rgb);
        }
    }

    private void syncHexField() {
        if (this.hexField == null) {
            return;
        }
        this.syncingHex = true;
        try {
            this.hexField.setValue(String.format("#%06X", this.currentRgb() & 0xFFFFFF));
        } finally {
            this.syncingHex = false;
        }
    }

    private void onHex(String text) {
        if (this.syncingHex) {
            return;
        }
        int rgb = parseHexStrict(text);
        if (rgb < 0) {
            return; // intermediate input while typing
        }
        float[] hsv = rgbToHsv(rgb);
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = hsv[2];
        this.commit();
    }

    private void copyHex() {
        try {
            net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(
                    String.format("#%06X", this.currentRgb() & 0xFFFFFF));
        } catch (Throwable ignored) {
        }
    }

    /** Strict #RRGGBB / RRGGBB, or -1. */
    private static int parseHexStrict(String text) {
        if (text == null) {
            return -1;
        }
        String t = text.strip();
        if (t.startsWith("#")) {
            t = t.substring(1);
        }
        if (t.length() != 6) {
            return -1;
        }
        try {
            return Integer.parseUnsignedInt(t, 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /** h 0..360, s/v 0..1. */
    static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0.0F;
        if (d > 0.0F) {
            if (max == r) {
                h = 60.0F * (((g - b) / d) % 6.0F);
            } else if (max == g) {
                h = 60.0F * ((b - r) / d + 2.0F);
            } else {
                h = 60.0F * ((r - g) / d + 4.0F);
            }
            if (h < 0.0F) {
                h += 360.0F;
            }
        }
        float s = max <= 0.0F ? 0.0F : d / max;
        return new float[]{h, s, max};
    }

    /** Returns packed {@code 0xRRGGBB}. */
    static int hsvToRgb(float h, float s, float v) {
        float hh = ((h % 360.0F) + 360.0F) % 360.0F;
        float c = v * s;
        float x = c * (1.0F - Math.abs((hh / 60.0F) % 2.0F - 1.0F));
        float m = v - c;
        float r;
        float g;
        float b;
        if (hh < 60.0F) {
            r = c;
            g = x;
            b = 0.0F;
        } else if (hh < 120.0F) {
            r = x;
            g = c;
            b = 0.0F;
        } else if (hh < 180.0F) {
            r = 0.0F;
            g = c;
            b = x;
        } else if (hh < 240.0F) {
            r = 0.0F;
            g = x;
            b = c;
        } else if (hh < 300.0F) {
            r = x;
            g = 0.0F;
            b = c;
        } else {
            r = c;
            g = 0.0F;
            b = x;
        }
        int ri = Math.clamp((int) ((r + m) * 255.0F), 0, 255);
        int gi = Math.clamp((int) ((g + m) * 255.0F), 0, 255);
        int bi = Math.clamp((int) ((b + m) * 255.0F), 0, 255);
        return (ri << 16) | (gi << 8) | bi;
    }

    private static int lerpWhite(int hueRgb, float s) {
        int hr = (hueRgb >> 16) & 0xFF;
        int hg = (hueRgb >> 8) & 0xFF;
        int hb = hueRgb & 0xFF;
        int r = (int) (255.0F + (hr - 255.0F) * s);
        int g = (int) (255.0F + (hg - 255.0F) * s);
        int b = (int) (255.0F + (hb - 255.0F) * s);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void ring(GuiGraphicsExtractor graphics, int cx, int cy, int r) {
        graphics.fill(cx - r, cy - r, cx + r + 1, cy - r + 1, 0xFFFFFFFF);
        graphics.fill(cx - r, cy + r, cx + r + 1, cy + r + 1, 0xFFFFFFFF);
        graphics.fill(cx - r, cy - r, cx - r + 1, cy + r + 1, 0xFFFFFFFF);
        graphics.fill(cx + r, cy - r, cx + r + 1, cy + r + 1, 0xFFFFFFFF);
        int i = r - 1;
        graphics.fill(cx - i, cy - i, cx + i + 1, cy - i + 1, 0xFF000000);
        graphics.fill(cx - i, cy + i, cx + i + 1, cy + i + 1, 0xFF000000);
        graphics.fill(cx - i, cy - i, cx - i + 1, cy + i + 1, 0xFF000000);
        graphics.fill(cx + i, cy - i, cx + i + 1, cy + i + 1, 0xFF000000);
    }

    /** Saturation-value square for the popup's current hue. */
    static final class SvArea implements LeWidget {
        private final LeColorPickerPopup popup;
        private final int x;
        private final int y;
        private final int w;
        private final int h;
        private boolean dragging;

        SvArea(LeColorPickerPopup popup, int x, int y, int w, int h) {
            this.popup = popup;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        @Override
        public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int hueRgb = hsvToRgb(this.popup.hue, 1.0F, 1.0F);
            for (int i = 0; i < this.w; i++) {
                float s = i / (float) (this.w - 1);
                graphics.fill(this.x + i, this.y, this.x + i + 1, this.y + this.h,
                        lerpWhite(hueRgb, s));
            }
            for (int j = 0; j < this.h; j++) {
                int a = (int) ((j / (float) (this.h - 1)) * 255.0F);
                graphics.fill(this.x, this.y + j, this.x + this.w, this.y + j + 1, a << 24);
            }
            int mx = this.x + Math.round(this.popup.sat * (this.w - 1));
            int my = this.y + Math.round((1.0F - this.popup.val) * (this.h - 1));
            ring(graphics, mx, my, 4);
            border(graphics);
        }

        private void border(GuiGraphicsExtractor graphics) {
            graphics.fill(this.x, this.y, this.x + this.w, this.y + 1, LeTheme.PANEL_BORDER);
            graphics.fill(this.x, this.y + this.h - 1, this.x + this.w, this.y + this.h, LeTheme.PANEL_BORDER);
            graphics.fill(this.x, this.y, this.x + 1, this.y + this.h, LeTheme.PANEL_BORDER);
            graphics.fill(this.x + this.w - 1, this.y, this.x + this.w, this.y + this.h, LeTheme.PANEL_BORDER);
        }

        private void updateFrom(double mouseX, double mouseY) {
            float s = (float) ((mouseX - this.x) / (this.w - 1));
            float v = 1.0F - (float) ((mouseY - this.y) / (this.h - 1));
            this.popup.sat = Math.clamp(s, 0.0F, 1.0F);
            this.popup.val = Math.clamp(v, 0.0F, 1.0F);
            this.popup.commit();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && this.isMouseOver(mouseX, mouseY)) {
                this.dragging = true;
                this.updateFrom(mouseX, mouseY);
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (this.dragging && button == 0) {
                this.updateFrom(mouseX, mouseY);
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (this.dragging && button == 0) {
                this.dragging = false;
                return true;
            }
            return false;
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.w
                    && mouseY >= this.y && mouseY < this.y + this.h;
        }
    }

    /** Rainbow hue bar. */
    static final class HueBar implements LeWidget {
        private final LeColorPickerPopup popup;
        private final int x;
        private final int y;
        private final int w;
        private final int h;
        private boolean dragging;

        HueBar(LeColorPickerPopup popup, int x, int y, int w, int h) {
            this.popup = popup;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        @Override
        public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            for (int i = 0; i < this.w; i++) {
                graphics.fill(this.x + i, this.y, this.x + i + 1, this.y + this.h,
                        0xFF000000 | hsvToRgb(i / (float) (this.w - 1) * 360.0F, 1.0F, 1.0F));
            }
            int mx = this.x + Math.round(this.popup.hue / 360.0F * (this.w - 1));
            graphics.fill(mx - 2, this.y - 1, mx - 1, this.y + this.h + 1, 0xFFFFFFFF);
            graphics.fill(mx + 1, this.y - 1, mx + 2, this.y + this.h + 1, 0xFFFFFFFF);
            graphics.fill(this.x, this.y, this.x + this.w, this.y + 1, LeTheme.PANEL_BORDER);
            graphics.fill(this.x, this.y + this.h - 1, this.x + this.w, this.y + this.h, LeTheme.PANEL_BORDER);
            graphics.fill(this.x, this.y, this.x + 1, this.y + this.h, LeTheme.PANEL_BORDER);
            graphics.fill(this.x + this.w - 1, this.y, this.x + this.w, this.y + this.h, LeTheme.PANEL_BORDER);
        }

        private void updateFrom(double mouseX) {
            float h = (float) ((mouseX - this.x) / (this.w - 1)) * 360.0F;
            this.popup.hue = Math.clamp(h, 0.0F, 360.0F);
            this.popup.commit();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && this.isMouseOver(mouseX, mouseY)) {
                this.dragging = true;
                this.updateFrom(mouseX);
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (this.dragging && button == 0) {
                this.updateFrom(mouseX);
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (this.dragging && button == 0) {
                this.dragging = false;
                return true;
            }
            return false;
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.w
                    && mouseY >= this.y - 2 && mouseY < this.y + this.h + 2;
        }
    }
}
