package net.mine_diver.developermode.client;

import net.mine_diver.developermode.feature.world.transform.Stack;

/**
 * What the world generation windows share.
 *
 * <p>The transforms are tuned in one window and seen in another, so they live
 * here rather than in either. They last as long as the game does.
 */
public final class Workbench {
    /** The transforms every preview is built with. */
    public static final Stack STACK = new Stack();

    private Workbench() {}
}
