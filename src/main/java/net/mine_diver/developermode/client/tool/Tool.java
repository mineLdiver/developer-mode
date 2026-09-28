package net.mine_diver.developermode.client.tool;

import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.player.Warp;
import net.mine_diver.developermode.feature.world.Blast;
import net.mine_diver.developermode.feature.world.Grow;
import net.mine_diver.developermode.feature.world.Smite;

import java.util.function.DoubleConsumer;

/**
 * Something the ring hands you to point at the world with.
 *
 * <p>Each one is a request aimed where you are looking, sent again with every
 * left click for as long as it is armed. What it says about itself is what
 * the HUD needs to keep it impossible to forget: a name, what a click does,
 * and how to count what it has done.
 */
public enum Tool {
    WARP("Warp", "warp", "warp", "warps", DevStatus.WARP, Warp::request),
    GROW("Grow", "grow", "grown", "grown", DevStatus.GROW, Grow::request),
    BLAST("Blast", "blast", "blast", "blasts", DevStatus.BLAST, Blast::request),
    SMITE("Smite", "strike", "strike", "strikes", DevStatus.SMITE, Smite::request);

    private final String label;
    private final String verb;
    private final String one;
    private final String many;
    private final String kind;
    private final DoubleConsumer request;

    Tool(String label, String verb, String one, String many, String kind, DoubleConsumer request) {
        this.label = label;
        this.verb = verb;
        this.one = one;
        this.many = many;
        this.kind = kind;
        this.request = request;
    }

    public String label() {
        return label;
    }

    /** What a left click does, as in "left click to strike". */
    public String verb() {
        return verb;
    }

    /** How many times it has gone through, as in "3 strikes". */
    public String count(int done) {
        return done + " " + (done == 1 ? one : many);
    }

    /** The status kind its answers come back under. */
    public String kind() {
        return kind;
    }

    /** @param reach how far the sender can see, in blocks */
    public void use(double reach) {
        request.accept(reach);
    }
}
