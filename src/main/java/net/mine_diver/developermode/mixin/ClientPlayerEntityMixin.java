package net.mine_diver.developermode.mixin;

import net.mine_diver.developermode.feature.player.Power;
import net.mine_diver.developermode.feature.player.Powers;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops the world shoving a noclipping player back out of it.
 *
 * <p>Each tick, the client tries all four corners of the player against the
 * blocks around them and nudges them clear of anything they are standing
 * inside. That is what keeps somebody who spawned in a wall from being stuck,
 * and it is the opposite of what noclip is for: without this, standing inside
 * a block is a shove towards the nearest opening rather than a place to be.
 */
@Mixin(ClientPlayerEntity.class)
class ClientPlayerEntityMixin {
    @Inject(method = "pushOutOfBlock(DDD)Z", at = @At("HEAD"), cancellable = true)
    private void developermode_stayWhereYouAre(double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        // False is what this says when it found nothing to push out of.
        if (Powers.has((PlayerEntity) (Object) this, Power.NOCLIP)) cir.setReturnValue(false);
    }
}
