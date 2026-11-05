package net.mine_diver.developermode.mixin;

import net.minecraft.world.chunk.ChunkCache;
import net.minecraft.world.chunk.ChunkSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The generator behind a local world's chunks.
 *
 * <p>A world built without a server keeps its chunks in this cache, and the
 * cache keeps the one generator its chunks came from. Regenerating asks that
 * same instance rather than a new one from the dimension, so the chunks that
 * come back are made by whatever that generator is now, edits hotswapped into
 * it included.
 */
@Mixin(ChunkCache.class)
public interface ChunkCacheAccessor {
    @Accessor("generator")
    ChunkSource getGenerator();
}
