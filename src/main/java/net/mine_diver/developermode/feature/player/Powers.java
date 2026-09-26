package net.mine_diver.developermode.feature.player;

import net.mine_diver.developermode.feature.net.packet.PowersC2SPacket;
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
     * Asks for one power to change, and leaves the answer to come back as a
     * packet.
     *
     * <p>Flipping a switch can move the other one with it, which is the
     * {@link Power#legal} rule seen from the side that is asking: turning
     * noclip on asks for the flight it needs, and turning flight off gives up
     * the noclip that was resting on it.
     */
    public static void toggle(PlayerEntity player, int power) {
        if (player == null) return;

        int wanted = of(player) ^ power;
        if (power == Power.NOCLIP && (wanted & Power.NOCLIP) != 0) wanted |= Power.FLIGHT;
        PacketHelper.send(new PowersC2SPacket(Power.legal(wanted)));
    }
}
