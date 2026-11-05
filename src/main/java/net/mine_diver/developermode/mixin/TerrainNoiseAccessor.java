package net.mine_diver.developermode.mixin;

import net.minecraft.world.gen.chunk.NetherChunkGenerator;
import net.minecraft.world.gen.chunk.OverworldChunkGenerator;
import net.minecraft.world.gen.chunk.SkyChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The coarse grid of noise a known generator shapes its terrain from.
 *
 * <p>Each one keeps the grid it last built in this field, after everything
 * that reworks it on the way has had its turn, StationAPI's biome heights and
 * taller worlds included. Read right after building a chunk's terrain, it is
 * exactly what that chunk was shaped from.
 */
@Mixin({ OverworldChunkGenerator.class, SkyChunkGenerator.class, NetherChunkGenerator.class })
public interface TerrainNoiseAccessor {
    @Accessor("heightMap")
    double[] getHeightMap();
}
