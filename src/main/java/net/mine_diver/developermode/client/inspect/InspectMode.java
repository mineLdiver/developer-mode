package net.mine_diver.developermode.client.inspect;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.gui.Projection;
import net.mine_diver.developermode.client.gui.window.BlockEntityEditorWindow;
import net.mine_diver.developermode.client.gui.window.EntityEditorWindow;
import net.mine_diver.developermode.client.summon.SummonMode;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResultType;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Picking an entity by looking at it, with the world still there.
 *
 * <p>Picking is done with a pointer rather than the crosshair, so what is
 * under it is found by turning that pixel back into a ray through the world
 * instead of asking the camera where it is looking.
 *
 * <p>Block entities are picked the same way and compete on distance, so a chest
 * two blocks away wins over a cow ten blocks behind it. A block with nothing
 * behind its face is not a candidate at all, which leaves entities reachable
 * through walls the way they were.
 */
public final class InspectMode {
    /** How far the pointer's ray looks. This runs every frame. */
    private static final double REACH = 32;
    /** Cosine of the half angle that counts as "in front of you". */
    private static final double CONE = 0.9;
    private static final float PICK_MARGIN = 0.25F;

    private static final List<Entity> candidates = new ArrayList<>();

    private static boolean active;
    /** Where the pointer is, in window pixels as the mouse reports them. */
    private static int pointerX;
    private static int pointerY;
    private static Entity focused;
    private static boolean blockFocused;
    private static int blockX;
    private static int blockY;
    private static int blockZ;

    private InspectMode() {}

    public static boolean isActive() {
        return active;
    }

    public static Entity focused() {
        return focused;
    }

    /** Whether a block entity, rather than an entity, is under the crosshair. */
    public static boolean isBlockFocused() {
        return blockFocused;
    }

    public static int blockX() {
        return blockX;
    }

    public static int blockY() {
        return blockY;
    }

    public static int blockZ() {
        return blockZ;
    }

    public static List<Entity> candidates() {
        return candidates;
    }

    public static void enter() {
        SummonMode.exit();
        active = true;
    }

    /** Told each frame by the screen that owns the pointer. */
    public static void aimAt(int windowX, int windowY) {
        pointerX = windowX;
        pointerY = windowY;
    }

    public static void exit() {
        active = false;
        focused = null;
        blockFocused = false;
        candidates.clear();
    }

    /**
     * Polled once a frame from the world render, where the pointer's ray can
     * be worked out from the matrices the world was just drawn with.
     */
    public static void update(float tickDelta) {
        if (!active) return;

        Minecraft minecraft = DeveloperModeClient.minecraft();
        if (minecraft == null || minecraft.world == null || minecraft.player == null) {
            exit();
            return;
        }
        recompute(minecraft, tickDelta);
    }

    /** Opens whatever is under the pointer. */
    public static void pick() {
        Entity picked = focused;
        boolean pickedBlock = blockFocused;
        int blockAtX = blockX;
        int blockAtY = blockY;
        int blockAtZ = blockZ;

        if (pickedBlock) {
            BlockEntityEditorWindow.open(blockAtX, blockAtY, blockAtZ);
        } else if (picked != null) {
            EntityEditorWindow.open(picked);
        }
    }

    private static void recompute(Minecraft minecraft, float tickDelta) {
        candidates.clear();
        focused = null;
        blockFocused = false;

        LivingEntity camera = minecraft.camera;
        if (camera == null) return;

        // Camera relative, because that is the space the world is drawn in.
        Vec3d near = Projection.at(pointerX, pointerY, 0);
        Vec3d far = Projection.at(pointerX, pointerY, 1);
        if (near == null || far == null) return;

        double towardX = far.x - near.x;
        double towardY = far.y - near.y;
        double towardZ = far.z - near.z;
        double span = Math.sqrt(towardX * towardX + towardY * towardY + towardZ * towardZ);
        if (span < 1.0E-6) return;

        double eyeX = camera.lastTickX + (camera.x - camera.lastTickX) * tickDelta;
        double eyeY = camera.lastTickY + (camera.y - camera.lastTickY) * tickDelta;
        double eyeZ = camera.lastTickZ + (camera.z - camera.lastTickZ) * tickDelta;

        Vec3d origin = Vec3d.create(eyeX + near.x, eyeY + near.y, eyeZ + near.z);
        Vec3d look = Vec3d.create(towardX / span, towardY / span, towardZ / span);
        Vec3d end = origin.add(look.x * REACH, look.y * REACH, look.z * REACH);

        Box sweep = camera.boundingBox
                .stretch(look.x * REACH, look.y * REACH, look.z * REACH)
                .expand(4, 4, 4);

        double bestDistance = Double.MAX_VALUE;

        for (Object loaded : minecraft.world.getEntities(camera, sweep)) {
            Entity entity = (Entity) loaded;
            if (entity.dead) continue;

            Box box = entity.boundingBox;
            double toX = (box.minX + box.maxX) / 2 - origin.x;
            double toY = (box.minY + box.maxY) / 2 - origin.y;
            double toZ = (box.minZ + box.maxZ) / 2 - origin.z;
            double length = Math.sqrt(toX * toX + toY * toY + toZ * toZ);
            if (length > REACH) continue;

            // Outlining everything loaded would light up the whole chunk, so
            // only show what is roughly in front of you: the things a small
            // turn of the head could actually put under the crosshair.
            if (length > 0.01 && (toX * look.x + toY * look.y + toZ * look.z) / length < CONE) continue;

            candidates.add(entity);

            float margin = entity.getTargetingMargin() + PICK_MARGIN;
            Box pickBox = box.expand(margin, margin, margin);

            double hitDistance;
            if (pickBox.contains(origin)) {
                hitDistance = 0;
            } else {
                HitResult hit = pickBox.raycast(origin, end);
                if (hit == null) continue;
                hitDistance = origin.distanceTo(hit.pos);
            }

            if (hitDistance < bestDistance) {
                focused = entity;
                bestDistance = hitDistance;
            }
        }

        focusBlockEntity(minecraft, origin, end, bestDistance);
    }

    /**
     * Lets the block under the crosshair compete with the entity, on distance.
     *
     * <p>Only a block that has a block entity is a candidate, since a block
     * with nothing behind its face has no NBT to open. A plain wall therefore
     * does not shadow an entity standing behind it, which is the same rule the
     * outlines already draw by.
     */
    private static void focusBlockEntity(Minecraft minecraft, Vec3d origin, Vec3d end,
                                         double bestDistance) {
        HitResult hit = minecraft.world.raycast(origin, end);
        if (hit == null || hit.type != HitResultType.BLOCK) return;
        if (minecraft.world.getBlockEntity(hit.blockX, hit.blockY, hit.blockZ) == null) return;

        if (origin.distanceTo(hit.pos) >= bestDistance) return;

        focused = null;
        blockFocused = true;
        blockX = hit.blockX;
        blockY = hit.blockY;
        blockZ = hit.blockZ;
    }
}
