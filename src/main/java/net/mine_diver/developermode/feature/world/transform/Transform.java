package net.mine_diver.developermode.feature.world.transform;

import java.util.List;

/**
 * A change laid over a generator's noise, between it and the blocks made from
 * it.
 *
 * <p>Each one reads the density of the stage below it and gives its own: it
 * can look elsewhere, which is a warp, or change the value where it is, which
 * is a shift. They exist only in developer-mode, so nothing a mod ships ever
 * depends on one; what comes out of tuning one is numbers, or code written
 * from it.
 */
public abstract class Transform {
    private final String key;
    private final List<Parameter> parameters;
    private boolean enabled = true;

    /** @param key the translation key of its name, with each parameter's name beneath it */
    protected Transform(String key, Parameter... parameters) {
        this.key = key;
        this.parameters = List.of(parameters);
    }

    public String key() {
        return key;
    }

    public List<Parameter> parameters() {
        return parameters;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * The density with this laid over it.
     *
     * @param seed the world's seed, for anything placed at random
     */
    public abstract Sampler over(Sampler below, long seed);

    /**
     * Where this looks for the value of a point, for drawing how it bends
     * the world.
     *
     * @return null for one that never looks anywhere but the point itself
     */
    public Lookup lookup(long seed) {
        return null;
    }

    /** Where a transform takes a point's value from. */
    @FunctionalInterface
    public interface Lookup {
        /** @param into receives the x and z looked at, the height being left where it is */
        void source(double x, double y, double z, double[] into);
    }

    /** How far sideways it can look for a value, in blocks, and 0 for one that never looks elsewhere. */
    public int reach() {
        return 0;
    }

    /** One that will not change when this one is tuned further, for a build to hold on to. */
    public abstract Transform copy();

    /** Copies the settings of another of the same kind into a fresh one. */
    protected final <T extends Transform> T copyInto(T fresh) {
        fresh.setEnabled(enabled);
        for (int i = 0; i < parameters.size(); i++) fresh.parameters().get(i).set(parameters.get(i).get());
        return fresh;
    }
}
