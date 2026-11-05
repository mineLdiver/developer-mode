package net.mine_diver.developermode.feature.world;

import net.mine_diver.developermode.DeveloperMode;
import net.mine_diver.developermode.feature.Message;
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
 * <p>This is the Terrain step: the blocks a generator's {@code getChunk} hands
 * back, which is the shape, the surface and the caves, with no decoration.
 */
public final class Preview {
    private final World world;
    private final ChunkSource generator;
    private final int fromX;
    private final int fromZ;
    private final int across;
    private final FlattenedChunk[] chunks;
    /** Indices into {@link #chunks}, nearest the middle first. */
    private final int[] order;

    private int made;
    private Message failure;

    private Preview(World world, ChunkSource generator, int centerX, int centerZ, int radius) {
        this.world = world;
        this.generator = generator;
        this.across = radius * 2 + 1;
        this.fromX = centerX - radius;
        this.fromZ = centerZ - radius;
        this.chunks = new FlattenedChunk[across * across];

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
    public static Preview around(World world, int chunkX, int chunkZ, int radius) {
        return new Preview(world, world.dimension.createChunkGenerator(), chunkX, chunkZ, radius);
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
                Chunk chunk = generator.getChunk(x, z);
                if (!(chunk instanceof FlattenedChunk flattened)) {
                    failure = Message.of("message.developermode.cannot_preview");
                    return true;
                }
                chunks[index] = flattened;
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
