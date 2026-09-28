package net.mine_diver.developermode.feature.player;

import net.mine_diver.developermode.feature.Message;
import net.mine_diver.developermode.feature.net.packet.HealC2SPacket;
import net.mine_diver.developermode.mixin.EntityAccessor;
import net.minecraft.entity.player.PlayerEntity;
import net.modificationstation.stationapi.api.network.packet.PacketHelper;

/**
 * Putting a player back the way they started the fight.
 *
 * <p>Health belongs to whoever simulates the player, so like everything else
 * here it is asked for rather than taken. A server sends the new health along
 * on its own the moment it differs from what the client was last told.
 */
public final class Heal {
    private Heal() {}

    public static void request() {
        PacketHelper.send(new HealC2SPacket());
    }

    /**
     * Everything that is currently wrong with a player and can be undone.
     *
     * <p>Fall distance counts: it is damage that has already been earned and
     * is only waiting for the ground, so leaving it would heal someone who
     * then dies of the fall they were in the middle of.
     *
     * @return what went wrong, or null if the player is whole again
     */
    public static Message heal(PlayerEntity player) {
        if (player.health <= 0) return Message.of("message.developermode.not_while_dead");

        EntityAccessor entity = (EntityAccessor) player;
        player.health = player.maxHealth;
        player.fireTicks = 0;
        player.air = entity.getMaxAir();
        entity.setFallDistance(0);
        return null;
    }
}
