package net.mine_diver.developermode.feature.world;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSource;
import net.modificationstation.stationapi.api.block.BlockState;
import net.modificationstation.stationapi.impl.world.chunk.ChunkSection;
import net.modificationstation.stationapi.impl.world.chunk.FlattenedChunk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * What a stretch of the world is made of, block state by block state: how
 * many there are to a chunk, how they spread over height, and how they clump
 * into veins.
 *
 * <p>For judging a generator by numbers rather than by eye. A count means
 * most next to another, so a census is usually read beside a vanilla block
 * counted in the same chunks, or beside an earlier census of the same place.
 *
 * <p>Only finished chunks count. In a world kept on this side, a chunk still
 * waiting on its neighbors to be decorated has no ores yet and would drag
 * every average down, so it is left out. A client of a server cannot tell,
 * since every chunk it is sent looks undecorated to it, and counts all of
 * them.
 *
 * <p>An area is tens of millions of blocks, so it is counted a slice at a
 * time with {@link #step}, each slice stopping once its share of a frame is
 * spent. The chunks are the ones that were there when it started.
 *
 * <p>Air is not counted. Everything else is, one tally per block state, which
 * is what StationAPI tells variants apart by. Beta's metadata is left out:
 * leaves and logs spend it on things like decay, which is not what a block
 * is.
 */
public final class Census {
    /**
     * How many of one block are kept by position for grouping into veins.
     * Past this a block is ground rather than a deposit, and grouping it would
     * cost more than it could say.
     */
    private static final int VEIN_LIMIT = 1 << 16;

    private final List<FlattenedChunk> chunks;
    private final int bottomY;
    private final int height;
    private final Map<BlockState, Tally> tallies = new IdentityHashMap<>();

    private int scanned;
    private Iterator<Tally> grouping;
    private boolean done;

    private Census(List<FlattenedChunk> chunks, int bottomY, int height) {
        this.chunks = chunks;
        this.bottomY = bottomY;
        this.height = height;
    }

    /**
     * A census of the finished chunks in the square around one, not yet
     * counted.
     *
     * @param radius how far out from that chunk, in chunks
     */
    public static Census around(World world, int chunkX, int chunkZ, int radius) {
        ChunkSource source = world.getChunkSource();
        List<FlattenedChunk> found = new ArrayList<>();
        for (int z = chunkZ - radius; z <= chunkZ + radius; z++) {
            for (int x = chunkX - radius; x <= chunkX + radius; x++) {
                // Asked first so a chunk that is not there is not loaded or
                // generated to answer, which on a client of a server is
                // every chunk, and an empty one comes back instead.
                if (!source.isChunkLoaded(x, z)) continue;
                Chunk chunk = source.getChunk(x, z);
                if (chunk.isEmpty() || !(chunk instanceof FlattenedChunk flattened)) continue;
                if (!world.isRemote && !chunk.terrainPopulated) continue;
                found.add(flattened);
            }
        }
        return new Census(found, world.getBottomY(), world.getHeight());
    }

    /**
     * Counts for about as long as it is given, and always at least one chunk
     * or one block's veins.
     *
     * @return whether it is finished
     */
    public boolean step(long budgetNanos) {
        long until = System.nanoTime() + budgetNanos;
        do {
            if (done) return true;
            if (scanned < chunks.size()) {
                scan(chunks.get(scanned), scanned);
                scanned++;
            } else if (grouping == null) {
                for (Tally tally : tallies.values()) tally.closeChunk();
                grouping = new ArrayList<>(tallies.values()).iterator();
            } else if (grouping.hasNext()) {
                grouping.next().groupVeins();
            } else {
                done = true;
            }
        } while (System.nanoTime() < until);
        return done;
    }

    public boolean done() {
        return done;
    }

    /** Whether it is still counting blocks, rather than grouping them or finished. */
    public boolean counting() {
        return scanned < chunks.size();
    }

    /** How many chunks it counts, all of them finished or not. */
    public int chunks() {
        return chunks.size();
    }

    /** How many chunks it has counted so far. */
    public int scanned() {
        return scanned;
    }

    public int bottomY() {
        return bottomY;
    }

    public int height() {
        return height;
    }

    /** Every block state found so far. */
    public Collection<Tally> tallies() {
        return Collections.unmodifiableCollection(tallies.values());
    }

    /** @return the tally for a block state, or null if none has turned up */
    public Tally tally(BlockState state) {
        return tallies.get(state);
    }

    private void scan(FlattenedChunk chunk, int index) {
        int originX = chunk.x << 4;
        int originZ = chunk.z << 4;
        // Most blocks are the same as the one before, and asking the map
        // again for stone tens of thousands of times would be most of the work.
        BlockState lastState = null;
        Tally last = null;

        for (ChunkSection section : chunk.sections) {
            if (section == null || section.isEmpty()) continue;
            int sectionY = section.getYOffset();
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state != lastState) {
                            lastState = state;
                            last = state.isAir() ? null : tallies.computeIfAbsent(state, Tally::new);
                        }
                        if (last != null) last.add(originX + x, sectionY + y, originZ + z, index);
                    }
                }
            }
        }
    }

    /** One block state's share of a census. */
    public final class Tally {
        private final BlockState state;
        private final int[] heights = new int[height];

        private long count;
        private int chunksWithAny;
        private int mostInAChunk;
        private int chunk = -1;
        private int inChunk;

        /** Packed positions, kept until there are too many to group or they have been grouped. */
        private long[] positions = new long[16];
        private int positionCount;
        private boolean tooCommon;

        private int veins;
        private int largestVein;
        private int medianVein;

        private Tally(BlockState state) {
            this.state = state;
        }

        public BlockState state() {
            return state;
        }

        public long count() {
            return count;
        }

        /** The average over every chunk counted, including those without any. */
        public double perChunk() {
            return scanned == 0 ? 0 : count / (double) Math.min(scanned, chunks.size());
        }

        /** How many of the chunks counted have at least one. */
        public int chunksWithAny() {
            return chunksWithAny;
        }

        public int mostInAChunk() {
            return Math.max(mostInAChunk, inChunk);
        }

        /** How many there are at a height, which is 0 outside the world. */
        public int at(int y) {
            int index = y - bottomY;
            return index < 0 || index >= heights.length ? 0 : heights[index];
        }

        public int lowest() {
            for (int i = 0; i < heights.length; i++) if (heights[i] > 0) return bottomY + i;
            return bottomY;
        }

        public int highest() {
            for (int i = heights.length - 1; i >= 0; i--) if (heights[i] > 0) return bottomY + i;
            return bottomY;
        }

        /** The height there are most of, the lowest one on a tie. */
        public int peak() {
            int best = 0;
            for (int i = 1; i < heights.length; i++) if (heights[i] > heights[best]) best = i;
            return bottomY + best;
        }

        /**
         * Whether its veins have been counted. They are not for a block too
         * common to clump, nor before the census has got to it.
         */
        public boolean hasVeins() {
            return veins > 0;
        }

        /** Whether there are too many to say anything about veins. */
        public boolean tooCommon() {
            return tooCommon;
        }

        /**
         * How many separate clumps it comes in. Blocks touching at a face,
         * an edge or a corner are one clump, since ore is placed as a blob
         * whose blocks often meet only diagonally. A vein cut by the edge of
         * the area counts as the part inside it.
         */
        public int veins() {
            return veins;
        }

        public double meanVein() {
            return veins == 0 ? 0 : count / (double) veins;
        }

        public int medianVein() {
            return medianVein;
        }

        public int largestVein() {
            return largestVein;
        }

        private void add(int x, int y, int z, int chunkIndex) {
            if (chunkIndex != chunk) {
                closeChunk();
                chunk = chunkIndex;
                chunksWithAny++;
            }
            inChunk++;
            count++;
            heights[y - bottomY]++;

            if (tooCommon) return;
            if (positionCount == VEIN_LIMIT) {
                tooCommon = true;
                positions = null;
                return;
            }
            if (positionCount == positions.length) positions = Arrays.copyOf(positions, positions.length * 2);
            positions[positionCount++] = pack(x, y - bottomY, z);
        }

        private void closeChunk() {
            mostInAChunk = Math.max(mostInAChunk, inChunk);
            inChunk = 0;
        }

        private void groupVeins() {
            if (positions == null) return;

            LongOpenHashSet left = new LongOpenHashSet(positions, 0, positionCount);
            int[] sizes = new int[positionCount];
            long[] stack = new long[64];
            for (int i = 0; i < positionCount; i++) {
                if (!left.remove(positions[i])) continue;

                int size = 0;
                int depth = 0;
                stack[depth++] = positions[i];
                while (depth > 0) {
                    long at = stack[--depth];
                    size++;
                    int x = (int) (at >> 38);
                    int y = (int) (at >> 26 & 0xFFF);
                    int z = (int) (at << 38 >> 38);
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            for (int dx = -1; dx <= 1; dx++) {
                                long next = pack(x + dx, y + dy, z + dz);
                                if (!left.remove(next)) continue;
                                if (depth == stack.length) stack = Arrays.copyOf(stack, depth * 2);
                                stack[depth++] = next;
                            }
                        }
                    }
                }
                sizes[veins++] = size;
                largestVein = Math.max(largestVein, size);
            }

            Arrays.sort(sizes, 0, veins);
            medianVein = veins == 0 ? 0 : sizes[veins / 2];
            positions = null;
        }
    }

    /** A position as one number, with y counted up from the bottom of the world. */
    private static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) y & 0xFFF) << 26 | (long) z & 0x3FFFFFF;
    }
}
