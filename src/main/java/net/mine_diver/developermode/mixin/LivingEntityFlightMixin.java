package net.mine_diver.developermode.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.mine_diver.developermode.client.Flier;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * What flying changes about moving through the air, which is not much.
 *
 * <p>Beta's own movement already has everything flight needs once gravity is
 * out of the way: a push from the movement keys, a drag that settles it into
 * a top speed, and the bookkeeping that follows a move, from the swing of the
 * arms and legs to the distance flown in the statistics. So flying is that
 * movement, with the few differences below and nothing else replaced. What
 * the air does to anyone else, and whatever other mods have it do, it does to
 * a flier too.
 *
 * <p>The differences:
 *
 * <ul>
 *   <li>A push two and a half times the one the air gives a jumper, which
 *       against the air's own drag settles at half a block a tick.
 *   <li>Water, lava and ladders are not there. They are things that happen to
 *       somebody moving through the world, and a flier is moving over it: the
 *       drag of a fluid would make flying through a lake a swim, and a ladder
 *       would hold anyone who brushed past it.
 *   <li>The ground covered is not counted as walked. That count is what decides
 *       when the next footstep sounds and how far the view bobs. Over open air
 *       there is no block to make a sound and so nothing moves it along either,
 *       and the debt would be paid off one step a tick the moment there was
 *       ground below again.
 * </ul>
 *
 * <p>Gravity is left where it is. It is taken off after the move, and the
 * climbing speed set before the next one replaces it before it is ever used.
 *
 * <p>Every one of these is asked for every living thing every tick, so the
 * cheap half of the question comes first, and all but one player answer no.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityFlightMixin {
    /** Per tick push from the movement keys, in place of the air's 0.02. */
    @Unique
    private static final float FLIGHT_PUSH = 0.05F;

    @ModifyExpressionValue(
            method = "travel(FF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isSubmergedInWater()Z")
    )
    private boolean developermode_flyOverWater(boolean inWater) {
        return inWater && !developermode_flying();
    }

    @ModifyExpressionValue(
            method = "travel(FF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isTouchingLava()Z")
    )
    private boolean developermode_flyOverLava(boolean inLava) {
        return inLava && !developermode_flying();
    }

    @ModifyExpressionValue(
            method = "travel(FF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isOnLadder()Z")
    )
    private boolean developermode_flyPastLadders(boolean onLadder) {
        return onLadder && !developermode_flying();
    }

    @ModifyArg(
            method = "travel(FF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;moveNonSolid(FFF)V"),
            index = 2
    )
    private float developermode_pushHarder(float speed) {
        return developermode_flying() ? FLIGHT_PUSH : speed;
    }

    @WrapOperation(
            method = "travel(FF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;move(DDD)V")
    )
    private void developermode_leaveStepsUncounted(LivingEntity self, double dx, double dy, double dz,
                                                  Operation<Void> original) {
        if (!developermode_flying()) {
            original.call(self, dx, dy, dz);
            return;
        }
        float walked = self.horizontalSpeed;
        original.call(self, dx, dy, dz);
        self.horizontalSpeed = walked;
    }

    @Unique
    private boolean developermode_flying() {
        return (Object) this instanceof Flier flier && flier.developermode_flying();
    }
}
