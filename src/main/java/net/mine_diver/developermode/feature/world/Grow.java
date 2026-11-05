package net.mine_diver.developermode.feature.world;

import net.mine_diver.developermode.api.Message;
import net.mine_diver.developermode.feature.Aim;
import net.mine_diver.developermode.feature.net.packet.GrowC2SPacket;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import net.modificationstation.stationapi.api.block.BlockState;
import net.modificationstation.stationapi.api.bonemeal.BonemealAPI;
import net.modificationstation.stationapi.api.network.packet.PacketHelper;

/**
 * Bone meal, without the bone meal.
 *
 * <p>Asks the same two questions StationAPI asks of bone meal: whether the
 * block has its own answer to it, the way a sapling or a crop does, and if
 * not, whether anything is registered to grow on it, the way grass and
 * flowers are on a grass block. A modded plant that answers bone meal answers
 * this too.
 */
public final class Grow {
    private Grow() {}

    /** @param reach how far the sender can see, in blocks */
    public static void request(double reach) {
        PacketHelper.send(new GrowC2SPacket(reach));
    }

    /** @return what went wrong, or null if something grew */
    public static Message grow(PlayerEntity player, double reach) {
        HitResult hit = Aim.block(player, reach);
        if (hit == null) return Message.of("message.developermode.nothing_in_reach");

        World world = player.world;
        int x = hit.blockX;
        int y = hit.blockY;
        int z = hit.blockZ;
        BlockState state = world.getBlockState(x, y, z);

        if (state.getBlock().onBonemealUse(world, x, y, z, state)) {
            world.setBlocksDirty(x, y, z, x, y, z);
            return null;
        }
        if (BonemealAPI.generate(world, x, y, z, state, hit.side)) {
            // Plants land anywhere in a few blocks around, not just on the
            // one that was hit.
            world.setBlocksDirty(x - 8, y - 8, z - 8, x + 8, y + 8, z + 8);
            return null;
        }
        return Message.of("message.developermode.nothing_grows");
    }
}
