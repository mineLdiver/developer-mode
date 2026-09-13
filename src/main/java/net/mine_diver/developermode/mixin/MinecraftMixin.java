package net.mine_diver.developermode.mixin;

import net.mine_diver.developermode.client.inspect.InspectMode;
import net.mine_diver.developermode.client.summon.SummonMode;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps clicks made while inspecting away from the world.
 *
 * <p>Without this, picking a mob would also swing at it, and holding the button
 * would start mining whatever is behind it.
 */
@Mixin(Minecraft.class)
class MinecraftMixin {
    @Inject(method = "handleMouseClick(I)V", at = @At("HEAD"), cancellable = true)
    private void developermode_inspectClick(int button, CallbackInfo ci) {
        // Summon mode does its own edge detection, since this handler repeats
        // while the button is held. It only needs the click kept off the world.
        if (SummonMode.isActive() || InspectMode.click(button)) ci.cancel();
    }

    @Inject(method = "handleMouseDown(IZ)V", at = @At("HEAD"), cancellable = true)
    private void developermode_inspectHold(int button, boolean holdingAttack, CallbackInfo ci) {
        if (InspectMode.isActive() || SummonMode.isActive()) ci.cancel();
    }
}
