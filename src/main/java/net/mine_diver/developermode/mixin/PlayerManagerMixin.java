package net.mine_diver.developermode.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.mine_diver.developermode.feature.entity.FrozenEntities;
import net.mine_diver.developermode.feature.player.Powers;
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
 * of those is locked, and what powers they have.
 *
 * <p>Respawning builds the player again from nothing rather than moving the
 * one that died, so the powers the old one had are handed to the new one as it
 * is built, before anything is told about it.
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
    private void developermode_tellArrival(ServerPlayerEntity player, ServerWorld world, CallbackInfo ci) {
        Locks.tell(player, world);
        Powers.arrive(player);
    }

    @ModifyExpressionValue(method = "respawnPlayer(Lnet/minecraft/entity/player/ServerPlayerEntity;I)Lnet/minecraft/entity/player/ServerPlayerEntity;", at = @At(value = "NEW", target = "net/minecraft/entity/player/ServerPlayerEntity"))
    private ServerPlayerEntity developermode_carryPowers(ServerPlayerEntity respawned, @Local(argsOnly = true) ServerPlayerEntity player) {
        Powers.set(respawned, Powers.of(player));
        return respawned;
    }
}
