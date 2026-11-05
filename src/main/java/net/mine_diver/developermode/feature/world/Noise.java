package net.mine_diver.developermode.feature.world;

import net.mine_diver.developermode.mixin.TerrainNoiseAccessor;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.gen.chunk.NetherChunkGenerator;
import net.minecraft.world.gen.chunk.OverworldChunkGenerator;
import net.minecraft.world.gen.chunk.SkyChunkGenerator;
import net.modificationstation.stationapi.api.util.math.MathHelper;

/**
 * The noise a generator shapes terrain from, at the resolution of blocks.
 *
 * <p>Beta's generators sample a density on a coarse grid and blend between
 * the samples for every block in between: where it comes out above zero is
 * solid, and everything else is air or, low enough, water. That density is
 * what decides the shape of the land, before the surface is laid and the caves
 * are cut, and it is the number that terrain is tuned through.
 *
 * <p>Only generators this knows the grid of can be asked: the overworld's, the
 * Sky dimension's and the Nether's, and anything built on them. The grid is
 * read back from the generator after it has built a chunk's terrain into a
 * scratch array, so it is the one the chunk is really shaped from, and then
 * blended block by block the way the generator itself does.
 */
public final class Noise {
    private Noise() {}

    /**
     * One chunk's density, a value per block.
     *
     * @param values  laid out by {@link #index}
     * @param bottomY the height of the lowest layer
     */
    public record Field(float[] values, int bottomY, int height) {
        /** @param x and z within the chunk, y in the world */
        public float at(int x, int y, int z) {
            return values[index(x, y - bottomY, z, height)];
        }

        /** Above zero, which is what a generator fills. Never true where there is no value. */
        public boolean solid(int x, int y, int z) {
            return at(x, y, z) > 0;
        }
    }

    /** Where a block lies in a field's values, with y counted up from its bottom. */
    public static int index(int x, int y, int z, int height) {
        return (x * 16 + z) * height + y;
    }

    /** Whether this generator's noise can be asked for. */
    public static boolean knows(ChunkSource generator) {
        return grid(generator, 128) != null;
    }

    /**
     * @return the chunk's density, or null for a generator this does not
     *         know the grid of
     */
    public static Field sample(ChunkSource generator, World world, int chunkX, int chunkZ) {
        int height = world.getHeight();
        Grid grid = grid(generator, height);
        if (grid == null || !(generator instanceof TerrainNoiseAccessor accessor)) return null;

        // Built the way getChunk builds it, minus everything after the shape.
        byte[] scratch = new byte[1 << (MathHelper.ceilLog2(height) + 8)];
        if (generator instanceof NetherChunkGenerator nether) {
            nether.buildTerrain(chunkX, chunkZ, scratch);
        } else {
            BiomeSource biomeSource = world.method_1781();
            Biome[] biomes = biomeSource.getBiomesInArea(null, chunkX * 16, chunkZ * 16, 16, 16);
            double[] temperatures = biomeSource.temperatureMap;
            if (generator instanceof OverworldChunkGenerator overworld) overworld.buildTerrain(chunkX, chunkZ, scratch, biomes, temperatures);
            else ((SkyChunkGenerator) generator).buildTerrain(chunkX, chunkZ, scratch, biomes, temperatures);
        }
        double[] samples = accessor.getHeightMap();
        if (samples == null) return null;

        int across = 16 / grid.width + 1;
        // Past the top sample there is nothing to blend, and nothing built.
        int covered = Math.min(height, (grid.layers - 1) * grid.height);
        float[] values = new float[16 * 16 * height];
        for (int x = 0; x < 16; x++) {
            int cellX = x / grid.width;
            double alongX = (x % grid.width) / (double) grid.width;
            for (int z = 0; z < 16; z++) {
                int cellZ = z / grid.width;
                double alongZ = (z % grid.width) / (double) grid.width;
                for (int y = 0; y < height; y++) {
                    if (y >= covered) {
                        values[index(x, y, z, height)] = Float.NaN;
                        continue;
                    }
                    int cellY = Math.min(y / grid.height, grid.layers - 2);
                    double alongY = (y - cellY * grid.height) / (double) grid.height;
                    double value = 0;
                    for (int corner = 0; corner < 8; corner++) {
                        int dx = corner & 1;
                        int dz = corner >> 1 & 1;
                        int dy = corner >> 2;
                        double weight = (dx == 0 ? 1 - alongX : alongX)
                                * (dz == 0 ? 1 - alongZ : alongZ)
                                * (dy == 0 ? 1 - alongY : alongY);
                        if (weight == 0) continue;
                        value += weight * samples[((cellX + dx) * across + cellZ + dz) * grid.layers + cellY + dy];
                    }
                    values[index(x, y, z, height)] = (float) value;
                }
            }
        }
        return new Field(values, world.getBottomY(), height);
    }

    /**
     * How a known generator lays out its samples.
     *
     * @param width  blocks between samples across
     * @param height blocks between samples up
     * @param layers samples from the bottom to the top
     */
    private record Grid(int width, int height, int layers) {}

    private static Grid grid(ChunkSource generator, int worldHeight) {
        // The overworld's is StationAPI's, which adds layers for a taller
        // world and keeps eight blocks between them.
        if (generator instanceof OverworldChunkGenerator) return new Grid(4, 8, (1 << (MathHelper.ceilLog2(worldHeight) - 3)) + 1);
        // The Sky dimension builds into Beta's 128 tall array whatever the world's height.
        if (generator instanceof SkyChunkGenerator) return new Grid(8, 4, 33);
        if (generator instanceof NetherChunkGenerator) return new Grid(4, 8, 17);
        return null;
    }
}
