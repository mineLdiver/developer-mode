package net.mine_diver.developermode.client;

import net.minecraft.client.Minecraft;

/**
 * How far the tools look into the world.
 *
 * <p>As far as it is drawn, and no further. Aiming is done by pointing at
 * something, so the honest limit is the last thing there is to point at: past
 * the fog there is nothing on screen to have meant.
 *
 * <p>Beta draws {@code 256 >> viewDistance} blocks, which is 256 on the widest
 * setting and 32 on the narrowest. Its own raycast gives up after two hundred
 * block boundaries, so the widest setting reaches a little short of the fog
 * rather than all the way to it.
 */
public final class Sight {
    /** What the view distance option divides down from, in blocks. */
    private static final int FAR = 256;
    /** Used before there is a game to ask, which is only ever a race. */
    private static final double UNKNOWN = FAR;

    private Sight() {}

    public static double reach() {
        Minecraft minecraft = DeveloperModeClient.minecraft();
        return minecraft == null || minecraft.options == null
                ? UNKNOWN
                : FAR >> minecraft.options.viewDistance;
    }
}
