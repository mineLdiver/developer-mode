package net.mine_diver.developermode.mixin;

import net.mine_diver.developermode.feature.player.Power;
import net.mine_diver.developermode.feature.player.Powers;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets a player who was granted flight fly on a server that has flight off.
 *
 * <p>Beta counts the ticks a player spends in the air without descending and
 * disconnects them after four seconds of it, unless the server was started
 * with flight allowed. That counter is the whole of what stands between this
 * mod's flight and a multiplayer world.
 *
 * <p>The server's own setting is left exactly as it is: what is answered per
 * player here is the question the counter asks, so allowing one operator to
 * fly is not the same as turning flight on for everybody.
 */
@Mixin(ServerPlayNetworkHandler.class)
class ServerPlayNetworkHandlerMixin {
    @Shadow
    private ServerPlayerEntity player;

    @Redirect(
            method = "onPlayerMove(Lnet/minecraft/network/packet/play/PlayerMovePacket;)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/server/MinecraftServer;flightEnabled:Z"
            )
    )
    private boolean developermode_allowGrantedFlight(MinecraftServer server) {
        return server.flightEnabled || Powers.has(player, Power.FLIGHT);
    }
}
