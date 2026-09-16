package net.mine_diver.developermode.feature.net;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;

/**
 * The operator list, which only exists on a server.
 *
 * <p>Loaded only from {@link Ops}, and only on the side that has a
 * MinecraftServer to ask.
 */
final class ServerOps {
    private ServerOps() {}

    @SuppressWarnings("deprecation")
    static boolean isOperator(PlayerEntity player) {
        MinecraftServer server = (MinecraftServer) FabricLoader.getInstance().getGameInstance();
        return server != null && player != null && server.playerManager.isOperator(player.name);
    }
}
