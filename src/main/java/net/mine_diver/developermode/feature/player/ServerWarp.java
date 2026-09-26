package net.mine_diver.developermode.feature.player;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;

/**
 * Moving a player that is on the far end of a connection.
 *
 * <p>Setting the position would only move the server's copy, and the next
 * movement packet from the client would put it straight back. The packet
 * handler is what tells the client it has been moved and what stops it
 * arguing, so the teleport goes through there.
 *
 * <p>Loaded only from {@link Warp}, and only on the side that has connections.
 */
final class ServerWarp {
    private ServerWarp() {}

    static void teleport(PlayerEntity player, double x, double y, double z) {
        ServerPlayerEntity sender = (ServerPlayerEntity) player;
        sender.networkHandler.teleport(x, y, z, sender.yaw, sender.pitch);
    }
}
