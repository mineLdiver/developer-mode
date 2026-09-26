package net.mine_diver.developermode.feature.world;

import net.mine_diver.developermode.feature.net.packet.ClearC2SPacket;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.util.math.Box;
import net.modificationstation.stationapi.api.network.packet.PacketHelper;

/**
 * Taking away what a test left lying around.
 *
 * <p>Everything goes as far as the sender can see, which is as far as they
 * could have noticed it. Entities are removed rather than killed: nothing
 * drops, nothing burns, and nothing is left to sweep up after the sweep.
 */
public final class Clearing {
    /** Dropped items and arrows. */
    public static final byte LITTER = 0;
    /** Everything alive that is not a player. */
    public static final byte MOBS = 1;

    private Clearing() {}

    /** @param reach how far the sender can see, in blocks */
    public static void request(byte what, double reach) {
        PacketHelper.send(new ClearC2SPacket(what, reach));
    }

    public static boolean legal(byte what) {
        return what == LITTER || what == MOBS;
    }

    /** @return how many were taken away */
    public static int clear(PlayerEntity player, byte what, double reach) {
        Box around = Box.create(
                player.x - reach, player.y - reach, player.z - reach,
                player.x + reach, player.y + reach, player.z + reach);

        int cleared = 0;
        for (Object loaded : player.world.getEntities(player, around)) {
            Entity entity = (Entity) loaded;
            if (entity.dead || !matches(entity, what)) continue;
            entity.markDead();
            cleared++;
        }
        return cleared;
    }

    private static boolean matches(Entity entity, byte what) {
        return switch (what) {
            case LITTER -> entity instanceof ItemEntity || entity instanceof ArrowEntity;
            case MOBS -> entity instanceof LivingEntity && !(entity instanceof PlayerEntity);
            default -> false;
        };
    }
}
