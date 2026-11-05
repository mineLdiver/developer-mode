package net.mine_diver.developermode.client.tool;

import net.mine_diver.developermode.client.Lang;
import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.player.Warp;
import net.mine_diver.developermode.feature.world.Blast;
import net.mine_diver.developermode.feature.world.Grow;
import net.mine_diver.developermode.feature.world.Regenerate;
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
 * <p>Some act on an area of chunks around the one pointed at rather than on a
 * point, and say how far out they can reach. The scroll wheel sets how far
 * while one of them is armed.
 *
 * <p>All of it is under one translation key: the name is the key itself, and
 * the rest are {@code .hint}, {@code .verb}, {@code .one} and {@code .many}
 * beneath it, and for a tool with an area {@code .area.one} and
 * {@code .area.many}, which name it.
 */
public enum Tool {
    WARP("gui.developermode.tool.warp", DevStatus.WARP, Warp::request),
    GROW("gui.developermode.tool.grow", DevStatus.GROW, Grow::request),
    BLAST("gui.developermode.tool.blast", DevStatus.BLAST, Blast::request),
    SMITE("gui.developermode.tool.smite", DevStatus.SMITE, Smite::request),
    REGENERATE("gui.developermode.tool.regenerate", DevStatus.REGENERATE,
            Regenerate.MAX_RADIUS, Regenerate.DEFAULT_RADIUS, Regenerate::request);

    private final String key;
    private final String kind;
    private final int maxRadius;
    private final int defaultRadius;
    private final Request request;

    Tool(String key, String kind, DoubleConsumer request) {
        this(key, kind, 0, 0, (reach, radius) -> request.accept(reach));
    }

    Tool(String key, String kind, int maxRadius, int defaultRadius, Request request) {
        this.key = key;
        this.kind = kind;
        this.maxRadius = maxRadius;
        this.defaultRadius = defaultRadius;
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

    /** Whether it acts on chunks around the one pointed at, rather than on a point. */
    public boolean hasArea() {
        return maxRadius > 0;
    }

    /** How far out it can reach, in chunks, and 0 for one without an area. */
    public int maxRadius() {
        return maxRadius;
    }

    /** How far out it reaches until the wheel says otherwise. */
    public int defaultRadius() {
        return defaultRadius;
    }

    /** What the area at a radius covers, as in "3×3 chunks". */
    public String area(int radius) {
        int across = radius * 2 + 1;
        return radius == 0 ? Lang.get(key + ".area.one") : Lang.get(key + ".area.many", across, across);
    }

    /**
     * @param reach  how far the sender can see, in blocks
     * @param radius how far out from the chunk pointed at, which only a tool with an area reads
     */
    public void use(double reach, int radius) {
        request.send(reach, radius);
    }

    @FunctionalInterface
    private interface Request {
        void send(double reach, int radius);
    }
}
