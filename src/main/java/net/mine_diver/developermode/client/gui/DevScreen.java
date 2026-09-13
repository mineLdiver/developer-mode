package net.mine_diver.developermode.client.gui;

import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Mouse;

/**
 * Shared behaviour for the developer UI: the world keeps ticking underneath,
 * the background is blurred rather than dimmed, and the scroll wheel is
 * delivered instead of being swallowed.
 */
public abstract class DevScreen extends Screen {
    /**
     * Beta pauses singleplayer whenever a screen is open. A developer tool
     * wants to watch what it is doing, so it does not.
     */
    @Override
    public boolean shouldPause() {
        return false;
    }

    protected void renderBackdrop() {
        // A clip left behind by a throwing window would otherwise follow us out
        // of the UI and crop the whole game.
        Draw.resetScissor();
        Blur.render(minecraft);
        Draw.rect(0, 0, width, height, Theme.SCRIM);
    }

    @Override
    public void onMouseEvent() {
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) mouseScrolled(mouseX(), mouseY(), wheel > 0 ? 1 : -1);

        // Movement only events report button -1, and vanilla turns those into
        // a bogus mouseReleased. Drop them instead.
        if (Mouse.getEventButton() < 0) return;
        super.onMouseEvent();
    }

    protected void mouseScrolled(int mouseX, int mouseY, int direction) {}

    /** Cursor position in GUI space, accurate this frame rather than this tick. */
    protected int mouseX() {
        return Mouse.getX() * width / minecraft.displayWidth;
    }

    protected int mouseY() {
        return height - Mouse.getY() * height / minecraft.displayHeight - 1;
    }
}
