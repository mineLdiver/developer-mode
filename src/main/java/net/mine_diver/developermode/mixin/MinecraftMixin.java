package net.mine_diver.developermode.mixin;

import net.mine_diver.developermode.client.summon.SummonMode;
import net.mine_diver.developermode.client.tool.ToolMode;
import net.mine_diver.developermode.feature.player.Powers;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps clicks made while placing entities or holding a tool away from the
 * world, and keeps a player's powers when the game swaps their player out.
 *
 * <p>Without the first, putting a mob down would also swing at whatever is
 * there, holding the button would start mining it, and putting a tool away
 * would open the chest it was pointed at.
 *
 * <p>Respawning builds a new player rather than reviving the old one, and on a
 * server so does being sent to another dimension. In singleplayer this client
 * is what decides the powers, so the new player is handed the old one's here or
 * they would be lost with it. On a server the answer is sent again anyway, and
 * carrying it over only keeps the new player from falling for the moment until
 * it arrives.
 */
@Mixin(Minecraft.class)
class MinecraftMixin {
    @Shadow
    public ClientPlayerEntity player;

    /** What the player being replaced had, held across the replacement. */
    @Unique
    private int developermode_carriedPowers;

    @Inject(method = "handleMouseClick(I)V", at = @At("HEAD"), cancellable = true)
    private void developermode_inspectClick(int button, CallbackInfo ci) {
        // Summon mode does its own edge detection, since this handler repeats
        // while the button is held. It only needs the click kept off the world.
        if (SummonMode.isActive() || ToolMode.isArmed()) ci.cancel();
    }

    @Inject(method = "handleMouseDown(IZ)V", at = @At("HEAD"), cancellable = true)
    private void developermode_inspectHold(int button, boolean holdingAttack, CallbackInfo ci) {
        if (SummonMode.isActive() || ToolMode.isArmed()) ci.cancel();
    }

    @Inject(method = "respawnPlayer(ZI)V", at = @At("HEAD"))
    private void developermode_holdPowers(boolean worldSpawn, int dimension, CallbackInfo ci) {
        developermode_carriedPowers = Powers.of(player);
    }

    @Inject(method = "respawnPlayer(ZI)V", at = @At("TAIL"))
    private void developermode_carryPowers(boolean worldSpawn, int dimension, CallbackInfo ci) {
        Powers.set(player, developermode_carriedPowers);
    }
}
