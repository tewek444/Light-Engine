package me.tewek.lightengine.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Custom single-line text field: caret, keyboard navigation, mouse
 * drag-selection, double-click word select, ctrl+A/C/X/V with the
 * system clipboard, and a change callback.
 *
 * <p>26.x notes: key/button codes follow SDL (see {@code SDLScancode}/
 * {@code SDL_BUTTON_*}); modifiers arrive as an {@code SDL_KMOD_*} mask.
 * Focus changes are reported to the SDL text-input manager so IME and
 * {@code charTyped} keep working.
 */
public class LeTextField implements LeWidget {
    // SDL3 keymod mask bits (stable across SDL2/SDL3).
    private static final int KMOD_LSHIFT = 0x0001;
    private static final int KMOD_RSHIFT = 0x0002;
    private static final int KMOD_LCTRL = 0x0040;
    private static final int KMOD_RCTRL = 0x0080;

    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected final Component message;
    protected String text = "";
    protected int caret;
    protected int anchor = -1;
    protected int maxLength = 64;
    protected boolean focused;
    protected boolean shiftDown;
    protected boolean dragging;
    protected long lastClickTime;
    protected int lastClickPos = -1;
    protected String placeholder = "";
    protected final Consumer<String> responder;

    private static final int SELECTION_COLOR = 0xFF2E4BD7;

