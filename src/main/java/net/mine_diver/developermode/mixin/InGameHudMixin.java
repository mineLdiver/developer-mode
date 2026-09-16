package net.mine_diver.developermode.mixin;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.DeveloperUi;
import net.mine_diver.developermode.client.inspect.InspectRenderer;
import net.mine_diver.developermode.client.summon.SummonRenderer;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the inspect readout on top of the normal HUD.
 */
@Mixin(InGameHud.class)
class InGameHudMixin {
    @Inject(method = "render(FZII)V", at = @At("RETURN"))
    private void developermode_renderInspectHud(float tickDelta, boolean screenOpen, int mouseX, int mouseY, CallbackInfo ci) {
        InspectRenderer.renderHud(DeveloperModeClient.minecraft());
        SummonRenderer.renderHud(DeveloperModeClient.minecraft());
        DeveloperUi.renderHeldIndicator(DeveloperModeClient.minecraft());
    }
}
