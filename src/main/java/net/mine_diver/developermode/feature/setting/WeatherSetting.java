package net.mine_diver.developermode.feature.setting;

import net.mine_diver.developermode.api.Message;
import net.mine_diver.developermode.api.setting.LockableSetting;
import net.mine_diver.developermode.feature.world.Locks;
import net.mine_diver.developermode.feature.world.Weather;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.modificationstation.stationapi.api.util.StringIdentifiable;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What the sky is doing.
 *
 * <p>A spell of weather runs out on its own, so this drifts, and can be
 * locked. Somewhere without a sky it has nothing to say, and the world turns
 * down anything asked of it there.
 */
public final class WeatherSetting implements LockableSetting<WeatherSetting.WeatherKind> {
    public enum WeatherKind implements StringIdentifiable {
        CLEAR("clear", Weather.CLEAR),
        RAIN("rain", Weather.RAIN),
        STORM("storm", Weather.STORM);

        private final String name;
        private final byte kind;

        WeatherKind(String name, byte kind) {
            this.name = name;
            this.kind = kind;
        }

        @Override
        public String asString() {
            return name;
        }
    }

    private static final List<WeatherKind> VALUES = List.of(WeatherKind.values());

    WeatherSetting() {}

    @Override
    public List<WeatherKind> values() {
        return VALUES;
    }

    @Override
    public @Nullable WeatherKind current(PlayerEntity player) {
        World world = skyOf(player);
        return world == null ? null : of(Weather.of(world));
    }

    @Override
    public @Nullable WeatherKind locked(PlayerEntity player) {
        World world = skyOf(player);
        int locked = world == null ? Locks.NONE : Locks.lockedWeather(world);
        return locked == Locks.NONE ? null : of(locked);
    }

    @Override
    public Message set(PlayerEntity player, WeatherKind value) {
        return Weather.set(player.world, value.kind);
    }

    @Override
    public Message lock(PlayerEntity player, WeatherKind value) {
        return Locks.lock(player.world, Locks.WEATHER, true, value.kind);
    }

    @Override
    public Message unlock(PlayerEntity player) {
        return Locks.lock(player.world, Locks.WEATHER, false, 0);
    }

    private static @Nullable WeatherKind of(int kind) {
        for (WeatherKind weather : VALUES) {
            if (weather.kind == kind) return weather;
        }
        return null;
    }

    /** The player's world, if it has a sky to have weather in. */
    private static @Nullable World skyOf(PlayerEntity player) {
        if (player == null || player.world == null || player.world.dimension.hasCeiling) return null;
        return player.world;
    }
}