    public LeTextField(int x, int y, int width, int height, Component message, Consumer<String> responder) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.message = message;
        this.responder = responder;
    }

    public String getValue() {
        return this.text;
    }

    public void setValue(String value) {
        String next = value == null ? "" : value;
        if (next.length() > this.maxLength) {
            next = next.substring(0, this.maxLength);
        }
        this.text = next;
        this.caret = Math.clamp(this.caret, 0, this.text.length());
        if (this.caret > this.text.length()) {
            this.caret = this.text.length();
        }
        this.anchor = -1;
        this.responder.accept(this.text);
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
    }

    private static boolean hasShift(int modifiers) {
        return (modifiers & (KMOD_LSHIFT | KMOD_RSHIFT)) != 0;
    }

    private static boolean hasCtrl(int modifiers) {
        return (modifiers & (KMOD_LCTRL | KMOD_RCTRL)) != 0;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
        this.dragging = false;
        if (!focused) {
            this.shiftDown = false;
        } else {
            this.caret = this.text.length();
            this.anchor = -1;
        }
        try {
            Minecraft.getInstance().textInputManager().onTextInputFocusChange(this, focused);
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean isFocused() {
        return this.focused;
    }

    private boolean hasSelection() {
        return this.anchor >= 0 && this.anchor != this.caret;
    }

    private int selMin() {
        return Math.min(this.caret, this.anchor);
    }

    private int selMax() {
        return Math.max(this.caret, this.anchor);
    }

    private void deleteSelection() {
        if (!this.hasSelection()) {
            return;
        }
        this.text = this.text.substring(0, this.selMin()) + this.text.substring(this.selMax());
        this.caret = this.selMin();
        this.anchor = -1;
        this.responder.accept(this.text);
    }

    private static String sanitize(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\n' || c == '\r') {
                out.append(' ');
            } else if (!Character.isISOControl(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '-' || c == '/' || c == '.';
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
                LeTheme.FIELD_BG, this.focused ? LeTheme.FIELD_FOCUSED : LeTheme.FIELD_BORDER);
        Font font = Minecraft.getInstance().font;
        int textX = this.x + 4;
        int textY = this.y + (this.height - 8) / 2;
        int innerW = this.width - 8;
        if (this.text.isEmpty() && !this.placeholder.isEmpty()) {
            graphics.text(font, this.trimToWidth(font, this.placeholder, innerW),
                    textX, textY, LeTheme.TEXT_DIM);
            if (this.focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                graphics.fill(textX, textY - 1, textX + 1, textY + 9, LeTheme.TEXT);
            }
            return;
        }
        // Window the visible portion so the caret stays on screen.
        int start = 0;
        while (start < this.caret && font.width(this.text.substring(start)) > innerW) {
            start++;
        }
        String visible = this.text.substring(start);
        visible = this.trimToWidth(font, visible, innerW);
        graphics.text(font, visible, textX, textY, LeTheme.TEXT);
        if (this.hasSelection()) {
            int from = Math.max(this.selMin(), start);
            int to = Math.min(this.selMax(), start + visible.length());
            if (to > from) {
                int x1 = textX + font.width(visible.substring(0, from - start));
                int x2 = textX + font.width(visible.substring(0, to - start));
                graphics.fill(x1, textY - 1, x2, textY + 9, SELECTION_COLOR);
            }
        }
        if (this.focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int caretInVisible = Math.min(this.caret - start, visible.length());
            int caretX = textX + font.width(visible.substring(0, caretInVisible));
            graphics.fill(caretX, textY - 1, caretX + 1, textY + 9, LeTheme.TEXT);
        }
    }

    private int caretFromMouse(double mouseX) {
        Font font = Minecraft.getInstance().font;
        int rel = (int) mouseX - (this.x + 4);
        int pos = 0;
        while (pos < this.text.length() && font.width(this.text.substring(0, pos + 1)) < rel) {
            pos++;
        }
        return pos;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean over = this.isMouseOver(mouseX, mouseY);
        this.dragging = false;
        // SDL button numbering (button == 1 is left).
        if (!over || button != LightEditorScreen.LEFT_BUTTON) {
            this.setFocused(false);
            return over;
        }
        this.focused = true;
        try {
            Minecraft.getInstance().textInputManager().onTextInputFocusChange(this, true);
        } catch (Exception ignored) {
        }
        int pos = this.caretFromMouse(mouseX);
        long now = System.currentTimeMillis();
        if (this.shiftDown) {
            if (this.anchor < 0) {
                this.anchor = this.caret;
            }
            this.caret = pos;
        } else if (now - this.lastClickTime < 250L && pos == this.lastClickPos) {
            int from = pos;
            int to = pos;
            while (from > 0 && isWordChar(this.text.charAt(from - 1))) {
                from--;
            }
            while (to < this.text.length() && isWordChar(this.text.charAt(to))) {
                to++;
            }
            this.anchor = from;
            this.caret = to;
            this.lastClickTime = 0;
            this.lastClickPos = -1;
            return true;
        } else {
            this.caret = pos;
            this.anchor = pos;
        }
        this.dragging = true;
        this.lastClickTime = now;
        this.lastClickPos = pos;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragging && this.focused && button == LightEditorScreen.LEFT_BUTTON) {
            this.caret = Math.clamp(this.caretFromMouse(mouseX), 0, this.text.length());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.dragging && button == LightEditorScreen.LEFT_BUTTON) {
            this.dragging = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.focused) {
            return false;
        }
        if (keyCode == InputConstants.KEY_LSHIFT || keyCode == InputConstants.KEY_RSHIFT) {
            this.shiftDown = true;
        }
        boolean ctrl = hasCtrl(modifiers);
        if (ctrl && keyCode == InputConstants.KEY_A) {
            this.anchor = 0;
            this.caret = this.text.length();
            return true;
        }
        if (ctrl && keyCode == InputConstants.KEY_C) {
            if (this.hasSelection()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(
                        this.text.substring(this.selMin(), this.selMax()));
            }
            return true;
        }
        if (ctrl && keyCode == InputConstants.KEY_X) {
            if (this.hasSelection()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(
                        this.text.substring(this.selMin(), this.selMax()));
                this.deleteSelection();
            }
            return true;
        }
        if (ctrl && keyCode == InputConstants.KEY_V) {
            String clip;
            try {
                clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            } catch (Exception ex) {
                return true;
            }
            if (clip != null && !clip.isEmpty()) {
                this.deleteSelection();
                String clean = sanitize(clip);
                int room = Math.max(0, this.maxLength - this.text.length());
                if (clean.length() > room) {
                    clean = clean.substring(0, room);
                }
                this.text = this.text.substring(0, this.caret) + clean + this.text.substring(this.caret);
                this.caret += clean.length();
                this.responder.accept(this.text);
            }
            return true;
        }
        if (keyCode == InputConstants.KEY_BACKSPACE) {
            if (this.hasSelection()) {
                this.deleteSelection();
            } else if (this.caret > 0 && !this.text.isEmpty()) {
                this.text = this.text.substring(0, this.caret - 1) + this.text.substring(this.caret);
                this.caret--;
                this.anchor = -1;
                this.responder.accept(this.text);
            }
            return true;
        }
        if (keyCode == InputConstants.KEY_DELETE) {
            if (this.hasSelection()) {
                this.deleteSelection();
            } else if (this.caret < this.text.length()) {
                this.text = this.text.substring(0, this.caret) + this.text.substring(this.caret + 1);
                this.anchor = -1;
                this.responder.accept(this.text);
            }
            return true;
        }
        boolean shift = this.shiftDown || hasShift(modifiers);
        if (keyCode == InputConstants.KEY_LEFT) {
            if (shift && this.anchor < 0) {
                this.anchor = this.caret;
            }
            if (this.caret > 0) {
                this.caret--;
            }
            if (!shift) {
                this.anchor = -1;
            }
            return true;
        }
        if (keyCode == InputConstants.KEY_RIGHT) {
            if (shift && this.anchor < 0) {
                this.anchor = this.caret;
            }
            if (this.caret < this.text.length()) {
                this.caret++;
            }
            if (!shift) {
                this.anchor = -1;
            }
            return true;
        }
        if (keyCode == InputConstants.KEY_HOME) {
            if (shift && this.anchor < 0) {
                this.anchor = this.caret;
            }
            this.caret = 0;
            if (!shift) {
                this.anchor = -1;
            }
            return true;
        }
        if (keyCode == InputConstants.KEY_END) {
            if (shift && this.anchor < 0) {
                this.anchor = this.caret;
            }
            this.caret = this.text.length();
            if (!shift) {
                this.anchor = -1;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_LSHIFT || keyCode == InputConstants.KEY_RSHIFT) {
            this.shiftDown = false;
            return this.focused;
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!this.focused || Character.isISOControl(codePoint)) {
            return false;
        }
        if (this.hasSelection()) {
            this.deleteSelection();
        }
        if (this.text.length() >= this.maxLength) {
            return true;
        }
        this.text = this.text.substring(0, this.caret) + codePoint + this.text.substring(this.caret);
        this.caret++;
        this.anchor = -1;
        this.responder.accept(this.text);
        return true;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseX < this.x + this.width
                && mouseY >= this.y && mouseY < this.y + this.height;
    }
}
