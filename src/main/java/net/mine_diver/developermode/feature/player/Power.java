package net.mine_diver.developermode.feature.player;

/**
 * The powers a player can be granted, as bits of one mask.
 *
 * <p>One mask rather than one message per power, because the two sides have to
 * agree about all of it at once. What comes back from a request is the whole of
 * what you have, so a client mirrors it wholesale instead of working out which
 * of its own guesses survived.
 */
public final class Power {
    public static final int NONE = 0;

    /** Nothing in the world can hurt you, or take you out of it. */
    public static final int GOD = 1;
    /** Gravity stops applying, and the movement keys fly you instead. */
    public static final int FLIGHT = 1 << 1;
    /** Blocks stop being solid, and stop being able to smother you. */
    public static final int NOCLIP = 1 << 2;
    /** A block comes out in one hit, whatever it is and whatever is in hand. */
    public static final int INSTANT_BREAK = 1 << 3;

    public static final int ALL = GOD | FLIGHT | NOCLIP | INSTANT_BREAK;

    private Power() {}

    /**
     * The mask a request is allowed to turn into.
     *
     * <p>Noclip on its own is a hole in the floor: gravity is still there and
     * the ground has stopped catching you, so all it does is drop you out of
     * the world. It therefore only holds while flight does.
     *
     * <p>Applied to whatever arrives over the wire, since a mask is a handful
     * of bits a client chose and this is the one rule about them that has to
     * hold.
     */
    public static int legal(int mask) {
        mask &= ALL;
        return (mask & FLIGHT) == 0 ? mask & ~NOCLIP : mask;
    }
}
