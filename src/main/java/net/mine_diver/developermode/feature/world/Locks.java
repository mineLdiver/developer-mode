package net.mine_diver.developermode.feature.world;

import net.mine_diver.developermode.api.Message;
import net.mine_diver.developermode.feature.net.packet.LockC2SPacket;
import net.mine_diver.developermode.feature.net.packet.LockS2CPacket;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import net.minecraft.world.WorldProperties;
import net.modificationstation.stationapi.api.network.packet.PacketHelper;

/**
 * Locking the sun or the weather where they are.
 *
 * <p>The sun is locked without stopping the clock. Scheduled block ticks are
 * due at a world time, so a stopped clock would stop water mid flow and
 * redstone mid pulse for as long as it stayed stopped. What is locked instead
 * is where the sky says the day is, which Beta works out in one place for the
 * sun, the light, the mobs that spawn in it and whether a bed can be slept in,
 * so all of them lock together. Unlocking moves the clock forward to the time
 * that was locked, so the day carries on from where it stood rather than
 * jumping to wherever the clock got to underneath.
 *
 * <p>The weather is locked by putting it back before every weather tick and
 * never letting its counts run out, which is the only thing in Beta that
 * changes it. Which weather is kept rather than only that some is, because a
 * server never tells its clients about thunder, and a client asked to show a
 * locked storm has no other way of knowing it is one. Unlocking clears the
 * counts, and the next tick answers with a natural spell of whatever the
 * weather is by then.
 *
 * <p>Locks are saved with the world. A client draws the sky itself, so it is
 * told what is locked whenever it is told about the world, and again whenever
 * that changes.
 */
public final class Locks {
    /** A locked time that locks nothing. */
    public static final int NONE = -1;

    public static final byte TIME = 0;
    public static final byte WEATHER = 1;

    /**
     * Where the weather counts are kept. Beta takes one off before it checks
     * for zero, so anything above one never runs out.
     */
    private static final int LOCKED_COUNT = 2;

    private Locks() {}

    /**
     * @param value the time of day or the weather to lock to, which is
     *              ignored when unlocking
     */
    public static void request(byte what, boolean lock, int value) {
        PacketHelper.send(new LockC2SPacket(what, lock, value));
    }

    public static int lockedTime(World world) {
        LockedSky locks = ((LockingWorld) world).developermode_locks();
        return locks == null ? NONE : locks.time();
    }

    public static boolean isTimeLocked(World world) {
        return lockedTime(world) != NONE;
    }

    /** The weather the sky is locked to, or {@link #NONE}. */
    public static int lockedWeather(World world) {
        LockedSky locks = ((LockingWorld) world).developermode_locks();
        return locks == null ? NONE : locks.weather();
    }

    public static boolean isWeatherLocked(World world) {
        return lockedWeather(world) != NONE;
    }

    /** @return what went wrong, or null if it is locked or unlocked */
    public static Message lock(World world, byte what, boolean lock, int value) {
        LockedSky locks = load(world);
        if (locks == null) return Message.of("message.developermode.world_keeps_nothing");

        if (what == TIME) {
            if (lock) {
                if (value < 0 || value >= Time.DAY) return Message.of("message.developermode.nonsense_time");
                // Brought round to it first, so unlocking later carries on
                // from here rather than from wherever the clock was.
                if (!isTimeLocked(world)) Time.set(world, value);
                locks.set(value, locks.weather());
            } else if (isTimeLocked(world)) {
                int locked = locks.time();
                locks.set(NONE, locks.weather());
                Time.set(world, locked);
            }
        } else if (what == WEATHER) {
            if (world.dimension.hasCeiling) return Message.of("message.developermode.no_sky");
            if (lock) {
                if (!Weather.legal((byte) value) || value != (byte) value)
                    return Message.of("message.developermode.no_such_weather");
                locks.set(locks.time(), value);
                Message failure = Weather.set(world, (byte) value);
                if (failure != null) return failure;
            } else if (locks.weather() != NONE) {
                locks.set(locks.time(), NONE);
                WorldProperties properties = world.getProperties();
                properties.setRainTime(0);
                properties.setThunderTime(0);
            }
        } else {
            return Message.of("message.developermode.nothing_to_lock");
        }
        tellEveryone(world);
        return null;
    }

    /** Moves which weather is locked, for weather that is locked already. */
    static void lockWeatherTo(World world, byte weather) {
        LockedSky locks = load(world);
        if (locks == null || locks.weather() == NONE) return;
        locks.set(locks.time(), weather);
        tellEveryone(world);
    }

    /** Moves where the sun is locked, for a clock that is locked already. */
    static void lockTimeAt(World world, int timeOfDay) {
        LockedSky locks = load(world);
        if (locks == null) return;
        locks.set(timeOfDay, locks.weather());
        tellEveryone(world);
    }

    /**
     * Keeps locked weather from changing. Called before every weather tick,
     * which is also the first moment a world is certainly done being built,
     * so it is where a world's saved locks are picked up.
     */
    public static void keepWeather(World world, WorldProperties properties) {
        load(world);
        int weather = lockedWeather(world);
        if (weather == NONE) return;
        properties.setRaining(weather != Weather.CLEAR);
        properties.setThundering(weather == Weather.STORM);
        properties.setRainTime(Math.max(properties.getRainTime(), LOCKED_COUNT));
        properties.setThunderTime(Math.max(properties.getThunderTime(), LOCKED_COUNT));
    }

    /**
     * What a client is told, taken as it is. The world it draws is not the one
     * that decides, so nothing it is told is saved on this side.
     */
    public static void mirror(World world, int lockedTime, int lockedWeather) {
        LockingWorld locking = (LockingWorld) world;
        LockedSky locks = locking.developermode_locks();
        if (locks == null) {
            locks = new LockedSky(id(world));
            locking.developermode_locks(locks);
        }
        locks.set(lockedTime < 0 || lockedTime >= Time.DAY ? NONE : lockedTime,
                Weather.legal((byte) lockedWeather) && lockedWeather == (byte) lockedWeather ? lockedWeather : NONE);
    }

    public static void tell(PlayerEntity player, World world) {
        load(world);
        PacketHelper.sendTo(player, new LockS2CPacket(lockedTime(world), lockedWeather(world)));
    }

    private static void tellEveryone(World world) {
        for (Object player : world.players) tell((PlayerEntity) player, world);
    }

    /**
     * The world's saved locks, read from its data folder the first time they
     * are wanted, or new ones if it has none yet.
     *
     * <p>Never while the world is being built: the Nether on a server is handed
     * the Overworld's state manager only once its own constructor is done, and
     * anything registered before then goes with the manager it replaces.
     */
    private static LockedSky load(World world) {
        LockingWorld locking = (LockingWorld) world;
        LockedSky locks = locking.developermode_locks();
        if (locks != null || world.persistentStateManager == null) return locks;

        String id = id(world);
        PersistentState stored = world.getOrCreateState(LockedSky.class, id);
        if (stored instanceof LockedSky found) {
            locks = found;
        } else {
            locks = new LockedSky(id);
            world.setState(id, locks);
        }
        locking.developermode_locks(locks);
        return locks;
    }

    /** One per dimension, since a server's dimensions share one data folder. */
    private static String id(World world) {
        return "developermode_locks_" + world.dimension.id;
    }
}
