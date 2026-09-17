package net.mine_diver.developermode.client.gui.composer;

import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.Theme;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * The desktop, tucked along the top of the screen as a row of tabs.
 *
 * <p>So that the composer is somewhere you can see from the state that can
 * reach it, rather than something you have to already know about. What is open
 * is named, and how many are open is countable, without any of it covering the
 * world.
 *
 * <p>The tabs sit inside the viewport rather than against its edge. Reaching
 * for an edge means aiming at the boundary of the window, and in a windowed
 * game that is a good way to leave it.
 */
public final class WindowDock {
    public static final int HEIGHT = 11;

    private static final int MARGIN = 4;
    private static final int GAP = 3;
    private static final int PADDING = 4;
    /** Shown when nothing is open, so the way in is still visible. */
    private static final String EMPTY = "Composer";

    private WindowDock() {}

    public static void render(Minecraft minecraft, int mouseX, int mouseY) {
        List<DevWindow> windows = ComposerScreen.instance().windows();
        int hovered = tabAt(minecraft, windows, mouseX, mouseY);

        int x = MARGIN;
        for (int i = 0; i < Math.max(1, windows.size()); i++) {
            String label = windows.isEmpty() ? EMPTY : windows.get(i).title();
            int tabWidth = width(minecraft, label);
            boolean lit = i == hovered;

            Draw.rect(x, 0, x + tabWidth, HEIGHT, lit ? Theme.PANEL_RAISED : Theme.PANEL);
            Draw.rect(x, HEIGHT - 1, x + tabWidth, HEIGHT, lit ? Theme.BORDER_FOCUSED : Theme.BORDER);
            Draw.text(minecraft, label, x + PADDING, 2, lit ? Theme.ACCENT : Theme.TEXT_DIM);

            x += tabWidth + GAP;
        }
    }

    /**
     * Opens the desktop, with whatever was clicked brought to the front.
     *
     * @return true if a tab was clicked rather than the world behind it
     */
    public static boolean clicked(Minecraft minecraft, int mouseX, int mouseY) {
        List<DevWindow> windows = ComposerScreen.instance().windows();
        int index = tabAt(minecraft, windows, mouseX, mouseY);
        if (index < 0) return false;

        if (index < windows.size()) ComposerScreen.instance().focus(windows.get(index));
        ComposerScreen.open();
        return true;
    }

    /** Which tab a point is on, or -1. */
    private static int tabAt(Minecraft minecraft, List<DevWindow> windows, int pointX, int pointY) {
        if (pointY < 0 || pointY >= HEIGHT) return -1;

        int x = MARGIN;
        for (int i = 0; i < Math.max(1, windows.size()); i++) {
            String label = windows.isEmpty() ? EMPTY : windows.get(i).title();
            int tabWidth = width(minecraft, label);
            if (pointX >= x && pointX < x + tabWidth) return i;
            x += tabWidth + GAP;
        }
        return -1;
    }

    private static int width(Minecraft minecraft, String label) {
        return Draw.textWidth(minecraft, label) + PADDING * 2;
    }
}
