package net.mine_diver.developermode.feature.world;

import net.mine_diver.developermode.mixin.ServerChunkCacheAccessor;
import net.minecraft.world.chunk.ChunkSource;

/**
 * The generator behind a server's chunk cache.
 *
 * <p>Loaded only from {@link Regenerate}, and only on the side that has a
 * server chunk cache to ask.
 */
final class ServerGenerator {
    private ServerGenerator() {}

    /** @return the generator, or null when the cache is not a server's */
    static ChunkSource of(ChunkSource cache) {
        return cache instanceof ServerChunkCacheAccessor server ? server.getGenerator() : null;
    }
}
