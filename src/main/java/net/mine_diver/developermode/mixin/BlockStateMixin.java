package net.mine_diver.developermode.mixin;

import net.mine_diver.developermode.feature.player.Power;
import net.mine_diver.developermode.feature.player.Powers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.modificationstation.stationapi.api.block.AbstractBlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Taking a block out in one hit.
 *
 * <p>This one answer is the whole of it. Every way a block comes apart asks
 * how much of it a tick of mining takes off, and every one of them treats a
 * whole block's worth as already done: the click that starts the mining, the
 * tick that carries it on, and the server's own copy of both. Neither side has
 * to be told anything else, and the two cannot disagree, because they are
 * asking the same question of the same block about the same player.
 *
 * <p>The question is asked of the block's state rather than of the block.
 * Beta asks {@code Block.getHardness(PlayerEntity)}, but StationAPI's
 * flattening sends every one of those calls here instead, so that a block's
 * metadata gets a say in how hard it is. That makes this the only place all of
 * them actually pass through.
 *
 * <p>Answered before the block's hardness is looked at rather than after, so a
 * block that is never meant to come apart goes the same way as any other.
 * Bedrock is not a special case here because it is not one there either: it is
 * a block whose hardness says never, and this says now.
 *
 * <p>What it drops is left alone, and still wants the right tool. Breaking a
 * block quickly and earning what is inside it are different questions, and
 * only the first one was asked.
 */
@Mixin(AbstractBlockState.class)
class BlockStateMixin {
    @Inject(
            method = "calcBlockBreakingDelta(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)F",
            at = @At("HEAD"),
            cancellable = true
    )
    private void developermode_breakInOneHit(PlayerEntity player, BlockView world, BlockPos pos,
                                             CallbackInfoReturnable<Float> cir) {
        if (Powers.has(player, Power.INSTANT_BREAK)) cir.setReturnValue(1.0F);
    }
}
