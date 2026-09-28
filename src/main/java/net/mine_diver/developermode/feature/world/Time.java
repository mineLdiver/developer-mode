package net.mine_diver.developermode.feature.world;

import net.mine_diver.developermode.api.Message;
import net.minecraft.world.World;

/**
 * Moving the sun.
 *
 * <p>The clock only ever goes forward. Scheduled block ticks are due at a
 * world time, so turning it back would hold every one of them until the clock
 * caught up again: water would stop mid flow and redstone mid pulse, for as
 * long as the day was turned back by. Going forward only brings them due,
 * which is what waiting would have done anyway.
 *
 * <p>The four times are a quarter of a day apart, which is also how much of
 * the day each of them stands for when the ring shows what time it is.
 */
public final class Time {
    /** Ticks in a day, sunrise to sunrise. */
    public static final int DAY = 24000;

    public static final int DAWN = 0;
    public static final int NOON = DAY / 4;
    public static final int DUSK = DAY / 2;
    public static final int MIDNIGHT = DAY * 3 / 4;

    private Time() {}

    /**
     * Whether the day is nearer this time than any of the others.
     *
     * <p>Half open, so that exactly one of the four is ever the answer.
     */
    public static boolean around(World world, int timeOfDay) {
        long now = Locks.isTimeLocked(world) ? Locks.lockedTime(world) : world.getTime();
        return Math.floorMod(now - timeOfDay + DAY / 8, DAY) < DAY / 4;
    }

    /**
     * Forward to the next time the day reads this, which is now if it
     * already does.
     *
     * <p>While the sun is locked, this moves where it is locked instead, and
     * the clock is left to go on as it was. It catches up when it is unlocked.
     *
     * @return what went wrong, or null if the sun is there
     */
    public static Message set(World world, int timeOfDay) {
        if (Locks.isTimeLocked(world)) {
            Locks.lockTimeAt(world, timeOfDay);
            return null;
        }

        long now = world.getTime();
        long then = now - Math.floorMod(now, DAY) + timeOfDay;
        if (then < now) then += DAY;
        world.getProperties().setTime(then);
        return null;
    }
}
