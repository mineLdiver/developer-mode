package net.mine_diver.developermode.mixin;

import net.mine_diver.developermode.client.DeveloperUi;
import net.mine_diver.developermode.client.inspect.InspectMode;
import net.mine_diver.developermode.client.inspect.InspectRenderer;
import net.mine_diver.developermode.client.summon.SummonMode;
import net.mine_diver.developermode.client.summon.SummonRenderer;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hangs inspect mode off the world render.
 *
 * <p>The snow call is the anchor: it happens exactly once per eye, after the
 * terrain, entities and block outline, with the camera matrices still set and
 * lighting already off, which is precisely the state wireframes want.
 */
@Mixin(GameRenderer.class)
class GameRendererMixin {
    @Inject(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/GameRenderer;renderSnow(F)V"
            )
    )
    private void developermode_renderInspectOverlay(float tickDelta, long time, CallbackInfo ci) {
        DeveloperUi.releaseUnusedFreezes();
        InspectMode.update();
        SummonMode.update();
        InspectRenderer.renderWorld(tickDelta);
        SummonRenderer.renderWorld(tickDelta);
    }
}
