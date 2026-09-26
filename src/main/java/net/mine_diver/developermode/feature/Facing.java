package net.mine_diver.developermode.feature;

/**
 * Which way a block face points, by Beta's numbering of the six sides.
 *
 * <p>A hit result says which face was struck and nothing more, so anything
 * that wants to put something against that face, or clear of it, has to turn
 * the number back into a direction first.
 */
public final class Facing {
    private Facing() {}

    public static double x(int side) {
        return side == 4 ? -1 : side == 5 ? 1 : 0;
    }

    public static double y(int side) {
        return side == 0 ? -1 : side == 1 ? 1 : 0;
    }

    public static double z(int side) {
        return side == 2 ? -1 : side == 3 ? 1 : 0;
    }
}
