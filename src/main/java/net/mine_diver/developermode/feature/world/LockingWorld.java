package net.mine_diver.developermode.feature.world;

/**
 * A world, seen as something whose sun and weather can be locked.
 *
 * <p>Every world is one. It keeps a reference to its {@link LockedSky} so the
 * sky can be drawn and the weather ticked without a lookup by name each time,
 * and so a client that is only told what is locked has somewhere to keep it.
 *
 * @see net.mine_diver.developermode.mixin.WorldLocksMixin
 */
public interface LockingWorld {
    /** What is locked, or null until it has been looked up or been told. */
    LockedSky developermode_locks();

    void developermode_locks(LockedSky locks);
}
