package net.mine_diver.developermode.mixin;

import net.mine_diver.developermode.feature.world.Noise;
import net.mine_diver.developermode.feature.world.transform.Substitution;
import net.minecraft.block.Block;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.chunk.OverworldChunkGenerator;
import net.minecraft.world.gen.chunk.SkyChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a preview's transformed noise stand in for the overworld's or the Sky
 * dimension's own, after they have filled a chunk's shape and before they lay
 * its surface. Does nothing for any generator but a preview's own.
 */
@Mixin({ OverworldChunkGenerator.class, SkyChunkGenerator.class })
class TerrainSubstitutionMixin {
    /** The overworld's sea level, in layers from the bottom of the array. */
    private static final int SEA_LEVEL = 64;

    @Inject(method = "buildTerrain", at = @At("RETURN"))
    private void developermode_substitute(int chunkX, int chunkZ, byte[] blocks, Biome[] biomes, double[] temperatures,
                                          CallbackInfo ci) {
        Noise.Field field = Substitution.fieldFor(this);
        if (field == null) return;
        boolean overworld = (Object) this instanceof OverworldChunkGenerator;
        Substitution.fill(blocks, field, Block.STONE, overworld ? Block.WATER : null, overworld ? SEA_LEVEL - field.bottomY() : 0);
    }
}
