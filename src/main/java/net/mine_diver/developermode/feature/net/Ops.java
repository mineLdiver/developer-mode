package net.mine_diver.developermode.feature.net;

import net.minecraft.entity.player.PlayerEntity;
import net.modificationstation.stationapi.api.util.SideUtil;

/**
 * Who is allowed to use the tools.
 *
 * <p>On a server, operators. In singleplayer there is nobody else to ask:
 * Beta has no integrated server, so a local world has no operator list and the
 * only player is the one running the game.
 */
public final class Ops {
    private Ops() {}

    public static boolean allows(PlayerEntity player) {
        // Lambdas rather than method references. A method reference loads its
        // target class on the side that never calls it, and MinecraftServer is
        // not there to load on a client.
        return SideUtil.get(() -> true, () -> ServerOps.isOperator(player));
    }
}
