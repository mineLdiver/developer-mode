package net.mine_diver.developermode.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.CharacterUtils;
import org.lwjgl.input.Keyboard;

/**
 * A single line text input, small enough to live inside a window without
 * dragging in vanilla's screen bound widget.
 */
public final class TextField {
    public static final int HEIGHT = 14;

    private final StringBuilder text = new StringBuilder();
    private final int maxLength;

    public int x;
    public int y;
    public int width;

    private boolean focused;
    private int caretTicks;

    public TextField(int maxLength) {
        this.maxLength = maxLength;
    }

    public void bounds(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public String text() {
        return text.toString();
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
        this.caretTicks = 0;
    }

    public void tick() {
        caretTicks++;
    }

    public boolean contains(int pointX, int pointY) {
        return pointX >= x && pointX < x + width && pointY >= y && pointY < y + HEIGHT;
    }

    public void render(Minecraft minecraft, String placeholder) {
        Draw.rect(x, y, x + width, y + HEIGHT, Theme.PANEL_SUNKEN);
        Draw.outline(x, y, width, HEIGHT, focused ? Theme.BORDER_FOCUSED : Theme.BORDER);

        int textY = y + 3;
        if (text.length() == 0 && !focused) {
            Draw.text(minecraft, Draw.ellipsize(minecraft, placeholder, width - 8), x + 4, textY, Theme.TEXT_FAINT);
            return;
        }

        String visible = Draw.ellipsize(minecraft, text.toString(), width - 10);
        Draw.text(minecraft, visible, x + 4, textY, Theme.TEXT);

        if (focused && caretTicks / 6 % 2 == 0) {
            int caretX = x + 4 + Draw.textWidth(minecraft, visible);
            Draw.rect(caretX, textY - 1, caretX + 1, textY + 9, Theme.ACCENT);
        }
    }

    /**
     * @return true if the text changed
     */
    public boolean keyPressed(char character, int keyCode) {
        if (!focused) return false;

        if (keyCode == Keyboard.KEY_BACK) {
            if (text.length() == 0) return false;
            text.setLength(text.length() - 1);
            return true;
        }

        if (keyCode == Keyboard.KEY_V && isControlDown()) {
            String clipboard = Screen.getClipboard();
            if (clipboard == null) return false;
            boolean changed = false;
            for (int i = 0; i < clipboard.length(); i++) changed |= append(clipboard.charAt(i));
            return changed;
        }

        return append(character);
    }

    public void clear() {
        text.setLength(0);
    }

    public void setText(String value) {
        text.setLength(0);
        if (value != null) text.append(value.length() > maxLength ? value.substring(0, maxLength) : value);
    }

    private boolean append(char character) {
        if (text.length() >= maxLength) return false;
        if (CharacterUtils.VALID_CHARACTERS.indexOf(character) < 0) return false;
        text.append(character);
        return true;
    }

    private static boolean isControlDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }
}
