package net.mine_diver.developermode.client.gui.composer;

import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.Theme;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Where the desktop waits while you are out in the world.
 *
 * <p>Something to flick at rather than something to click: it sits across the
 * top middle, wide and shallow, and reaching it opens the desktop with no
 * second action. A row of things to aim at individually would be a window
 * picker, which is a different job and a slower one.
 *
 * <p>It stops short of the top of the screen on purpose. Aiming at the edge of
 * the viewport means aiming at the edge of the window, and windowed that is
 * how a pointer leaves the game.
 */
public final class WindowDock {
    private static final int HEIGHT = 13;
    private static final int WIDTH = 124;
    private static final int CARET_SIZE = 5;

    private WindowDock() {}

    public static void render(Minecraft minecraft, int screenWidth, int mouseX, int mouseY) {
        boolean lit = reached(screenWidth, mouseX, mouseY);
        int left = left(screenWidth);

        Draw.rect(left, 0, left + WIDTH, HEIGHT, lit ? Theme.PANEL_RAISED : Theme.PANEL);
        Draw.rect(left, HEIGHT - 1, left + WIDTH, HEIGHT, lit ? Theme.BORDER_FOCUSED : Theme.BORDER);
        Draw.caret(left + 6, 4, CARET_SIZE, true, lit ? Theme.ACCENT : Theme.TEXT_FAINT);

        Draw.text(minecraft, label(), left + 16, 3, lit ? Theme.ACCENT : Theme.TEXT_DIM);
    }

    /** Whether the pointer has got to it, which is the whole of the gesture. */
    public static boolean reached(int screenWidth, int mouseX, int mouseY) {
        int left = left(screenWidth);
        return mouseY >= 0 && mouseY < HEIGHT && mouseX >= left && mouseX < left + WIDTH;
    }

    /** Says what is waiting, so the count is known without opening it. */
    private static String label() {
        List<DevWindow> windows = ComposerScreen.instance().windows();
        if (windows.isEmpty()) return "Composer";

        return windows.size() == 1
                ? "Composer   1 window"
                : "Composer   " + windows.size() + " windows";
    }

    private static int left(int screenWidth) {
        return screenWidth / 2 - WIDTH / 2;
    }
}
