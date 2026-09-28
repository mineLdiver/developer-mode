package net.mine_diver.developermode.client.tool;

import net.mine_diver.developermode.client.Lang;
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
 *
 * <p>All of it is under one translation key: the name is the key itself, and
 * the rest are {@code .hint}, {@code .verb}, {@code .one} and {@code .many}
 * beneath it.
 */
public enum Tool {
    WARP("gui.developermode.tool.warp", DevStatus.WARP, Warp::request),
    GROW("gui.developermode.tool.grow", DevStatus.GROW, Grow::request),
    BLAST("gui.developermode.tool.blast", DevStatus.BLAST, Blast::request),
    SMITE("gui.developermode.tool.smite", DevStatus.SMITE, Smite::request);

    private final String key;
    private final String kind;
    private final DoubleConsumer request;

    Tool(String key, String kind, DoubleConsumer request) {
        this.key = key;
        this.kind = kind;
        this.request = request;
    }

    /** The translation key the name is under, and the rest beneath it. */
    public String key() {
        return key;
    }

    public String label() {
        return Lang.get(key);
    }

    /** What a left click does, as in "left click to strike". */
    public String verb() {
        return Lang.get(key + ".verb");
    }

    /** How many times it has gone through, as in "3 strikes". */
    public String count(int done) {
        return Lang.get(key + (done == 1 ? ".one" : ".many"), done);
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
