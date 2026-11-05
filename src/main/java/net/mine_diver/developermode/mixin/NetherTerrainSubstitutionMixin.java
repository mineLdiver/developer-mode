package net.mine_diver.developermode.mixin;

import net.mine_diver.developermode.feature.world.Noise;
import net.mine_diver.developermode.feature.world.transform.Substitution;
import net.minecraft.block.Block;
import net.minecraft.world.gen.chunk.NetherChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Nether's half of {@link TerrainSubstitutionMixin}, apart only because
 * its terrain is built without biomes.
 */
@Mixin(NetherChunkGenerator.class)
class NetherTerrainSubstitutionMixin {
    /** The Nether's lava sea, in layers from the bottom. */
    private static final int LAVA_LEVEL = 32;

    @Inject(method = "buildTerrain", at = @At("RETURN"))
    private void developermode_substitute(int chunkX, int chunkZ, byte[] blocks, CallbackInfo ci) {
        Noise.Field field = Substitution.fieldFor(this);
        if (field != null) Substitution.fill(blocks, field, Block.NETHERRACK, Block.LAVA, LAVA_LEVEL);
    }
}
