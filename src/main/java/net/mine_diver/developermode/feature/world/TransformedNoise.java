package net.mine_diver.developermode.feature.world;

import net.mine_diver.developermode.feature.world.transform.Sampler;
import net.mine_diver.developermode.feature.world.transform.Stack;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSource;

import java.util.Map;

/**
 * A generator's noise with a stack of transforms laid over it, worked out a
 * chunk at a time.
 *
 * <p>Transforms that warp look for values sideways, past the chunk being
 * worked out, so the generator's own noise is kept for every chunk asked
 * about, wherever the asking came from.
 */
public final class TransformedNoise {
    private final ChunkSource generator;
    private final World world;
    private final Stack stack;
    private final Map<Long, Noise.Field> raw;
    private Sampler sampler;

    /**
     * @param raw where the generator's own noise is kept by chunk, which can
     *            be shared with another over the same ground and seed
     */
    public TransformedNoise(ChunkSource generator, World world, Stack stack, Map<Long, Noise.Field> raw) {
        this.generator = generator;
        this.world = world;
        this.stack = stack;
        this.raw = raw;
    }

    /** The generator's own noise for a chunk, worked out once. */
    public Noise.Field raw(int chunkX, int chunkZ) {
        return raw.computeIfAbsent((long) chunkX << 32 | chunkZ & 0xFFFFFFFFL,
                key -> Noise.sample(generator, world, chunkX, chunkZ));
    }

    /** A chunk's noise with the stack laid over it, worked out block by block. */
    public Noise.Field transformed(int chunkX, int chunkZ) {
        if (sampler == null) {
            Sampler noise = (x, y, z) -> {
                Noise.Field field = raw(x >> 4, z >> 4);
                if (field == null || y < field.bottomY() || y >= field.bottomY() + field.height()) return Double.NaN;
                return field.at(x & 15, y, z & 15);
            };
            sampler = stack.over(noise, world.getSeed());
        }
        int bottom = world.getBottomY();
        int height = world.getHeight();
        float[] values = new float[16 * 16 * height];
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++)
                for (int y = 0; y < height; y++)
                    values[Noise.index(x, y, z, height)] = (float) sampler.at(chunkX * 16 + x, bottom + y, chunkZ * 16 + z);
        return new Noise.Field(values, bottom, height);
    }
}
