package net.mine_diver.developermode.feature.world;

import net.mine_diver.developermode.feature.net.packet.WeatherC2SPacket;
import net.minecraft.world.World;
import net.minecraft.world.WorldProperties;
import net.modificationstation.stationapi.api.network.packet.PacketHelper;

/**
 * Changing the sky.
 *
 * <p>Beta counts rain and thunder down separately, and flips each one when
 * its count runs out. A count of zero is not a flip but a question: the next
 * tick answers it with a fresh spell of whatever the weather now is, as long
 * as a spell of that weather would naturally last. So setting the weather
 * clears both counts, and how long it holds is the world's own call rather
 * than a guess made here.
 *
 * <p>A server tells its clients when rain starts and stops, and nothing about
 * thunder, so a client on a server only ever sees a storm as rain.
 */
public final class Weather {
    public static final byte CLEAR = 0;
    public static final byte RAIN = 1;
    public static final byte STORM = 2;

    private Weather() {}

    public static void request(byte weather) {
        PacketHelper.send(new WeatherC2SPacket(weather));
    }

    public static boolean legal(byte weather) {
        return weather == CLEAR || weather == RAIN || weather == STORM;
    }

    /** What the sky is doing, as far as this world has been told. */
    public static byte of(World world) {
        WorldProperties properties = world.getProperties();
        if (!properties.getRaining()) return CLEAR;
        return properties.getThundering() ? STORM : RAIN;
    }

    /** @return what went wrong, or null if the sky is changing */
    public static String set(World world, byte weather) {
        if (!legal(weather)) return "No such weather";
        // Beta only runs the weather under an open sky, so anywhere else the
        // counts would be set and never looked at.
        if (world.dimension.hasCeiling) return "No sky here";

        WorldProperties properties = world.getProperties();
        properties.setRaining(weather != CLEAR);
        properties.setThundering(weather == STORM);
        properties.setRainTime(0);
        properties.setThunderTime(0);
        return null;
    }
}
