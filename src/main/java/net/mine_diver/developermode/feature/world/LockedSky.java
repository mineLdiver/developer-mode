package net.mine_diver.developermode.feature.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.PersistentState;

/**
 * What is locked in one world's sky, kept with the world's own save.
 *
 * <p>Saved the way Beta saves maps, as a file in the world's data folder,
 * so a lock is part of the world rather than of whoever set it: it is there
 * again when the world is opened again, and it is gone with the world when
 * the world is deleted.
 *
 * <p>Built by the world's state manager when it finds the file, which it does
 * through the constructor that takes only the id.
 */
public final class LockedSky extends PersistentState {
    private static final String TIME_KEY = "LockedTime";
    private static final String WEATHER_KEY = "LockedWeather";

    /** The time of day the sun is locked at, or {@link Locks#NONE}. */
    private int time = Locks.NONE;
    /** The weather the sky is locked to, or {@link Locks#NONE}. */
    private int weather = Locks.NONE;

    public LockedSky(String id) {
        super(id);
    }

    public int time() {
        return time;
    }

    public int weather() {
        return weather;
    }

    void set(int time, int weather) {
        if (time == this.time && weather == this.weather) return;
        this.time = time;
        this.weather = weather;
        markDirty();
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        int stored = nbt.contains(TIME_KEY) ? nbt.getInt(TIME_KEY) : Locks.NONE;
        // A number off the disk, which a hand edit or a later version could
        // have made anything.
        time = stored >= 0 && stored < Time.DAY ? stored : Locks.NONE;
        int storedWeather = nbt.contains(WEATHER_KEY) ? nbt.getInt(WEATHER_KEY) : Locks.NONE;
        weather = Weather.legal((byte) storedWeather) && storedWeather == (byte) storedWeather
                ? storedWeather : Locks.NONE;
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        nbt.putInt(TIME_KEY, time);
        nbt.putInt(WEATHER_KEY, weather);
    }
}
