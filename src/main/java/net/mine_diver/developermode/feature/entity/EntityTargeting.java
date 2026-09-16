package net.mine_diver.developermode.feature.entity;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Works out which entity you are looking at, and remembers it.
 *
 * <p>Vanilla's own {@code targetedEntity} is no use here: it is clamped to
 * three blocks outside of test mode, and it skips anything that is not
 * collidable, which rules out dropped items and projectiles. This does its own
 * cast, much further, at everything.
 *
 * <p>The target is captured the moment the menu key goes down, because once a
 * screen is up you cannot aim any more. That is the whole trick behind aiming
 * at something and then opening the ring.
 */
public final class EntityTargeting {
    public static final double REACH = 48;
    /** Aim assist. A dev poking at a chicken from across the room wants some. */
    private static final float EXTRA_MARGIN = 0.25F;

    private static Entity target;

    private EntityTargeting() {}

    /**
     * @return the entity now under the crosshair, or null
     */
    public static Entity capture() {
        Minecraft minecraft = DeveloperModeClient.minecraft();
        if (minecraft == null || minecraft.world == null || minecraft.camera == null) {
            target = null;
            return null;
        }

        LivingEntity camera = minecraft.camera;
        Vec3d origin = camera.getPosition(1);
        Vec3d look = camera.getLookVector(1);
        Vec3d end = origin.add(look.x * REACH, look.y * REACH, look.z * REACH);

        Box sweep = camera.boundingBox
                .stretch(look.x * REACH, look.y * REACH, look.z * REACH)
                .expand(1, 1, 1);

        Entity best = null;
        double bestDistance = Double.MAX_VALUE;

        List<?> candidates = minecraft.world.getEntities(camera, sweep);
        for (Object candidate : candidates) {
            Entity entity = (Entity) candidate;
            if (entity.dead) continue;

            float margin = entity.getTargetingMargin() + EXTRA_MARGIN;
            Box box = entity.boundingBox.expand(margin, margin, margin);

            double distance;
            if (box.contains(origin)) {
                distance = 0;
            } else {
                HitResult hit = box.raycast(origin, end);
                if (hit == null) continue;
                distance = origin.distanceTo(hit.pos);
            }

            if (distance < bestDistance) {
                best = entity;
                bestDistance = distance;
            }
        }

        target = best;
        return best;
    }

    /** The last captured entity, if it is still a live part of this world. */
    public static Entity current() {
        if (target == null) return null;

        Minecraft minecraft = DeveloperModeClient.minecraft();
        if (target.dead || minecraft == null || target.world != minecraft.world) {
            target = null;
        }
        return target;
    }

    public static void set(Entity entity) {
        target = entity;
    }
}
