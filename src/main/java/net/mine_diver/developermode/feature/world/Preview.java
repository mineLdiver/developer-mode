package net.mine_diver.developermode.feature.world;

import net.mine_diver.developermode.DeveloperMode;
import net.mine_diver.developermode.feature.Message;
import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSource;
import net.modificationstation.stationapi.impl.world.chunk.FlattenedChunk;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Chunks generated off to the side, for looking at what a generator makes
 * without it ever reaching the world.
 *
 * <p>A preview has a generator of its own, made fresh from the dimension, so
 * nothing done to it can leak into the chunks the world goes on to generate.
 * It is given the world's seed and the coordinates being looked at, so it
 * shows exactly what that spot would become. Its chunks belong to it alone:
 * the world never hears of them and nothing saves them.
 *
 * <p>The chunks are made a few at a time with {@link #step}, nearest the
 * middle first, so the picture fills in from the center out. All of it runs on
 * the main thread, since StationAPI's generation keeps its working arrays in
 * static fields and a second thread would trample the world's own generation.
 *
 * <p>A preview stops the generator at the step the question needs, and the
 * earlier the step, the cheaper it is.
 */
public final class Preview {
    /** How far a preview lets the generator go. */
    public enum Step {
        /**
         * The density the generator shapes terrain from, shown as solid
         * wherever it is above zero: the bare shape, with no surface and no
         * caves. Only for a generator {@link Noise} knows.
         */
        NOISE("gui.developermode.preview.step.noise"),
        /**
         * The blocks {@code getChunk} hands back: the shape, the surface and
         * the caves, before any decoration.
         */
        TERRAIN("gui.developermode.preview.step.terrain");

        public final String key;

        Step(String key) {
            this.key = key;
        }
    }

    private final World world;
    private final ChunkSource generator;
    private final Step step;
    private final int fromX;
    private final int fromZ;
    private final int across;
    private final FlattenedChunk[] chunks;
    /** The density of each chunk, at the Noise step, and null at any other. */
    private final Noise.Field[] noise;
    /** Indices into {@link #chunks}, nearest the middle first. */
    private final int[] order;

    private int made;
    private Message failure;

    private Preview(World world, ChunkSource generator, Step step, int centerX, int centerZ, int radius) {
        this.world = world;
        this.generator = generator;
        this.step = step;
        this.across = radius * 2 + 1;
        this.fromX = centerX - radius;
        this.fromZ = centerZ - radius;
        this.chunks = new FlattenedChunk[across * across];
        this.noise = step == Step.NOISE ? new Noise.Field[chunks.length] : null;

        Integer[] byDistance = new Integer[chunks.length];
        for (int i = 0; i < byDistance.length; i++) byDistance[i] = i;
        Arrays.sort(byDistance, Comparator.comparingInt(i -> {
            int dx = i % across - radius;
            int dz = i / across - radius;
            return dx * dx + dz * dz;
        }));
        this.order = Arrays.stream(byDistance).mapToInt(Integer::intValue).toArray();
    }

    /**
     * A preview of the square of chunks around one, not generated yet.
     *
     * @param radius how far out from that chunk, in chunks
     */
    public static Preview around(World world, Step step, int chunkX, int chunkZ, int radius) {
        return new Preview(world, world.dimension.createChunkGenerator(), step, chunkX, chunkZ, radius);
    }

    /**
     * Generates for about as long as it is given, and always at least one
     * chunk.
     *
     * @return whether it is finished, which it also is once the generator
     *         has failed
     */
    public boolean step(long budgetNanos) {
        long until = System.nanoTime() + budgetNanos;
        while (!done()) {
            int index = order[made];
            int x = fromX + index % across;
            int z = fromZ + index / across;
            try {
                if (step == Step.NOISE) {
                    Noise.Field field = Noise.sample(generator, world, x, z);
                    if (field == null) {
                        failure = Message.of("message.developermode.no_noise");
                        return true;
                    }
                    noise[index] = field;
                    chunks[index] = solidOf(field, x, z);
                } else {
                    Chunk chunk = generator.getChunk(x, z);
                    if (!(chunk instanceof FlattenedChunk flattened)) {
                        failure = Message.of("message.developermode.cannot_preview");
                        return true;
                    }
                    chunks[index] = flattened;
                }
            } catch (RuntimeException | LinkageError error) {
                DeveloperMode.LOGGER.error("Generating preview chunk {}, {} threw", x, z, error);
                failure = Message.of("message.developermode.preview_threw", error.getClass().getSimpleName(), x, z);
                return true;
            }
            made++;
            if (System.nanoTime() >= until) break;
        }
        return done();
    }

    /**
     * A chunk of stone wherever the density is above zero, for drawing the
     * Noise step the way every other step is drawn. Written into its sections
     * directly, since setting blocks through the chunk would tell the world
     * about them.
     */
    private FlattenedChunk solidOf(Noise.Field field, int chunkX, int chunkZ) {
        FlattenedChunk chunk = new FlattenedChunk(world, chunkX, chunkZ);
        var stone = Block.STONE.getDefaultState();
        int bottom = field.bottomY();
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++)
                for (int y = bottom; y < bottom + field.height(); y++)
                    if (field.solid(x, y, z)) chunk.getOrCreateSection(y, false).setBlockState(x, y & 15, z, stone);
        return chunk;
    }

    public Step step() {
        return step;
    }

    /**
     * The density at a block, in world coordinates.
     *
     * @return NaN where there is none: at any step but Noise, outside the
     *         preview, or in a chunk not made yet
     */
    public float density(int x, int y, int z) {
        if (noise == null) return Float.NaN;
        int chunkX = (x >> 4) - fromX;
        int chunkZ = (z >> 4) - fromZ;
        if (chunkX < 0 || chunkZ < 0 || chunkX >= across || chunkZ >= across) return Float.NaN;
        Noise.Field field = noise[chunkZ * across + chunkX];
        if (field == null || y < field.bottomY() || y >= field.bottomY() + field.height()) return Float.NaN;
        return field.at(x & 15, y, z & 15);
    }

    public boolean done() {
        return failure != null || made == chunks.length;
    }

    /** What stopped it, or null if nothing has. */
    public Message failure() {
        return failure;
    }

    /** How many chunks are made so far. */
    public int made() {
        return made;
    }

    /** How many chunks there are to make. */
    public int count() {
        return chunks.length;
    }

    /** How many chunks across the square is. */
    public int across() {
        return across;
    }

    /** The chunk coordinates of its lowest corner. */
    public int fromX() {
        return fromX;
    }

    public int fromZ() {
        return fromZ;
    }

    public World world() {
        return world;
    }

    public int bottomY() {
        return world.getBottomY();
    }

    public int height() {
        return world.getHeight();
    }

    /** @return the chunk at these chunk coordinates, or null if it is outside or not made yet */
    public FlattenedChunk chunk(int chunkX, int chunkZ) {
        int x = chunkX - fromX;
        int z = chunkZ - fromZ;
        if (x < 0 || z < 0 || x >= across || z >= across) return null;
        return chunks[z * across + x];
    }

    /**
     * The chunks in the order they are made, nearest the middle first, as
     * indices: chunk {@code i} is at {@code fromX + i % across, fromZ + i / across}.
     */
    public int orderAt(int position) {
        return order[position];
    }
}
