package net.mine_diver.developermode.mixin;

import net.minecraft.server.world.chunk.ServerChunkCache;
import net.minecraft.world.chunk.ChunkSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The generator behind a server world's chunks, for the same reason
 * {@link ChunkCacheAccessor} gives for a local one.
 */
@Mixin(ServerChunkCache.class)
public interface ServerChunkCacheAccessor {
    @Accessor("generator")
    ChunkSource getGenerator();
}
