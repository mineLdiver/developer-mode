package net.mine_diver.developermode.feature.world.transform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Transforms in the order they are laid over the noise, each switchable and
 * movable on its own, the way a modeling program stacks modifiers.
 *
 * <p>The first reads the generator's noise, and each after it reads the one
 * before. Switching one off leaves it in its place, so it can be compared on
 * and off without losing its settings.
 */
public final class Stack {
    private final List<Transform> transforms = new ArrayList<>();

    public List<Transform> transforms() {
        return Collections.unmodifiableList(transforms);
    }

    public void add(Transform transform) {
        transforms.add(transform);
    }

    public void remove(Transform transform) {
        transforms.remove(transform);
    }

    /** Moves a transform earlier, for a negative step, or later. */
    public void move(Transform transform, int step) {
        int from = transforms.indexOf(transform);
        if (from < 0) return;
        int to = Math.max(0, Math.min(transforms.size() - 1, from + step));
        transforms.remove(from);
        transforms.add(to, transform);
    }

    /** Whether any transform is switched on, which is when there is anything to lay over. */
    public boolean active() {
        for (Transform transform : transforms) if (transform.enabled()) return true;
        return false;
    }

    /** The noise with every transform that is switched on laid over it, in order. */
    public Sampler over(Sampler noise, long seed) {
        Sampler sampler = noise;
        for (Transform transform : transforms) if (transform.enabled()) sampler = transform.over(sampler, seed);
        return sampler;
    }

    /**
     * Where the noise is finally read for a point, through every transform
     * that looks elsewhere: the last one's lookup is followed first, since it
     * reads the stage below it, which reads the one below that.
     */
    public Transform.Lookup lookup(long seed) {
        List<Transform.Lookup> lookups = new ArrayList<>();
        for (Transform transform : transforms) {
            if (!transform.enabled()) continue;
            Transform.Lookup lookup = transform.lookup(seed);
            if (lookup != null) lookups.add(0, lookup);
        }
        return (x, y, z, into) -> {
            into[0] = x;
            into[1] = z;
            for (Transform.Lookup lookup : lookups) lookup.source(into[0], y, into[1], into);
        };
    }

    /** How far sideways the whole stack can look, in blocks. */
    public int reach() {
        int reach = 0;
        for (Transform transform : transforms) if (transform.enabled()) reach += transform.reach();
        return reach;
    }

    /** One that will not change when this one is tuned further, for a build to hold on to. */
    public Stack copy() {
        Stack copy = new Stack();
        for (Transform transform : transforms) copy.add(transform.copy());
        return copy;
    }

    /**
     * Changes whenever anything that decides the result does: the order,
     * which are switched on, and every number. Watched to know when to build
     * again.
     */
    public long fingerprint() {
        long hash = 17;
        for (Transform transform : transforms) {
            hash = hash * 31 + transform.getClass().hashCode();
            hash = hash * 31 + (transform.enabled() ? 1 : 0);
            for (Parameter parameter : transform.parameters()) hash = hash * 31 + Double.hashCode(parameter.get());
        }
        return hash;
    }
}
