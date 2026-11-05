package net.mine_diver.developermode.feature.world.transform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Twists the noise into vortexes at scattered points.
 *
 * <p>The world is divided into square cells, and the seed and a cell decide
 * whether it has a vortex and where in it. Around each one, a block takes its
 * density from a point turned about the center, by the most at the center and
 * by less further out, so the land winds into a spiral. The turning also fades
 * above and below a height, so a vortex can be a whirl near the surface and
 * straight walls deep down.
 *
 * <p>It warps the noise rather than the blocks. Copying finished blocks from
 * turned positions makes stairs of every curve and twists the grass along with
 * the stone, while a warped density gives smooth walls and lets the surface be
 * laid on top afterwards. And it looks up every block rather than every noise
 * sample, since a generator's grid is several blocks wide and a spiral is not.
 *
 * <p>A vortex never reaches past its own radius, and the radius is held to the
 * size of a cell, so a block only has to ask its own cell and the eight around
 * it.
 */
public final class VortexWarp extends Transform {
    public static final String KIND = "vortex";

    private static final int CELL = 0;
    private static final int CHANCE = 1;
    private static final int RADIUS = 2;
    private static final int TWIST = 3;
    private static final int FALLOFF = 4;
    private static final int HEIGHT = 5;
    private static final int VERTICAL_REACH = 6;
    private static final int STRENGTH = 7;

    public VortexWarp() {
        super(KIND, "gui.developermode.transform.vortex",
                new Parameter("gui.developermode.transform.vortex.cell", 16, 512, 128, true),
                new Parameter("gui.developermode.transform.vortex.chance", 0, 1, 0.6, false),
                new Parameter("gui.developermode.transform.vortex.radius", 4, 256, 40, true),
                new Parameter("gui.developermode.transform.vortex.twist", 0, 4, 1.5, false),
                new Parameter("gui.developermode.transform.vortex.falloff", 0.25, 4, 2, false),
                new Parameter("gui.developermode.transform.vortex.height", 0, 255, 64, true),
                new Parameter("gui.developermode.transform.vortex.vertical_reach", 1, 256, 48, true),
                new Parameter("gui.developermode.transform.vortex.strength", 0, 1, 1, false));
    }

    /** Where the vortexes are for a seed, and how they turn the blocks around them. */
    public Placement placement(long seed) {
        return new Placement(seed);
    }

    @Override
    public Sampler over(Sampler below, long seed) {
        Placement placement = placement(seed);
        double[] at = new double[2];
        return (x, y, z) -> {
            placement.source(x + 0.5, y, z + 0.5, at);
            if (at[0] == x + 0.5 && at[1] == z + 0.5) return below.at(x, y, z);
            return below.between(at[0] - 0.5, y, at[1] - 0.5);
        };
    }

    @Override
    public Lookup lookup(long seed) {
        return placement(seed)::source;
    }

    @Override
    public int reach() {
        // A turned point stays as far from the center as it was, so it is
        // never more than a diameter from where it started.
        return enabled() ? (int) Math.ceil(radius() * 2) : 0;
    }

    @Override
    public Transform copy() {
        return copyInto(new VortexWarp());
    }

    private double radius() {
        return Math.min(parameters().get(RADIUS).get(), parameters().get(CELL).get());
    }

    /** One seed's vortexes, worked out a cell at a time and remembered. */
    public final class Placement {
        private final long seed;
        private final int cell = (int) parameters().get(CELL).get();
        private final double chance = parameters().get(CHANCE).get();
        private final double radius = VortexWarp.this.radius();
        private final double twist = parameters().get(TWIST).get() * Math.PI * 2;
        private final double falloff = parameters().get(FALLOFF).get();
        private final double height = parameters().get(HEIGHT).get();
        private final double verticalReach = parameters().get(VERTICAL_REACH).get();
        private final double strength = parameters().get(STRENGTH).get();
        /** Each cell's center, or an empty array for a cell without one. */
        private final Map<Long, double[]> centers = new HashMap<>();

        private Placement(long seed) {
            this.seed = seed;
        }

        public int cell() {
            return cell;
        }

        public double radius() {
            return radius;
        }

        /** @return the center of a cell's vortex, or null if it has none */
        public double[] center(int cellX, int cellZ) {
            double[] center = centers.computeIfAbsent((long) cellX << 32 | cellZ & 0xFFFFFFFFL, key -> {
                // Seeded the way Beta seeds a chunk, so neighboring cells are unrelated.
                Random random = new Random(seed ^ (cellX * 341873128712L + cellZ * 132897987541L));
                if (random.nextDouble() >= chance) return new double[0];
                return new double[] { (cellX + random.nextDouble()) * cell, (cellZ + random.nextDouble()) * cell };
            });
            return center.length == 0 ? null : center;
        }

        /** Every vortex center in a stretch of the world, for drawing them. */
        public List<double[]> centersIn(double minX, double minZ, double maxX, double maxZ) {
            List<double[]> found = new ArrayList<>();
            for (int cellZ = (int) Math.floor(minZ / cell) - 1; cellZ <= (int) Math.floor(maxZ / cell) + 1; cellZ++) {
                for (int cellX = (int) Math.floor(minX / cell) - 1; cellX <= (int) Math.floor(maxX / cell) + 1; cellX++) {
                    double[] center = center(cellX, cellZ);
                    if (center != null) found.add(center);
                }
            }
            return found;
        }

        /**
         * Where a point takes its density from: turned about every vortex it
         * is within reach of.
         *
         * @param into receives x and z
         */
        public void source(double x, double y, double z, double[] into) {
            double fromX = x;
            double fromZ = z;
            double vertical = 1 - Math.abs(y - height) / verticalReach;
            if (vertical > 0 && strength > 0) {
                int cellX = (int) Math.floor(x / cell);
                int cellZ = (int) Math.floor(z / cell);
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        double[] center = center(cellX + dx, cellZ + dz);
                        if (center == null) continue;
                        double offsetX = fromX - center[0];
                        double offsetZ = fromZ - center[1];
                        double distance = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
                        if (distance >= radius) continue;
                        double angle = twist * strength * vertical * Math.pow(1 - distance / radius, falloff);
                        double cos = Math.cos(angle);
                        double sin = Math.sin(angle);
                        fromX = center[0] + offsetX * cos - offsetZ * sin;
                        fromZ = center[1] + offsetX * sin + offsetZ * cos;
                    }
                }
            }
            into[0] = fromX;
            into[1] = fromZ;
        }
    }
}
