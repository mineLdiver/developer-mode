package net.mine_diver.developermode.feature.player;

/**
 * A player, seen as something that can be granted powers.
 *
 * <p>Every player is one. The mask is a field on the player, so it arrives
 * with them, it is read without a lookup on every tick of movement and every
 * point of damage, and it goes when they do. Nothing has to remember to forget
 * it.
 *
 * <p>Named the way the rest of what this mod puts on a game class is, because
 * that is what these end up as: methods on
 * {@link net.minecraft.entity.player.PlayerEntity} itself.
 *
 * @see net.mine_diver.developermode.mixin.PlayerEntityMixin
 */
public interface Empowered {
    int developermode_powers();

    void developermode_powers(int mask);
}
