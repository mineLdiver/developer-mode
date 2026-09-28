package net.mine_diver.developermode.feature.setting;

import net.mine_diver.developermode.api.Message;
import net.mine_diver.developermode.api.setting.Setting;
import net.mine_diver.developermode.feature.world.Locks;
import net.mine_diver.developermode.feature.world.Weather;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What the sky is doing.
 *
 * <p>A spell of weather runs out on its own, so this drifts, and can be
 * locked. Somewhere without a sky it has nothing to say, and the world turns
 * down anything asked of it there.
 */
public final class WeatherSetting implements Setting {
    public static final String CLEAR = "clear";
    public static final String RAIN = "rain";
    public static final String STORM = "storm";

    /** Indexed by the {@link Weather} kind each one is. */
    private static final List<String> VALUES = List.of(CLEAR, RAIN, STORM);

    WeatherSetting() {}

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
        World world = skyOf(player);
        return world == null ? null : VALUES.get(Weather.of(world));
    }

    @Override
    public @Nullable String locked(PlayerEntity player) {
        World world = skyOf(player);
        int locked = world == null ? Locks.NONE : Locks.lockedWeather(world);
        return locked == Locks.NONE ? null : VALUES.get(locked);
    }

    @Override
    public Message set(PlayerEntity player, String value) {
        return Weather.set(player.world, kind(value));
    }

    @Override
    public Message lock(PlayerEntity player, String value) {
        return Locks.lock(player.world, Locks.WEATHER, true, kind(value));
    }

    @Override
    public Message unlock(PlayerEntity player) {
        return Locks.lock(player.world, Locks.WEATHER, false, 0);
    }

    private static byte kind(String value) {
        return (byte) VALUES.indexOf(value);
    }

    /** The player's world, if it has a sky to have weather in. */
    private static @Nullable World skyOf(PlayerEntity player) {
        if (player == null || player.world == null || player.world.dimension.hasCeiling) return null;
        return player.world;
    }
}
