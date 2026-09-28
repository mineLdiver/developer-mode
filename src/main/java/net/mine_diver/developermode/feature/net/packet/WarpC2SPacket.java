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
 * A request to stand wherever the sender is looking.
 *
 * <p>It carries how far they can see and nothing else. Where to go is worked
 * out on this side, from where the sender is and which way they face, so the
 * only places reachable are the ones the world itself puts in front of them.
 *
 * <p>How far is still the client's to say, because how far the world is drawn
 * is something only it knows, and pointing past the fog is pointing at
 * nothing. It is a number off a socket all the same, so it is clamped to the
 * widest any client draws.
 */
public class WarpC2SPacket extends Packet implements ManagedPacket<WarpC2SPacket> {
    public static final PacketType<WarpC2SPacket> TYPE =
            PacketType.builder(false, true, WarpC2SPacket::new).build();

    /** Beta's widest draw distance, which nothing can see past. */
    private static final double MAX_REACH = 256;

    public double reach;

    public WarpC2SPacket() {}

    public WarpC2SPacket(double reach) {
        this.reach = reach;
    }

    @Override
    public void read(DataInputStream in) {
        try {
            reach = in.readDouble();
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void write(DataOutputStream out) {
        try {
            out.writeDouble(reach);
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
        if (Double.isNaN(reach) || Double.isInfinite(reach)) {
            reply(player, Message.of("message.developermode.nonsense_reach"), false);
            return;
        }

        Message failure = Warp.warp(player, Math.min(Math.max(reach, 0), MAX_REACH));
        reply(player, failure == null ? Message.of("message.developermode.warped") : failure, failure == null);
    }

    private static void reply(PlayerEntity player, Message message, boolean ok) {
        PacketHelper.sendTo(player, new StatusS2CPacket(DevStatus.WARP, message, ok));
    }

    @Override
    public int size() {
        return Double.BYTES;
    }

    @Override
    public @NotNull PacketType<WarpC2SPacket> getType() {
        return TYPE;
    }
}
