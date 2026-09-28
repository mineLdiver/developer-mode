package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.feature.Aim;
import net.mine_diver.developermode.feature.Message;
import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.net.Ops;
import net.mine_diver.developermode.feature.world.Smite;
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
 * A request for lightning wherever the sender is looking.
 *
 * <p>It carries how far the sender can see and nothing else, for the reasons
 * {@link Aim} gives.
 */
public class SmiteC2SPacket extends Packet implements ManagedPacket<SmiteC2SPacket> {
    public static final PacketType<SmiteC2SPacket> TYPE =
            PacketType.builder(false, true, SmiteC2SPacket::new).build();

    public double reach;

    public SmiteC2SPacket() {}

    public SmiteC2SPacket(double reach) {
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
        if (!Aim.sensible(reach)) {
            reply(player, Message.of("message.developermode.nonsense_reach"), false);
            return;
        }

        Message failure = Smite.smite(player, Aim.clamp(reach));
        reply(player, failure == null ? Message.of("message.developermode.struck") : failure, failure == null);
    }

    private static void reply(PlayerEntity player, Message message, boolean ok) {
        PacketHelper.sendTo(player, new StatusS2CPacket(DevStatus.SMITE, message, ok));
    }

    @Override
    public int size() {
        return Double.BYTES;
    }

    @Override
    public @NotNull PacketType<SmiteC2SPacket> getType() {
        return TYPE;
    }
}
