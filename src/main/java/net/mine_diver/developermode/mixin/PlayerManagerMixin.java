package net.mine_diver.developermode.mixin;

import net.mine_diver.developermode.feature.entity.FrozenEntities;
import net.mine_diver.developermode.feature.world.Locks;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.PlayerManager;
import net.minecraft.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where the server tells a player about the world they are in, and where it
 * lets them go.
 *
 * <p>Arriving in a world, by joining or by respawning into it, is when a
 * player is told its time and weather, so it is also when they are told what
 * of those is locked.
 *
 * <p>Leaving lets go of everything the player was holding still. A freeze is
 * only safe because whoever asked for it can see it on their HUD and release
 * it. Once they are gone it is neither, so it outlives them by no longer than
 * this.
 */
@Mixin(PlayerManager.class)
class PlayerManagerMixin {
    @Inject(method = "disconnect(Lnet/minecraft/entity/player/ServerPlayerEntity;)V", at = @At("HEAD"))
    private void developermode_releaseFreezes(ServerPlayerEntity player, CallbackInfo ci) {
        FrozenEntities.releaseAll(player);
    }

    @Inject(method = "sendWorldInfo(Lnet/minecraft/entity/player/ServerPlayerEntity;Lnet/minecraft/world/ServerWorld;)V", at = @At("TAIL"))
    private void developermode_tellLocks(ServerPlayerEntity player, ServerWorld world, CallbackInfo ci) {
        Locks.tell(player, world);
    }
}
