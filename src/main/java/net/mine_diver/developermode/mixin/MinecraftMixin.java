package net.mine_diver.developermode.mixin;

import net.mine_diver.developermode.client.summon.SummonMode;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps clicks made while placing entities away from the world.
 *
 * <p>Without this, putting a mob down would also swing at whatever is there,
 * and holding the button would start mining it.
 */
@Mixin(Minecraft.class)
class MinecraftMixin {
    @Inject(method = "handleMouseClick(I)V", at = @At("HEAD"), cancellable = true)
    private void developermode_inspectClick(int button, CallbackInfo ci) {
        // Summon mode does its own edge detection, since this handler repeats
        // while the button is held. It only needs the click kept off the world.
        if (SummonMode.isActive()) ci.cancel();
    }

    @Inject(method = "handleMouseDown(IZ)V", at = @At("HEAD"), cancellable = true)
    private void developermode_inspectHold(int button, boolean holdingAttack, CallbackInfo ci) {
        if (SummonMode.isActive()) ci.cancel();
    }
}
