package net.mine_diver.developermode.mixin;

import net.mine_diver.developermode.feature.entity.FrozenEntities;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.PlayerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets go of everything a departing player was holding still.
 *
 * <p>A freeze is only safe because whoever asked for it can see it on their
 * HUD and release it. Once they are gone it is neither, so it outlives them by
 * no longer than this.
 */
@Mixin(PlayerManager.class)
class PlayerManagerMixin {
    @Inject(method = "disconnect(Lnet/minecraft/entity/player/ServerPlayerEntity;)V", at = @At("HEAD"))
    private void developermode_releaseFreezes(ServerPlayerEntity player, CallbackInfo ci) {
        FrozenEntities.releaseAll(player);
    }
}
