package net.mine_diver.developermode.feature.setting;

import net.mine_diver.developermode.api.Message;
import net.mine_diver.developermode.api.setting.LockableSetting;
import net.mine_diver.developermode.feature.world.Locks;
import net.mine_diver.developermode.feature.world.Time;
import net.minecraft.entity.player.PlayerEntity;
import net.modificationstation.stationapi.api.util.StringIdentifiable;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Where the sun is, as one of four times a quarter of a day apart.
 *
 * <p>The day moves on from wherever it is set, so this drifts, and can be
 * locked. What it currently is is whichever of the four the day is nearest,
 * which is also how much of the day each of them stands for in the ring.
 */
public final class TimeSetting implements LockableSetting<TimeSetting.TimeOfDay> {
    /** In the order the day passes them. */
    public enum TimeOfDay implements StringIdentifiable {
        DAWN("dawn", Time.DAWN),
        NOON("noon", Time.NOON),
        DUSK("dusk", Time.DUSK),
        MIDNIGHT("midnight", Time.MIDNIGHT);

        private final String name;
        private final int ticks;

        TimeOfDay(String name, int ticks) {
            this.name = name;
            this.ticks = ticks;
        }

        @Override
        public String asString() {
            return name;
        }
    }

    private static final List<TimeOfDay> VALUES = List.of(TimeOfDay.values());

    TimeSetting() {}

    @Override
    public List<TimeOfDay> values() {
        return VALUES;
    }

    @Override
    public @Nullable TimeOfDay current(PlayerEntity player) {
        if (player == null || player.world == null) return null;
        for (TimeOfDay time : VALUES) {
            if (Time.around(player.world, time.ticks)) return time;
        }
        return null;
    }

    @Override
    public @Nullable TimeOfDay locked(PlayerEntity player) {
        if (player == null || player.world == null) return null;
        return at(Locks.lockedTime(player.world));
    }

    @Override
    public Message set(PlayerEntity player, TimeOfDay value) {
        return Time.set(player.world, value.ticks);
    }

    @Override
    public Message lock(PlayerEntity player, TimeOfDay value) {
        return Locks.lock(player.world, Locks.TIME, true, value.ticks);
    }

    @Override
    public Message unlock(PlayerEntity player) {
        return Locks.lock(player.world, Locks.TIME, false, 0);
    }

    /** The time a lock holds, if it is one of the four, which a lock made here always is. */
    private static @Nullable TimeOfDay at(int ticks) {
        for (TimeOfDay time : VALUES) {
            if (time.ticks == ticks) return time;
        }
        return null;
    }
}
