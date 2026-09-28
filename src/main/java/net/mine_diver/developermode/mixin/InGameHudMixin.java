package net.mine_diver.developermode.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.DeveloperUi;
import net.mine_diver.developermode.client.summon.SummonRenderer;
import net.mine_diver.developermode.client.tool.ToolRenderer;
import net.mine_diver.developermode.feature.player.Power;
import net.mine_diver.developermode.feature.player.Powers;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws what the world modes have to say on top of the normal HUD, and takes
 * away what god mode makes meaningless.
 *
 * <p>Health, armor and air are all a count of how much more the world can do
 * to you, and under god mode it can do nothing, so they are not drawn. Beta
 * already has the question that decides it: the HUD asks the interaction
 * manager whether to draw them, and the creative mode Beta still carries from
 * before survival, unused, answers no. This answers the same way for god mode,
 * and only where the HUD asks.
 */
@Mixin(InGameHud.class)
class InGameHudMixin {
    @Inject(method = "render(FZII)V", at = @At("RETURN"))
    private void developermode_renderInspectHud(float tickDelta, boolean screenOpen, int mouseX, int mouseY, CallbackInfo ci) {
        SummonRenderer.renderHud(DeveloperModeClient.minecraft());
        ToolRenderer.renderHud(DeveloperModeClient.minecraft());
        DeveloperUi.renderHeldIndicator(DeveloperModeClient.minecraft());
    }

    @ModifyExpressionValue(
            method = "render(FZII)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/InteractionManager;canBeRendered()Z")
    )
    private boolean developermode_hideStatusInGodMode(boolean statusBars) {
        return statusBars && !Powers.has(DeveloperModeClient.minecraft().player, Power.GOD);
    }
}
