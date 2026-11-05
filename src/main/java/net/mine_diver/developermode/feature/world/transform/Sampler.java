package net.mine_diver.developermode.feature.world.transform;

/**
 * A density defined everywhere, in world coordinates: a generator's noise,
 * or that noise with transforms laid over it.
 */
@FunctionalInterface
public interface Sampler {
    /** At a block. NaN where there is nothing to say, such as above what a generator builds. */
    double at(int x, int y, int z);

    /**
     * Between blocks, blended from the eight around the point, the way a
     * generator blends its own grid. A warp looks things up here, so a
     * spiral comes out as a smooth wall rather than steps.
     */
    default double between(double x, double y, double z) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);
        double fx = x - x0;
        double fy = y - y0;
        double fz = z - z0;
        double value = 0;
        for (int corner = 0; corner < 8; corner++) {
            int dx = corner & 1;
            int dy = corner >> 1 & 1;
            int dz = corner >> 2;
            double weight = (dx == 0 ? 1 - fx : fx) * (dy == 0 ? 1 - fy : fy) * (dz == 0 ? 1 - fz : fz);
            if (weight == 0) continue;
            value += weight * at(x0 + dx, y0 + dy, z0 + dz);
        }
        return value;
    }
}
