package net.mine_diver.developermode.feature.player;

import net.mine_diver.developermode.api.Message;
import net.mine_diver.developermode.feature.Facing;
import net.mine_diver.developermode.feature.Ray;
import net.mine_diver.developermode.feature.net.packet.WarpC2SPacket;
import net.mine_diver.developermode.mixin.EntityAccessor;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResultType;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.modificationstation.stationapi.api.network.packet.PacketHelper;
import net.modificationstation.stationapi.api.util.SideUtil;

/**
 * Standing where you were looking.
 *
 * <p>The request carries only how far the sender can see. Where they are and
 * which way they are facing is something the world already knows to the tick,
 * so the ray is cast by whoever is going to honor it: the only places
 * reachable are the ones the world itself puts in front of them, which is a
 * stronger answer than checking a destination would be.
 *
 * <p>The ring holds the head still while it is up, so the direction this finds
 * is the one you were facing when you opened it. Aim first, then flick.
 */
public final class Warp {
    /** Clear of the face, once the player's own size is out of the way. */
    private static final double CLEARANCE = 0.05;

    private Warp() {}

    /** @param reach how far the sender can see, in blocks */
    public static void request(double reach) {
        PacketHelper.send(new WarpC2SPacket(reach));
    }

    /** @return what went wrong, or null if they got there */
    public static Message warp(PlayerEntity player, double reach) {
        // Beta keeps an entity's y at its eyes and hangs the box below it, so
        // this is already the camera.
        Vec3d origin = Vec3d.create(player.x, player.y, player.z);
        Vec3d look = player.getLookVector(1);

        HitResult hit = Ray.cast(player.world, origin, look, reach);
        if (hit == null || hit.type != HitResultType.BLOCK) return Message.of("message.developermode.nothing_in_reach");

        Vec3d feet = landing(player.boundingBox, hit);
        // Back into Beta's terms, where the coordinate asked for is the eyes.
        double eyeY = feet.y + (player.y - player.boundingBox.minY);

        // Arriving is not falling. Whatever speed and drop were building up
        // belong to where the player was, and carrying them over would land
        // them with fall damage they never took.
        player.velocityX = 0;
        player.velocityY = 0;
        player.velocityZ = 0;
        ((EntityAccessor) player).setFallDistance(0);

        // Lambdas rather than method references, so the server only class is
        // not loaded on the side that never calls it.
        SideUtil.run(
                () -> player.setPositionAndAngles(feet.x, eyeY, feet.z, player.yaw, player.pitch),
                () -> ServerWarp.teleport(player, feet.x, eyeY, feet.z));
        return null;
    }

    /**
     * Where the feet go for something this size, stood off the face that was
     * hit: by half a width beside a wall, the whole height under a ceiling,
     * and nothing at all on top of a floor, which is where feet belong.
     *
     * <p>Shared with the preview, so the ghost stands exactly where the warp
     * would put you.
     */
    public static Vec3d landing(Box box, HitResult hit) {
        double halfWidth = (box.maxX - box.minX) / 2;
        double height = box.maxY - box.minY;
        double up = Facing.y(hit.side);

        return Vec3d.create(
                hit.pos.x + Facing.x(hit.side) * (halfWidth + CLEARANCE),
                hit.pos.y + (up < 0 ? -(height + CLEARANCE) : up * CLEARANCE),
                hit.pos.z + Facing.z(hit.side) * (halfWidth + CLEARANCE));
    }
}
