package net.mine_diver.developermode.feature.setting;

import net.mine_diver.developermode.api.Message;
import net.mine_diver.developermode.api.setting.Setting;
import net.mine_diver.developermode.feature.world.Locks;
import net.mine_diver.developermode.feature.world.Time;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Where the sun is, as one of four times a quarter of a day apart.
 *
 * <p>The day moves on from wherever it is set, so this drifts, and can be
 * locked. What it currently is is whichever of the four the day is nearest,
 * which is also how much of the day each of them stands for in the ring.
 */
public final class TimeSetting implements Setting {
    public static final String DAWN = "dawn";
    public static final String NOON = "noon";
    public static final String DUSK = "dusk";
    public static final String MIDNIGHT = "midnight";

    /** In the order the day passes them, which is also the order of {@link #TICKS}. */
    private static final List<String> VALUES = List.of(DAWN, NOON, DUSK, MIDNIGHT);
    private static final int[] TICKS = { Time.DAWN, Time.NOON, Time.DUSK, Time.MIDNIGHT };

    TimeSetting() {}

    @Override
    public List<String> values() {
        return VALUES;
    }

    @Override
    public boolean stays() {
        return false;
    }

    @Override
    public boolean lockable() {
        return true;
    }

    @Override
    public @Nullable String current(PlayerEntity player) {
        if (player == null || player.world == null) return null;
        for (int i = 0; i < TICKS.length; i++) {
            if (Time.around(player.world, TICKS[i])) return VALUES.get(i);
        }
        return null;
    }

    @Override
    public @Nullable String locked(PlayerEntity player) {
        if (player == null || player.world == null) return null;
        return named(Locks.lockedTime(player.world));
    }

    @Override
    public Message set(PlayerEntity player, String value) {
        return Time.set(player.world, ticks(value));
    }

    @Override
    public Message lock(PlayerEntity player, String value) {
        return Locks.lock(player.world, Locks.TIME, true, ticks(value));
    }

    @Override
    public Message unlock(PlayerEntity player) {
        return Locks.lock(player.world, Locks.TIME, false, 0);
    }

    private static int ticks(String value) {
        return TICKS[VALUES.indexOf(value)];
    }

    /** The time a lock holds, if it is one of the four, which a lock made here always is. */
    private static @Nullable String named(int ticks) {
        for (int i = 0; i < TICKS.length; i++) {
            if (TICKS[i] == ticks) return VALUES.get(i);
        }
        return null;
    }
}
