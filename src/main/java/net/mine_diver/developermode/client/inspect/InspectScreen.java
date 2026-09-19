package net.mine_diver.developermode.client.inspect;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.gui.DevScreen;
import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.Theme;
import net.mine_diver.developermode.client.gui.composer.ComposerScreen;
import net.mine_diver.developermode.client.gui.composer.WindowDock;
import net.mine_diver.developermode.client.gui.radial.RadialScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * The way into the mod: hold the key, get a pointer, point at things.
 *
 * <p>The world is left exactly as it is. Every other screen here blurs what is
 * behind it, and this one must not: what is behind it is the subject, and
 * picking something you can barely see is what made this feel detached from
 * the world when it was a window.
 *
 * <p>Held rather than latched. Releasing the key puts you back where you were,
 * which is what makes it free to hold just to look at what is around you.
 *
 * <p>Left click opens whatever is under the pointer. Right click opens the
 * radial, held by that button, and letting it go comes back here.
 *
 * <p>The desktop waits across the top, visible from the one state that can
 * reach it, and reaching it is all it takes to open.
 */
public final class InspectScreen extends DevScreen {
    /** Height of the drawn arrow, in GUI pixels. */
    private static final int POINTER_HEIGHT = 11;
    private static final int POINTER_WIDTH = 7;

    /**
     * Where the pointer is, in window pixels with the origin at the bottom
     * left, which is the space a pixel has to be in to be turned back into a
     * ray through the world.
     */
    private int pointerX;
    private int pointerY;

    public static void open() {
        DeveloperModeClient.minecraft().setScreen(new InspectScreen());
    }

    /**
     * The pointer is this screen's own, drawn by it and moved by raw mouse
     * travel, rather than the system cursor placed where the crosshair is.
     *
     * <p>Asking the window system to move its cursor is a request, not an
     * instruction, and a compositor is free to decline it, which leaves the
     * arrow wherever it already was. Holding the mouse and counting how far it
     * moves needs no such permission, so the pointer starts at the crosshair
     * every time. It also cannot be thrown out of the window, which is what
     * the edges of the viewport otherwise invite.
     */
    @Override
    public void init() {
        // Also on the way back from the ring, which is what puts the outlines
        // back when the ring goes away.
        InspectMode.enter();

        pointerX = minecraft.displayWidth / 2;
        pointerY = minecraft.displayHeight / 2;

        Mouse.setGrabbed(true);
        // Whatever travel piled up before the grab belongs to aiming the
        // camera, not to this pointer's first frame.
        Mouse.getDX();
        Mouse.getDY();

        InspectMode.aimAt(pointerX, pointerY);
    }

    @Override
    public void removed() {
        Mouse.setGrabbed(false);
        InspectMode.exit();
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        // A clip left behind by something that threw would otherwise follow us
        // out of here and crop the whole game.
        Draw.resetScissor();

        // Polled rather than taken off a key event, so letting go is felt on
        // the frame it happens.
        if (!Keyboard.isKeyDown(DeveloperModeClient.INSPECT_KEY.code)) {
            minecraft.setScreen(null);
            return;
        }

        // Both of these are window pixels, where up is positive, so travel
        // simply adds. The flip to screen coordinates is where they are read.
        pointerX = clamp(pointerX + Mouse.getDX(), minecraft.displayWidth);
        pointerY = clamp(pointerY + Mouse.getDY(), minecraft.displayHeight);
        InspectMode.aimAt(pointerX, pointerY);

        int x = pointerGuiX();
        int y = pointerGuiY();

        if (WindowDock.reached(width, x, y)) {
            ComposerScreen.open();
            return;
        }

        InspectRenderer.renderReadout(minecraft, width, height);
        WindowDock.render(minecraft, width, x, y);
        renderPointer(x, y);
    }

    /**
     * The position a click arrives with belongs to a cursor that is being held
     * still, so what was clicked is whatever this screen's own pointer is on.
     */
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 1) {
            RadialScreen.open(this, RadialScreen.Hold.RIGHT_BUTTON);
            return;
        }
        if (button == 0) InspectMode.pick();
    }

    /** An arrow, drawn over its own shadow so that it reads against any world. */
    private void renderPointer(int x, int y) {
        Draw.triangle(x + 1, y + 1, x + 1, y + POINTER_HEIGHT + 1,
                x + POINTER_WIDTH + 1, y + POINTER_HEIGHT - 2, Theme.SHADOW);
        Draw.triangle(x, y, x, y + POINTER_HEIGHT,
                x + POINTER_WIDTH, y + POINTER_HEIGHT - 3, Theme.ACCENT);
    }

    private int pointerGuiX() {
        return pointerX * width / minecraft.displayWidth;
    }

    private int pointerGuiY() {
        return height - pointerY * height / minecraft.displayHeight - 1;
    }

    private static int clamp(int value, int size) {
        return Math.max(0, Math.min(value, size - 1));
    }
}
