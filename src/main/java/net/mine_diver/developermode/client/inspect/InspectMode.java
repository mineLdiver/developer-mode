package net.mine_diver.developermode.client.inspect;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.gui.window.EntityEditorWindow;
import net.mine_diver.developermode.client.summon.SummonMode;
import net.mine_diver.developermode.client.EntityTargeting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Picking an entity by looking at it, with the world still there.
 *
 * <p>This is deliberately not a {@link net.minecraft.client.gui.screen.Screen}.
 * A screen would take the mouse, stop you aiming, and sit behind the composer's
 * blur, which is what makes picking through a window feel detached from
 * whatever you are aiming at. Instead the mode is a flag: the world keeps
 * rendering and you keep aiming normally, entities in front of you outline, and
 * a click takes the one under the crosshair.
 *
 * <p>Entered either by holding the inspect key or from the radial's Inspect
 * slot. Left click picks, right click backs out.
 */
public final class InspectMode {
    /** Shorter than {@link EntityTargeting#REACH}: this runs every frame. */
    private static final double REACH = 32;
    /** Cosine of the half angle that counts as "in front of you". */
    private static final double CONE = 0.9;
    private static final float PICK_MARGIN = 0.25F;

    private static final List<Entity> candidates = new ArrayList<>();

    private static boolean active;
    private static boolean heldByKey;
    private static boolean keyWasDown;
    private static Entity focused;

    private InspectMode() {}

    public static boolean isActive() {
        return active;
    }

    public static Entity focused() {
        return focused;
    }

    public static List<Entity> candidates() {
        return candidates;
    }

    public static void enter() {
        SummonMode.exit();
        active = true;
        heldByKey = false;
    }

    public static void exit() {
        active = false;
        heldByKey = false;
        focused = null;
        candidates.clear();
    }

    /**
     * Polled once a frame from the world render, rather than driven off key
     * events, so entering and leaving track the key exactly and a mode left
     * open by a screen appearing cannot get stuck.
     */
    public static void update() {
        Minecraft minecraft = DeveloperModeClient.minecraft();
        boolean available = minecraft != null && minecraft.world != null
                && minecraft.player != null && minecraft.currentScreen == null;

        boolean keyDown = available && Keyboard.isKeyDown(DeveloperModeClient.INSPECT_KEY.code);
        if (keyDown && !keyWasDown) {
            SummonMode.exit();
            active = true;
            heldByKey = true;
        } else if (!keyDown && keyWasDown && heldByKey) {
            exit();
        }
        keyWasDown = keyDown;

        if (!active) return;
        if (!available) {
            exit();
            return;
        }
        recompute(minecraft);
    }

    /**
     * @return true if the click belonged to this mode and vanilla should not
     *         see it, which is what stops picking a mob from also punching it
     */
    public static boolean click(int button) {
        if (!active) return false;

        if (button == 1) {
            exit();
            return true;
        }
        if (button != 0) return false;

        Entity picked = focused;
        exit();
        if (picked != null) {
            EntityTargeting.set(picked);
            EntityEditorWindow.open(picked);
        }
        return true;
    }

    private static void recompute(Minecraft minecraft) {
        candidates.clear();
        focused = null;

        LivingEntity camera = minecraft.camera;
        if (camera == null) return;

        Vec3d origin = camera.getPosition(1);
        Vec3d look = camera.getLookVector(1);
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
    }
}
