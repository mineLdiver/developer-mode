package net.mine_diver.developermode.mixin;

import net.mine_diver.developermode.feature.player.Power;
import net.mine_diver.developermode.feature.player.Powers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The two ways the world gets at a player without ever calling it damage.
 *
 * <p>Falling out of the bottom of the world deletes the entity rather than
 * hurting it, and a player standing inside a block is smothered by a check
 * that runs before anything else in the tick. Both live on {@link Entity} and
 * neither is overridden for players, which is why they are answered here
 * rather than beside the rest of god mode.
 */
@Mixin(Entity.class)
class EntityMixin {
    @Inject(method = "tickInVoid()V", at = @At("HEAD"), cancellable = true)
    private void developermode_holdOutOfTheVoid(CallbackInfo ci) {
        if (developermode_granted(Power.GOD)) ci.cancel();
    }

    /**
     * Blocks cannot smother someone they are not stopping either. Noclip puts
     * you inside them as a matter of course, so this is what keeps it from
     * costing a heart a tick to be in one.
     */
    @Inject(method = "isInsideWall()Z", at = @At("HEAD"), cancellable = true)
    private void developermode_ignoreWalls(CallbackInfoReturnable<Boolean> cir) {
        if (developermode_granted(Power.NOCLIP)) cir.setReturnValue(false);
    }

    /**
     * Both of these run for every entity in the world, every tick, so the
     * cheap half of the question is asked first.
     */
    @Unique
    private boolean developermode_granted(int power) {
        return (Object) this instanceof PlayerEntity player && Powers.has(player, power);
    }
}
