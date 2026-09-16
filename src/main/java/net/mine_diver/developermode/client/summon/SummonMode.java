package net.mine_diver.developermode.client.summon;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.inspect.InspectMode;
import net.mine_diver.developermode.feature.entity.EntitySummoning;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResultType;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.input.Mouse;

/**
 * Placing a new entity by pointing at where it should go.
 *
 * <p>The same shape as {@link InspectMode}: not a screen, world still live,
 * still aiming. A ghost of the entity stands wherever it would land, left click
 * places one, and the mode stays armed afterwards so a pen of test mobs is a
 * few clicks rather than a few round trips through the menu. Right click stops.
 */
public final class SummonMode {
    private static final double REACH = 48;
    /** Where it goes when you are pointing at open sky. */
    private static final double FALLBACK_DISTANCE = 4;
    /** Nudge off the hit face so the entity is not born inside the block. */
    private static final double SURFACE_CLEARANCE = 0.01;

    private static boolean active;
    private static String type;
    /** NBT this arming was set up with, or null to summon them as built. */
    private static NbtCompound preset;
    private static Entity preview;

    private static double targetX;
    private static double targetY;
    private static double targetZ;
    private static float yaw;
    private static boolean grounded;

    private static boolean buttonWasDown;
    private static boolean cancelWasDown;
    private static int placed;
    private static String error = "";

    private SummonMode() {}

    public static boolean isActive() {
        return active;
    }

    public static String type() {
        return type;
    }

    public static boolean hasPreset() {
        return preset != null;
    }

    public static Entity preview() {
        return preview;
    }

    public static boolean isGrounded() {
        return grounded;
    }

    public static int placed() {
        return placed;
    }

    public static String error() {
        return error;
    }

    /**
     * @param nbt what every entity this arming places is loaded with, or null
     *            for whatever the registry builds
     */
    public static void arm(String entityType, NbtCompound nbt) {
        InspectMode.exit();
        active = true;
        type = entityType;
        // Copied, so the arming is fixed at the moment it was taken. The window
        // that handed this over stays open and goes on being edited.
        preset = nbt == null ? null : nbt.copy();
        preview = null;
        placed = 0;
        error = "";
        // The click that chose the type is probably still held. Require a
        // release before the first placement, or it lands one immediately.
        buttonWasDown = true;
        cancelWasDown = true;
    }

    public static void exit() {
        active = false;
        type = null;
        preset = null;
        preview = null;
    }

    /** Polled once a frame from the world render. */
    public static void update() {
        if (!active) return;

        Minecraft minecraft = DeveloperModeClient.minecraft();
        boolean available = minecraft != null && minecraft.world != null
                && minecraft.player != null && minecraft.currentScreen == null;
        if (!available) {
            // Not exiting: the composer is probably just open on top. Hold the
            // arming so closing it puts you back where you were.
            buttonWasDown = true;
            cancelWasDown = true;
            return;
        }

        updateTarget(minecraft);
        updatePreview(minecraft);
        handleButtons();
    }

    private static void updateTarget(Minecraft minecraft) {
        LivingEntity camera = minecraft.camera;
        if (camera == null) return;

        Vec3d origin = camera.getPosition(1);
        Vec3d look = camera.getLookVector(1);

        HitResult hit = camera.raycast(REACH, 1);
        if (hit != null && hit.type == HitResultType.BLOCK) {
            targetX = hit.pos.x + normalX(hit.side) * SURFACE_CLEARANCE;
            targetY = hit.pos.y + normalY(hit.side) * SURFACE_CLEARANCE;
            targetZ = hit.pos.z + normalZ(hit.side) * SURFACE_CLEARANCE;
            grounded = true;
        } else {
            targetX = origin.x + look.x * FALLBACK_DISTANCE;
            targetY = origin.y + look.y * FALLBACK_DISTANCE;
            targetZ = origin.z + look.z * FALLBACK_DISTANCE;
            grounded = false;
        }

        // Facing you, which is what you want when testing how something reacts.
        yaw = minecraft.player.yaw + 180;
    }

    private static void updatePreview(Minecraft minecraft) {
        if (preview == null || preview.world != minecraft.world) rebuildPreview(minecraft);
        if (preview != null) EntitySummoning.place(preview, targetX, targetY, targetZ, yaw);
    }

    /**
     * Builds the ghost the same way the summon itself is built, preset and all,
     * so what is standing there before the click is what lands after it.
     */
    private static void rebuildPreview(Minecraft minecraft) {
        preview = EntitySummoning.create(type, minecraft.world);
        if (preview == null || preset == null) return;

        String failure = EntitySummoning.applyPreset(preview, preset);
        // Placing would fail the same way for the same reason, so this is not
        // stepping on a placement error, it is getting ahead of one.
        if (failure != null) error = "preset: " + failure;
    }

    private static void handleButtons() {
        // Edge detected here rather than off vanilla's click handler, which
        // repeats several times a second while the button is held and would
        // carpet the ground in zombies.
        boolean placeDown = Mouse.isButtonDown(0);
        if (placeDown && !buttonWasDown) place();
        buttonWasDown = placeDown;

        boolean cancelDown = Mouse.isButtonDown(1);
        if (cancelDown && !cancelWasDown) exit();
        cancelWasDown = cancelDown;
    }

    private static void place() {
        String failure = EntitySummoning.summon(type, targetX, targetY, targetZ, yaw, preset);
        if (failure == null) {
            placed++;
            error = "";
        } else {
            error = failure;
        }
    }

    private static double normalX(int side) {
        return side == 4 ? -1 : side == 5 ? 1 : 0;
    }

    private static double normalY(int side) {
        return side == 0 ? -1 : side == 1 ? 1 : 0;
    }

    private static double normalZ(int side) {
        return side == 2 ? -1 : side == 3 ? 1 : 0;
    }
}
