package net.mine_diver.developermode.api.setting;

import net.mine_diver.developermode.api.Message;
import net.minecraft.entity.player.PlayerEntity;
import net.modificationstation.stationapi.api.util.StringIdentifiable;
import org.jetbrains.annotations.Nullable;

/**
 * A setting that drifts, and can be held at a value until it is let go.
 *
 * <p>A preset that locks one is lit while the lock holds, and choosing it
 * again lets go and leaves the setting wherever it was held. The ring's
 * slots for one can each be held down to lock at their value.
 *
 * <p>{@link #locked} is asked on the client, the way {@link #current} is, and
 * {@link #lock} and {@link #unlock} of the side that owns the state, the way
 * {@link #set} is, with the value already checked to be one of this setting's.
 */
public interface LockableSetting<V extends Enum<V> & StringIdentifiable> extends Setting<V> {
    /** Drifts, since a value that stays has no need of being held. */
    @Override
    default boolean stays() {
        return false;
    }

    /** The value it is held at, or null if it is not held. */
    @Nullable V locked(PlayerEntity player);

    /**
     * Holds it at a value, moving it there first.
     *
     * @return why it could not be, or null if it is done
     */
    @Nullable Message lock(PlayerEntity player, V value);

    /**
     * Lets go of a lock, and leaves it wherever it was held.
     *
     * @return why it could not be, or null if it is done
     */
    @Nullable Message unlock(PlayerEntity player);
}
