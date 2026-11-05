package net.mine_diver.developermode.feature.world.transform;

import net.mine_diver.developermode.feature.world.Noise;
import net.minecraft.block.Block;
import net.minecraft.world.chunk.ChunkSource;

import java.util.function.Supplier;

/**
 * A preview's own noise, given to its generator in place of the generator's
 * for the length of one chunk.
 *
 * <p>Transforms change the density, but a generator makes blocks from the
 * density it works out itself. So while a preview's generator builds a chunk,
 * the blocks it fills from its noise are filled again from the preview's
 * transformed density, and everything the generator does next, the surface
 * and the caves, is done on top of the transformed shape. Only that one
 * generator instance is ever answered, so a transform can never reach a chunk
 * of the world.
 */
public final class Substitution {
    private static ChunkSource generator;
    private static Noise.Field field;

    private Substitution() {}

    /** Runs a build with the field standing in for the generator's own noise. */
    public static <T> T during(ChunkSource generator, Noise.Field field, Supplier<T> build) {
        Substitution.generator = generator;
        Substitution.field = field;
        try {
            return build.get();
        } finally {
            Substitution.generator = null;
            Substitution.field = null;
        }
    }

    /** @return the field standing in for this generator's noise, or null if there is none */
    public static Noise.Field fieldFor(Object generator) {
        return generator == Substitution.generator ? field : null;
    }

    /**
     * Fills a generator's legacy block array from a field the way the
     * generator fills it from its own noise: solid above zero, the fluid
     * below its level, and air elsewhere.
     *
     * @param fluidLevel how many layers from the bottom are fluid where not solid, 0 for none
     */
    public static void fill(byte[] blocks, Noise.Field field, Block solid, Block fluid, int fluidLevel) {
        // The array is 256 columns of a power of two layers, x outermost.
        int bits = Integer.numberOfTrailingZeros(blocks.length >> 8);
        int layers = Math.min(1 << bits, field.height());
        byte solidId = (byte) solid.id;
        byte fluidId = fluid == null ? 0 : (byte) fluid.id;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int column = x << bits + 4 | z << bits;
                for (int y = 0; y < layers; y++) {
                    float density = field.values()[Noise.index(x, y, z, field.height())];
                    blocks[column | y] = density > 0 ? solidId : y < fluidLevel ? fluidId : 0;
                }
            }
        }
    }
}
