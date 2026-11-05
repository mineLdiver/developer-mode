package net.mine_diver.developermode.feature.world.transform;

/**
 * One number a transform is tuned by, kept within the range it makes sense
 * over.
 */
public final class Parameter {
    private final String key;
    private final double min;
    private final double max;
    /** Whether it only takes whole numbers, like a size in blocks. */
    private final boolean whole;
    private double value;

    /** @param key the translation key of its name */
    public Parameter(String key, double min, double max, double value, boolean whole) {
        this.key = key;
        this.min = min;
        this.max = max;
        this.whole = whole;
        set(value);
    }

    public String key() {
        return key;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public boolean whole() {
        return whole;
    }

    public double get() {
        return value;
    }

    public void set(double value) {
        double clamped = Math.max(min, Math.min(max, value));
        this.value = whole ? Math.round(clamped) : clamped;
    }

    Parameter copy() {
        return new Parameter(key, min, max, value, whole);
    }
}
