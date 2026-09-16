package net.mine_diver.developermode.client;

import net.mine_diver.developermode.client.gui.DevScreen;
import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.Theme;
import net.mine_diver.developermode.feature.entity.FrozenEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

/**
 * Global state for the developer UI: whether it is up, and what it is still
 * holding on to when it is not.
 */
public final class DeveloperUi {
    private static World lastWorld;
    private static boolean wasOnScreen;

    private DeveloperUi() {}

    /** True while any of the mod's own screens is the current one. */
    public static boolean isOnScreen() {
        Minecraft minecraft = DeveloperModeClient.minecraft();
        return minecraft != null && minecraft.currentScreen instanceof DevScreen;
    }

    /**
     * Enforces the rule that an automatic freeze never outlives the screen that
     * asked for one.
     *
     * <p>Done centrally, once a frame, rather than by having each window undo
     * its own freeze on the way out. Windows live on a desktop that closing
     * does not destroy, so "the editor is gone" and "the editor released its
     * entity" are not the same event, and any scheme built on close hooks
     * leaks the first time someone presses escape instead of the close cross.
     */
    public static void releaseUnusedFreezes() {
        Minecraft minecraft = DeveloperModeClient.minecraft();
        if (minecraft == null) return;

        // Entities do not survive a world change, and neither should anything
        // claiming to be holding one still.
        if (lastWorld != minecraft.world) {
            lastWorld = minecraft.world;
            FrozenEntities.releaseAll();
        }

        boolean onScreen = isOnScreen();
        if (!onScreen) {
            FrozenEntities.releaseAutomatic();
            // Locally every frame, since that costs nothing and a window may
            // have re-asserted one since the last. Over a wire only on the
            // frame the UI went away, since that is the only one that is news.
            if (wasOnScreen) Freezing.releaseAutomatic();
        }
        wasOnScreen = onScreen;
    }

    /**
     * Says so, out in the world, when something is still deliberately frozen.
     * A held entity is invisible otherwise: it simply stands there looking like
     * any other entity that happens not to be moving.
     */
    public static void renderHeldIndicator(Minecraft minecraft) {
        if (minecraft.currentScreen != null) return;

        int held = FrozenEntities.heldCount();
        if (held == 0) return;

        String message = held + (held == 1 ? " entity held" : " entities held");
        Draw.text(minecraft, message, 4, 14, Theme.ACCENT);
    }
}
