package net.mine_diver.developermode.feature;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResultType;
import net.minecraft.util.math.Vec3d;

/**
 * What a player is pointing at, found by the side that is going to act on it.
 *
 * <p>A request that aims carries only how far the sender can see. Where they
 * stand and which way they face is something the world already knows to the
 * tick, so the only places a request can reach are the ones the world itself
 * puts in front of them.
 *
 * <p>How far is still the client's to say, because how far the world is drawn
 * is something only it knows, and pointing past the fog is pointing at
 * nothing. It is a number off a socket all the same, so it is held to the
 * widest any client draws.
 */
public final class Aim {
    /** Beta's widest draw distance, which nothing can see past. */
    public static final double FARTHEST = 256;

    private Aim() {}

    /** Whether a reach off the wire means anything at all. */
    public static boolean sensible(double reach) {
        return !Double.isNaN(reach) && !Double.isInfinite(reach);
    }

    /** A sensible reach, held to what a client can actually see. */
    public static double clamp(double reach) {
        return Math.min(Math.max(reach, 0), FARTHEST);
    }

    /** @return the block face the player is looking at, or null if there is none in reach */
    public static HitResult block(PlayerEntity player, double reach) {
        // Beta keeps an entity's y at its eyes and hangs the box below it, so
        // this is already the camera.
        Vec3d origin = Vec3d.create(player.x, player.y, player.z);
        HitResult hit = Ray.cast(player.world, origin, player.getLookVector(1), reach);
        return hit != null && hit.type == HitResultType.BLOCK ? hit : null;
    }
}
