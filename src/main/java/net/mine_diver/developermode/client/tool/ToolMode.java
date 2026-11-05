package net.mine_diver.developermode.client.tool;

import net.mine_diver.developermode.api.Message;
import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.Sight;
import net.mine_diver.developermode.client.summon.SummonMode;
import net.mine_diver.developermode.feature.Ray;
import net.mine_diver.developermode.feature.net.DevStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResultType;
import org.lwjgl.input.Mouse;

/**
 * Holding a tool: left click uses it, right click puts it away.
 *
 * <p>The same shape as {@link SummonMode}, and exclusive with it, since both
 * want the same clicks. Not a screen, so the world stays live and you go on
 * looking around to aim, and it stays armed after each use so a field can be
 * grown or a row of mobs struck without a trip back to the ring for each.
 *
 * <p>Nothing but the right click puts it away. Opening the ring, a window or
 * the pause menu only holds it, so a tool is exactly as armed after them as
 * it was before, and the HUD says so the whole time.
 */
public final class ToolMode {
    private static Tool armed;
    /** The block face in front of you, or null when there is none in reach. */
    private static HitResult target;

    private static boolean useWasDown;
    private static boolean cancelWasDown;
    private static int used;
    private static Message error;
    /** The block the error was earned on, which is as long as it stays true. */
    private static long errorAt;
    private static int statusSequence;

    private ToolMode() {}

    public static boolean isArmed() {
        return armed != null;
    }

    /** The tool in hand, or null when there is none. */
    public static Tool armed() {
        return armed;
    }

    public static HitResult target() {
        return target;
    }

    public static int used() {
        return used;
    }

    public static Message error() {
        return error;
    }

    public static void arm(Tool tool) {
        SummonMode.exit();
        armed = tool;
        target = null;
        used = 0;
        error = null;
        statusSequence = DevStatus.sequence(tool.kind());
        // The click that chose the tool may still be held. Require a release
        // before the first use, or it goes off the moment the ring closes.
        useWasDown = true;
        cancelWasDown = true;
    }

    public static void exit() {
        armed = null;
        target = null;
    }

    /** Polled once a frame from the world render. */
    public static void update() {
        if (armed == null) return;

        Minecraft minecraft = DeveloperModeClient.minecraft();
        boolean available = minecraft != null && minecraft.world != null
                && minecraft.player != null && minecraft.currentScreen == null;
        if (!available) {
            // Held rather than dropped, and a click that closes whatever is
            // on top must not count as one aimed at the world.
            target = null;
            useWasDown = true;
            cancelWasDown = true;
            return;
        }

        updateTarget(minecraft);
        // A refusal is about where it was aimed. Somewhere else is a new
        // question, and the old answer would only be in the way of it.
        if (error != null && key(target) != errorAt) error = null;
        readAnswers();
        handleButtons();
    }

    private static void updateTarget(Minecraft minecraft) {
        LivingEntity camera = minecraft.camera;
        if (camera == null) {
            target = null;
            return;
        }
        HitResult hit = Ray.cast(camera.world, camera.getPosition(1), camera.getLookVector(1), Sight.reach());
        target = hit != null && hit.type == HitResultType.BLOCK ? hit : null;
    }

    private static void handleButtons() {
        // Edge detected, like summoning, since vanilla's click handler repeats
        // while the button is held and one click should be one use.
        boolean useDown = Mouse.isButtonDown(0);
        if (useDown && !useWasDown) armed.use(Sight.reach());
        useWasDown = useDown;

        boolean cancelDown = Mouse.isButtonDown(1);
        if (cancelDown && !cancelWasDown) exit();
        cancelWasDown = cancelDown;
    }

    /**
     * Picks up what came of the last use.
     *
     * <p>Counted from the answers rather than the clicks, so the tally is of
     * what the world actually let happen.
     */
    private static void readAnswers() {
        String kind = armed.kind();
        if (DevStatus.sequence(kind) == statusSequence) return;
        statusSequence = DevStatus.sequence(kind);

        if (DevStatus.ok(kind)) {
            used++;
            error = null;
        } else {
            error = DevStatus.message(kind);
            errorAt = key(target);
        }
    }

    /** One number per block, or one for nothing at all. */
    private static long key(HitResult hit) {
        if (hit == null) return Long.MIN_VALUE;
        return ((long) hit.blockX & 0x3FFFFFF) << 38 | ((long) hit.blockY & 0xFFF) << 26 | (long) hit.blockZ & 0x3FFFFFF;
    }
}
