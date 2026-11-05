package net.mine_diver.developermode.feature.player;

import net.mine_diver.developermode.feature.net.Ops;
import net.mine_diver.developermode.feature.net.packet.PowersS2CPacket;
import net.minecraft.entity.player.PlayerEntity;
import net.modificationstation.stationapi.api.network.packet.PacketHelper;

/**
 * Reaching the powers a player has been granted.
 *
 * <p>They are kept on the player itself, so this is a cast and a field read.
 * It is worth a name of its own all the same: the read happens on every tick
 * of movement and every point of damage, and the side that answers a request
 * is not the side that makes one.
 *
 * <p>Whoever simulates the player is who holds them, because that is where
 * every power is carried out: damage is refused by the world that would have
 * applied it, and flight is flown by the client that owns the movement.
 */
public final class Powers {
    private Powers() {}

    /** Everything this player has, which for most players is nothing. */
    public static int of(PlayerEntity player) {
        return player == null ? Power.NONE : ((Empowered) player).developermode_powers();
    }

    /** Whether one power is on. */
    public static boolean has(PlayerEntity player, int power) {
        return (of(player) & power) != 0;
    }

    /** What the side that owns the player decided they have. */
    public static void set(PlayerEntity player, int mask) {
        if (player != null) ((Empowered) player).developermode_powers(Power.legal(mask));
    }

    /**
     * Settles what a player has on arriving in a world, and tells their client.
     *
     * <p>What they arrive with was saved, or carried over from the player they
     * were before a respawn, and either way it was granted to somebody who was
     * an operator at the time. That is checked again here, so taking someone
     * off the list takes their powers with it the next time they join.
     *
     * <p>A client keeps its copy on its own player object, which it replaces
     * whenever it is sent to another dimension, so it is told again every time
     * rather than only when something changes.
     */
    public static void arrive(PlayerEntity player) {
        if (of(player) != Power.NONE && !Ops.allows(player)) set(player, Power.NONE);
        PacketHelper.sendTo(player, new PowersS2CPacket(of(player)));
    }
}
