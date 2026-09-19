package net.mine_diver.developermode.client.inspect;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.gui.DevScreen;
import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.composer.ComposerScreen;
import net.mine_diver.developermode.client.gui.composer.WindowDock;
import net.mine_diver.developermode.client.gui.radial.RadialScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

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
    public static void open() {
        DeveloperModeClient.minecraft().setScreen(new InspectScreen());
    }

    @Override
    public void init() {
        // Also on the way back from the ring, which is what puts the outlines
        // back when the ring goes away.
        InspectMode.enter();

        // Start at the crosshair, so that a flick at the desktop is the same
        // flick every time.
        //
        // The game places the cursor itself when it lets go of it, but on the
        // AWT canvas, which keeps its windowed size while the display is full
        // screen, and only when it was holding the cursor to begin with. The
        // display is the space the mouse reports in, so it is the one to halve.
        int centerX = Display.getWidth() / 2;
        int centerY = Display.getHeight() / 2;
        Mouse.setCursorPosition(centerX, centerY);

        // Aimed at what was just set rather than at what the mouse reads back.
        // Moving the pointer is a request to the window system, and the mouse
        // goes on reporting the old position until that request comes back.
        InspectMode.aimAt(centerX, centerY);
    }

    @Override
    public void removed() {
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

        // Window pixels, which is what the mouse reports and what turning a
        // pixel back into a ray through the world needs.
        InspectMode.aimAt(Mouse.getX(), Mouse.getY());

        if (WindowDock.reached(width, mouseX, mouseY)) {
            ComposerScreen.open();
            return;
        }

        InspectRenderer.renderReadout(minecraft, width, height);
        WindowDock.render(minecraft, width, mouseX, mouseY);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 1) {
            RadialScreen.open(this, RadialScreen.Hold.RIGHT_BUTTON);
            return;
        }
        if (button == 0) InspectMode.pick();
    }
}
