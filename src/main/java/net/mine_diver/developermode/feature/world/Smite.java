package net.mine_diver.developermode.feature.world;

import net.mine_diver.developermode.feature.Aim;
import net.mine_diver.developermode.feature.net.packet.SmiteC2SPacket;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.modificationstation.stationapi.api.network.packet.PacketHelper;

/**
 * Lightning where you are looking, for finding out what a mob or a block
 * makes of being struck, without waiting on a storm to do it.
 *
 * <p>Spawned as a global entity, the way a storm spawns its own. A server
 * tracks ordinary entities only near the players who can see them, and
 * lightning is seen from much further off than that, so it is the one kind
 * of entity a server announces to everyone in range the moment it exists.
 */
public final class Smite {
    private Smite() {}

    /** @param reach how far the sender can see, in blocks */
    public static void request(double reach) {
        PacketHelper.send(new SmiteC2SPacket(reach));
    }

    /** @return what went wrong, or null if it struck */
    public static String smite(PlayerEntity player, double reach) {
        HitResult hit = Aim.block(player, reach);
        if (hit == null) return "Nothing in reach";

        player.world.spawnGlobalEntity(new LightningEntity(player.world, hit.pos.x, hit.pos.y, hit.pos.z));
        return null;
    }
}
