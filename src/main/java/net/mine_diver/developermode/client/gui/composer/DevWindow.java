package net.mine_diver.developermode.client.gui.composer;

import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.Theme;
import net.minecraft.client.Minecraft;

/**
 * A floating panel inside the {@link ComposerScreen}.
 *
 * <p>Subclasses fill in {@link #renderContent}; the frame, title bar, close
 * button and content clipping are handled here. All coordinates handed to a
 * window are in screen space, so a window that wants to know where something
 * of its own is should offset by {@link #contentX()} and {@link #contentY()}.
 */
public abstract class DevWindow {
    public static final int TITLE_BAR_HEIGHT = 13;
    public static final int PADDING = 4;

    /**
     * Handed to every window except the one the pointer actually belongs to.
     *
     * <p>Windows hit test the cursor themselves to decide what is hovered, and
     * on their own have no idea another window is sitting on top of them, so
     * stacked windows would all light up at once while only the top one
     * answered clicks. Feeding the others a position far outside themselves
     * makes every one of those tests miss, without each window having to
     * remember to ask. Far from any real coordinate, but nowhere near the edges
     * of int, so arithmetic on it stays well behaved.
     */
    public static final int POINTER_AWAY = -10000;

    private static final int CLOSE_SIZE = 11;

    public int x;
    public int y;
    public int width;
    public int height;

    private String title;

    protected DevWindow(String title, int width, int height) {
        this.title = title;
        this.width = width;
        this.height = height;
    }

    public final void render(Minecraft minecraft, int mouseX, int mouseY, float delta, boolean focused) {
        Draw.rect(x, y, x + width, y + height, Theme.PANEL);
        Draw.rect(x + 1, y + 1, x + width - 1, y + TITLE_BAR_HEIGHT,
                focused ? Theme.TITLE_BAR_FOCUSED : Theme.TITLE_BAR);
        Draw.outline(x, y, width, height, focused ? Theme.BORDER_FOCUSED : Theme.BORDER);

        Draw.text(minecraft, Draw.ellipsize(minecraft, title, width - CLOSE_SIZE - 10),
                x + 5, y + 3, focused ? Theme.TEXT : Theme.TEXT_DIM);

        boolean closeHovered = isOverClose(mouseX, mouseY);
        if (closeHovered) {
            Draw.rect(x + width - CLOSE_SIZE - 1, y + 1, x + width - 1, y + TITLE_BAR_HEIGHT, Theme.HOVER);
        }
        Draw.cross(x + width - CLOSE_SIZE / 2F - 1, y + TITLE_BAR_HEIGHT / 2F, 2.5,
                closeHovered ? Theme.DANGER : Theme.TEXT_DIM);

        Draw.pushScissor(minecraft, contentX(), contentY(), contentWidth(), contentHeight());
        renderContent(minecraft, mouseX, mouseY, delta, focused);
        Draw.popScissor();
    }

    protected abstract void renderContent(Minecraft minecraft, int mouseX, int mouseY, float delta, boolean focused);

    /**
     * Drawn after every window, outside any clipping, so tooltips and popups
     * are not cut off by the window they belong to.
     */
    public void renderOverlay(Minecraft minecraft, int mouseX, int mouseY) {}

    public void tick() {}

    /** Called once when the window is closed from the composer. */
    public void onClosed() {}

    public void mouseClicked(int mouseX, int mouseY, int button) {}

    public void mouseReleased(int mouseX, int mouseY, int button) {}

    public void mouseScrolled(int mouseX, int mouseY, int direction) {}

    public void keyPressed(char character, int keyCode) {}

    /** Gives up text focus. Returns true if there was any to give up. */
    public boolean clearTypingFocus() {
        return false;
    }

    /** What it calls itself, for anything listing windows from outside. */
    public final String title() {
        return title;
    }

    /** For a window that retargets, so the title bar can say what at. */
    protected final void setTitle(String title) {
        this.title = title;
    }

    public final boolean contains(int pointX, int pointY) {
        return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
    }

    public final boolean isOverTitleBar(int pointX, int pointY) {
        return pointX >= x && pointX < x + width && pointY >= y && pointY < y + TITLE_BAR_HEIGHT
                && !isOverClose(pointX, pointY);
    }

    public final boolean isOverClose(int pointX, int pointY) {
        return pointX >= x + width - CLOSE_SIZE - 1 && pointX < x + width - 1
                && pointY >= y + 1 && pointY < y + TITLE_BAR_HEIGHT;
    }

    public final int contentX() {
        return x + PADDING;
    }

    public final int contentY() {
        return y + TITLE_BAR_HEIGHT + PADDING;
    }

    public final int contentWidth() {
        return width - PADDING * 2;
    }

    public final int contentHeight() {
        return height - TITLE_BAR_HEIGHT - PADDING * 2;
    }
}
