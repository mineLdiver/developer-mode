package net.mine_diver.developermode.client;

/**
 * A player, seen as something that can be in the air by choice.
 *
 * <p>Only ever true of the player this client is driving, and only on the
 * client, since that is the one place a player is flown. It is asked on every
 * tick of every living thing's movement, which is why it is a field on the
 * player rather than something looked up.
 *
 * @see net.mine_diver.developermode.mixin.PlayerFlightMixin
 */
public interface Flier {
    boolean developermode_flying();
}
