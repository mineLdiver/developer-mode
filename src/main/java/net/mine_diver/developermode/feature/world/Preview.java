package net.mine_diver.developermode.feature.world;

import net.mine_diver.developermode.DeveloperMode;
import net.mine_diver.developermode.feature.Message;
import net.mine_diver.developermode.feature.world.transform.Stack;
import net.mine_diver.developermode.feature.world.transform.Substitution;
import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.modificationstation.stationapi.api.StationAPI;
import net.modificationstation.stationapi.api.event.world.gen.WorldGenEvent;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSource;
import net.modificationstation.stationapi.impl.world.chunk.FlattenedChunk;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

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
 *
 * <p>It can carry a stack of transforms laid over the generator's noise. At
 * the Noise step that is the whole of it; at the Terrain step the transformed
 * noise stands in for the generator's own while it builds each chunk, so the
 * surface and the caves are made on top of the transformed shape. A transform
 * can look for its value sideways, so the noise is worked out a little past
 * the edges too, and kept: a preview built again over the same ground with
 * different settings reuses it and only lays the transforms over again.
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
        TERRAIN("gui.developermode.preview.step.terrain"),
        /**
         * Everything, decoration included, decorated in a world of the
         * preview's own and drawn with the blocks' real textures. The slowest
         * by far.
         */
        MATERIALIZED("gui.developermode.preview.step.materialized");

        public final String key;

        Step(String key) {
            this.key = key;
        }
    }

    private final World world;
    private final ChunkSource generator;
    private final Step step;
    private final Stack stack;
    /** The generator's own noise by chunk, inside the preview and past its edges, shared with a rebuild. */
    private final Map<Long, Noise.Field> raw;
    private TransformedNoise transformed;
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

    /**
     * The world a Materialized preview decorates in, holding a ring of chunks
     * one wider than the preview, since decorating a chunk writes into the
     * ones beside it. Null at any other step.
     */
    private final PreviewWorld decorated;
    /** The ring's chunks in the order they are made, nearest the middle first, as positions across it. */
    private final int[] ring;
    /** The chunks decorated, one fewer across than the ring and short of it on the far side. */
    private final int[] decorating;
    private int generated;
    private int decoratedCount;

    private Preview(World world, PreviewWorld decorated, ChunkSource generator, Step step, Stack stack,
                    Map<Long, Noise.Field> raw, int centerX, int centerZ, int radius) {
        this.world = world;
        this.decorated = decorated;
        this.generator = generator;
        this.step = step;
        this.stack = stack;
        this.raw = raw;
        this.across = radius * 2 + 1;
        this.fromX = centerX - radius;
        this.fromZ = centerZ - radius;
        this.chunks = new FlattenedChunk[across * across];
        this.noise = step == Step.NOISE ? new Noise.Field[chunks.length] : null;

        this.order = centerOut(across, radius);
        this.ring = decorated == null ? new int[0] : centerOut(across + 2, radius + 1);
        this.decorating = decorated == null ? new int[0] : centerOut(across + 1, radius + 1);
    }

    /** The positions of a square, nearest a point first, as {@code x + z * side}. */
    private static int[] centerOut(int side, int center) {
        Integer[] byDistance = new Integer[side * side];
        for (int i = 0; i < byDistance.length; i++) byDistance[i] = i;
        Arrays.sort(byDistance, Comparator.comparingInt(i -> {
            int dx = i % side - center;
            int dz = i / side - center;
            return dx * dx + dz * dz;
        }));
        return Arrays.stream(byDistance).mapToInt(Integer::intValue).toArray();
    }

    /**
     * A preview of the square of chunks around one, not generated yet.
     *
     * @param radius   how far out from that chunk, in chunks
     * @param stack    the transforms to lay over the noise, copied so tuning
     *                 them further does not change this one
     * @param previous a preview of the same world whose noise can be reused,
     *                 or null to work it all out afresh, as after the
     *                 generator's code has changed
     */
    public static Preview around(World world, Step step, Stack stack, Preview previous, int chunkX, int chunkZ, int radius) {
        Map<Long, Noise.Field> raw = previous != null && previous.world == world ? previous.raw : new HashMap<>();
        // Decorating writes through the generator's own world, so a
        // Materialized preview's generator is made for a world of its own.
        PreviewWorld decorated = step == Step.MATERIALIZED ? new PreviewWorld(world) : null;
        ChunkSource generator = (decorated == null ? world : decorated).dimension.createChunkGenerator();
        return new Preview(world, decorated, generator, step, stack.copy(), raw, chunkX, chunkZ, radius);
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
        if (step == Step.MATERIALIZED) return materialize(until);
        while (!done()) {
            int index = order[made];
            int x = fromX + index % across;
            int z = fromZ + index / across;
            try {
                boolean transforming = stack.active();
                if ((step == Step.NOISE || transforming) && !Noise.knows(generator)) {
                    failure = Message.of(step == Step.NOISE ? "message.developermode.no_noise" : "message.developermode.no_transforms");
                    return true;
                }
                if (step == Step.NOISE) {
                    Noise.Field field = transforming ? transformed(x, z) : raw(x, z);
                    noise[index] = field;
                    chunks[index] = solidOf(field, x, z);
                } else {
                    FlattenedChunk chunk = terrain(x, z);
                    if (chunk == null) return true;
                    chunks[index] = chunk;
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
     * A chunk as getChunk hands it back, with the transformed noise standing
     * in for the generator's when there are transforms.
     *
     * @return null once something has failed, which is then said in {@link #failure}
     */
    private FlattenedChunk terrain(int x, int z) {
        Noise.Field field = stack.active() ? transformed(x, z) : null;
        Chunk chunk = field == null
                ? generator.getChunk(x, z)
                : Substitution.during(generator, field, () -> generator.getChunk(x, z));
        if (chunk instanceof FlattenedChunk flattened) return flattened;
        failure = Message.of("message.developermode.cannot_preview");
        return null;
    }

    /**
     * The Materialized step: the whole ring's terrain first, then the
     * decoration, each nearest the middle first, and the preview's chunks
     * handed over only once every one of them has all the decoration that
     * reaches into it.
     */
    private boolean materialize(long until) {
        while (!done()) {
            int x = 0;
            int z = 0;
            try {
                if (stack.active() && !Noise.knows(generator)) {
                    failure = Message.of("message.developermode.no_transforms");
                    return true;
                }
                if (generated < ring.length) {
                    x = fromX - 1 + ring[generated] % (across + 2);
                    z = fromZ - 1 + ring[generated] / (across + 2);
                    FlattenedChunk chunk = terrain(x, z);
                    if (chunk == null) return true;
                    decorated.put(chunk);
                    int areaX = x - fromX;
                    int areaZ = z - fromZ;
                    if (areaX >= 0 && areaZ >= 0 && areaX < across && areaZ < across) chunks[areaZ * across + areaX] = chunk;
                    generated++;
                } else if (decoratedCount < decorating.length) {
                    x = fromX - 1 + decorating[decoratedCount] % (across + 1);
                    z = fromZ - 1 + decorating[decoratedCount] / (across + 1);
                    decorate(x, z);
                    decoratedCount++;
                } else {
                    made = chunks.length;
                }
            } catch (RuntimeException | LinkageError error) {
                boolean decorating = generated == ring.length;
                DeveloperMode.LOGGER.error("{} preview chunk {}, {} threw", decorating ? "Decorating" : "Generating", x, z, error);
                failure = Message.of(decorating ? "message.developermode.preview_decoration_threw" : "message.developermode.preview_threw",
                        error.getClass().getSimpleName(), x, z);
                return true;
            }
            if (System.nanoTime() >= until) break;
        }
        return done();
    }

    /**
     * Decorates a chunk of the preview's world, and then lets every mod
     * decorate it too, with the event StationAPI posts after decorating a
     * chunk of a real world, seeded the same way.
     */
    private void decorate(int chunkX, int chunkZ) {
        decorated.getChunk(chunkX, chunkZ).terrainPopulated = true;
        generator.decorate(decorated.getChunkSource(), chunkX, chunkZ);

        int blockX = chunkX * 16;
        int blockZ = chunkZ * 16;
        long seed = decorated.getSeed();
        Random random = new Random(seed);
        long xMultiplier = random.nextLong() / 2L * 2L + 1L;
        long zMultiplier = random.nextLong() / 2L * 2L + 1L;
        random.setSeed((long) chunkX * xMultiplier + (long) chunkZ * zMultiplier ^ seed);
        StationAPI.EVENT_BUS.post(WorldGenEvent.ChunkDecoration.builder()
                .world(decorated)
                .worldSource(generator)
                .biome(decorated.method_1781().getBiome(blockX + 16, blockZ + 16))
                .x(blockX)
                .z(blockZ)
                .random(random)
                .build());
    }

    /**
     * Where the Materialized step has got to, as a fraction of each of its
     * two parts, or null at any other step.
     */
    public Message materializing() {
        if (decorated == null || done()) return null;
        return generated < ring.length
                ? Message.of("gui.developermode.preview.generating", generated, ring.length)
                : Message.of("gui.developermode.preview.decorating", decoratedCount, decorating.length);
    }

    /** The world a Materialized preview's chunks live in, or null at any other step. */
    public PreviewWorld decorated() {
        return decorated;
    }

    private Noise.Field raw(int chunkX, int chunkZ) {
        return noise().raw(chunkX, chunkZ);
    }

    private Noise.Field transformed(int chunkX, int chunkZ) {
        return noise().transformed(chunkX, chunkZ);
    }

    private TransformedNoise noise() {
        // Through the generator's own world, whose biomes are the ones it
        // reads while it builds the noise.
        if (transformed == null) transformed = new TransformedNoise(generator, decorated == null ? world : decorated, stack, raw);
        return transformed;
    }

    public Stack stack() {
        return stack;
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
