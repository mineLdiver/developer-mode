package net.mine_diver.developermode.feature.world;

import net.mine_diver.developermode.api.Message;
import net.mine_diver.developermode.feature.Aim;
import net.mine_diver.developermode.feature.net.packet.BlastC2SPacket;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.modificationstation.stationapi.api.network.packet.PacketHelper;

/**
 * An explosion where you are looking, for finding out what a block or a mob
 * makes of one.
 *
 * <p>The player who asked is the source. An explosion leaves its source out
 * of the entities it hurts and throws, so aiming one at your own feet costs
 * the ground and not you.
 */
public final class Blast {
    /** As strong as TNT, which is the explosion most things are tested against. */
    private static final float POWER = 4;
    /**
     * The farthest any block is broken. Beta casts the explosion as rays that
     * set out with up to 1.3 times its power and lose 0.75 of it per block even
     * through open air, so no ray carries past this. Entities are hurt from
     * twice the power away, which is further.
     */
    public static final double REACH = POWER * 1.3 / 0.75;

    private Blast() {}

    /** @param reach how far the sender can see, in blocks */
    public static void request(double reach) {
        PacketHelper.send(new BlastC2SPacket(reach));
    }

    /** @return what went wrong, or null if it went off */
    public static Message blast(PlayerEntity player, double reach) {
        HitResult hit = Aim.block(player, reach);
        if (hit == null) return Message.of("message.developermode.nothing_in_reach");

        player.world.createExplosion(player, hit.pos.x, hit.pos.y, hit.pos.z, POWER, false);
        return null;
    }
}
