package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.feature.Message;
import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.net.Ops;
import net.mine_diver.developermode.feature.player.Warp;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.NetworkHandler;
import net.minecraft.network.packet.Packet;
import net.modificationstation.stationapi.api.entity.player.PlayerHelper;
import net.modificationstation.stationapi.api.network.packet.ManagedPacket;
import net.modificationstation.stationapi.api.network.packet.PacketHelper;
import net.modificationstation.stationapi.api.network.packet.PacketType;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * A request to stand on top of a block, named by its position.
 *
 * <p>Unlike {@link WarpC2SPacket} this goes wherever it is told, which is the
 * point: the place was picked somewhere other than the world in front of the
 * sender. Operators can already go anywhere they like, so the only checks are
 * that the place is one the world has.
 */
public class WarpOntoC2SPacket extends Packet implements ManagedPacket<WarpOntoC2SPacket> {
    public static final PacketType<WarpOntoC2SPacket> TYPE =
            PacketType.builder(false, true, WarpOntoC2SPacket::new).build();

    /** Beta's world border, past which terrain stops making sense. */
    private static final int FARTHEST = 32_000_000;

    public int x;
    public int y;
    public int z;

    public WarpOntoC2SPacket() {}

    public WarpOntoC2SPacket(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void read(DataInputStream in) {
        try {
            x = in.readInt();
            y = in.readInt();
            z = in.readInt();
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void write(DataOutputStream out) {
        try {
            out.writeInt(x);
            out.writeInt(y);
            out.writeInt(z);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void apply(NetworkHandler handler) {
        PlayerEntity player = PlayerHelper.getPlayerFromPacketHandler(handler);
        if (player == null) return;

        if (!Ops.allows(player)) {
            reply(player, Message.of("message.developermode.requires_operator"), false);
            return;
        }
        if (Math.abs(x) > FARTHEST || Math.abs(z) > FARTHEST
                || y < player.world.getBottomY() || y >= player.world.getTopY()) {
            reply(player, Message.of("message.developermode.nonsense_position"), false);
            return;
        }

        Warp.onto(player, x, y, z);
        reply(player, Message.of("message.developermode.warped"), true);
    }

    private static void reply(PlayerEntity player, Message message, boolean ok) {
        PacketHelper.sendTo(player, new StatusS2CPacket(DevStatus.WARP, message, ok));
    }

    @Override
    public int size() {
        return Integer.BYTES * 3;
    }

    @Override
    public @NotNull PacketType<WarpOntoC2SPacket> getType() {
        return TYPE;
    }
}
