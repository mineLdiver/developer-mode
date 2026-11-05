package net.mine_diver.developermode.feature.world;

import net.minecraft.client.gui.screen.LoadingDisplay;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.dimension.Dimension;
import net.minecraft.world.storage.EmptyWorldStorage;
import net.modificationstation.stationapi.api.block.BlockState;
import net.modificationstation.stationapi.api.block.States;

import java.util.HashMap;
import java.util.Map;

/**
 * A world of its own for a preview to decorate, holding only the preview's
 * chunks and keeping none of them.
 *
 * <p>Decoration is written through a world, a block at a time, so decorating
 * off to the side needs a world that is off to the side. This one is made the
 * way Beta makes the world a multiplayer client sees, with storage that saves
 * nothing, and with the live world's seed and dimension, so a generator made
 * for it decorates exactly as it would the real thing. Nothing ticks it: no
 * sand falls, no water flows, no mob moves.
 *
 * <p>It is lit evenly instead of worked out, since it is only ever looked at,
 * and the question is the blocks rather than the shadows. And while its
 * faces are being worked out it can hide everything above a height and
 * everything outside the preview, so a cut-away shows the faces the cut
 * leaves, and the edges show the layers under the surface, rather than both
 * being hidden by the blocks beyond them.
 */
public final class PreviewWorld extends World {
    private static final int FULL_BRIGHT = 15;

    /** Blocks above this read as air. Only lowered while faces are worked out. */
    private int cut = Integer.MAX_VALUE;
    /** Blocks outside this square read as air, under the same terms. */
    private int minX = Integer.MIN_VALUE;
    private int minZ = Integer.MIN_VALUE;
    private int maxX = Integer.MAX_VALUE;
    private int maxZ = Integer.MAX_VALUE;

    public PreviewWorld(World world) {
        super(new EmptyWorldStorage(), "developermode preview", Dimension.fromId(world.dimension.id), world.getSeed());
    }

    @Override
    protected ChunkSource createChunkCache() {
        // Called from World's own constructor, before any field here is set.
        return new Chunks(this);
    }

    /** Hands a chunk to this world. */
    public void put(Chunk chunk) {
        ((Chunks) chunkSource).put(chunk);
    }

    /**
     * Runs something with every block above a height, and every block
     * outside a square, reading as air.
     */
    public void showing(int cut, int minX, int minZ, int maxX, int maxZ, Runnable action) {
        this.cut = cut;
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        try {
            action.run();
        } finally {
            this.cut = Integer.MAX_VALUE;
            this.minX = this.minZ = Integer.MIN_VALUE;
            this.maxX = this.maxZ = Integer.MAX_VALUE;
        }
    }

    private boolean hidden(int x, int y, int z) {
        return y > cut || x < minX || z < minZ || x > maxX || z > maxZ;
    }

    @Override
    public int getBlockId(int x, int y, int z) {
        return hidden(x, y, z) ? 0 : super.getBlockId(x, y, z);
    }

    @Override
    public BlockState getBlockState(int x, int y, int z) {
        return hidden(x, y, z) ? States.AIR.get() : super.getBlockState(x, y, z);
    }

    @Override
    public int getLightLevel(int x, int y, int z) {
        return FULL_BRIGHT;
    }

    @Override
    public int getLightLevel(int x, int y, int z, boolean bl) {
        return FULL_BRIGHT;
    }

    @Override
    public int getBrightness(LightType type, int x, int y, int z) {
        return FULL_BRIGHT;
    }

    @Override
    public void queueLightUpdate(LightType type, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean bl) {
        // Lit evenly, so there is nothing to work out.
    }

    /** The preview's chunks by position. Anything else is an empty chunk, and nothing is ever loaded or saved. */
    private static final class Chunks implements ChunkSource {
        private final Map<Long, Chunk> chunks = new HashMap<>();
        private final Chunk empty;

        Chunks(World world) {
            this.empty = new EmptyChunk(world, 0, 0);
        }

        void put(Chunk chunk) {
            chunks.put(key(chunk.x, chunk.z), chunk);
        }

        @Override
        public boolean isChunkLoaded(int x, int z) {
            return chunks.containsKey(key(x, z));
        }

        @Override
        public Chunk getChunk(int chunkX, int chunkZ) {
            return chunks.getOrDefault(key(chunkX, chunkZ), empty);
        }

        @Override
        public Chunk loadChunk(int chunkX, int chunkZ) {
            return getChunk(chunkX, chunkZ);
        }

        @Override
        public void decorate(ChunkSource source, int x, int z) {
            // A preview decorates its chunks itself, in its own order.
        }

        @Override
        public boolean save(boolean saveEntities, LoadingDisplay display) {
            return true;
        }

        @Override
        public boolean tick() {
            return false;
        }

        @Override
        public boolean canSave() {
            return false;
        }

        @Override
        public String getDebugInfo() {
            return "Preview: " + chunks.size();
        }

        private static long key(int x, int z) {
            return (long) x << 32 | z & 0xFFFFFFFFL;
        }
    }
}
