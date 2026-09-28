package net.mine_diver.developermode.feature;

/**
 * How far a request reaches, as its sender says.
 *
 * <p>A request that reaches into the world carries how far the sender can see
 * and nothing else. Where they stand is something the world already knows to
 * the tick, so the only places a request can reach are the ones around them.
 *
 * <p>How far is still the client's to say, because how far the world is drawn
 * is something only it knows, and reaching past the fog is reaching for
 * nothing. It is a number off a socket all the same, so it is held to the
 * widest any client draws.
 */
public final class Aim {
    /** Beta's widest draw distance, which nothing can see past. */
    public static final double FARTHEST = 256;

    private Aim() {}

    /** Whether a reach off the wire means anything at all. */
    public static boolean sensible(double reach) {
        return !Double.isNaN(reach) && !Double.isInfinite(reach);
    }

    /** A sensible reach, held to what a client can actually see. */
    public static double clamp(double reach) {
        return Math.min(Math.max(reach, 0), FARTHEST);
    }
}
