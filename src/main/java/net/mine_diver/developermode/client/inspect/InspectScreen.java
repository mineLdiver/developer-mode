package net.mine_diver.developermode.client.inspect;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.gui.DevScreen;
import net.mine_diver.developermode.client.gui.Draw;
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
 * <p>The desktop is tucked along the top as tabs, so it is visible from the
 * one state that can reach it.
 */
public final class InspectScreen extends DevScreen {
    private boolean pointerPlaced;

    public static void open() {
        DeveloperModeClient.minecraft().setScreen(new InspectScreen());
    }

    @Override
    public void init() {
        // Also on the way back from the ring, which is what puts the outlines
        // back when the ring goes away.
        InspectMode.enter();

        if (!pointerPlaced) {
            pointerPlaced = true;
            // Where the crosshair was, so that holding control and clicking is
            // one motion at what you were already looking at. Left where it
            // lies after that, and the ring puts it back itself.
            int centerX = minecraft.displayWidth / 2;
            int centerY = minecraft.displayHeight / 2;
            Mouse.setCursorPosition(centerX, centerY);
            InspectMode.aimAt(centerX, centerY);
            return;
        }
        InspectMode.aimAt(Mouse.getX(), Mouse.getY());
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
        InspectRenderer.renderReadout(minecraft, width, height);
        WindowDock.render(minecraft, mouseX, mouseY);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 1) {
            RadialScreen.open(this, RadialScreen.Hold.RIGHT_BUTTON);
            return;
        }
        if (button != 0) return;
        if (WindowDock.clicked(minecraft, mouseX, mouseY)) return;
        InspectMode.pick();
    }
}
