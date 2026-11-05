package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.feature.Aim;
import net.mine_diver.developermode.feature.Message;
import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.net.Ops;
import net.mine_diver.developermode.feature.world.Regenerate;
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
 * A request to regenerate the chunks around wherever the sender is looking.
 *
 * <p>It carries how far the sender can see, for the reasons {@link Aim} gives,
 * and how many chunks out from the one pointed at to go.
 */
public class RegenerateC2SPacket extends Packet implements ManagedPacket<RegenerateC2SPacket> {
    public static final PacketType<RegenerateC2SPacket> TYPE =
            PacketType.builder(false, true, RegenerateC2SPacket::new).build();

    public double reach;
    public int radius;

    public RegenerateC2SPacket() {}

    public RegenerateC2SPacket(double reach, int radius) {
        this.reach = reach;
        this.radius = radius;
    }

    @Override
    public void read(DataInputStream in) {
        try {
            reach = in.readDouble();
            radius = in.readUnsignedByte();
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void write(DataOutputStream out) {
        try {
            out.writeDouble(reach);
            out.writeByte(radius);
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
        if (!Regenerate.legalRadius(radius)) {
            reply(player, Message.of("message.developermode.nonsense_radius"), false);
            return;
        }

        Message failure = Regenerate.regenerate(player, Aim.clamp(reach), radius);
        reply(player, failure == null ? Message.of("message.developermode.regenerated") : failure, failure == null);
    }

    private static void reply(PlayerEntity player, Message message, boolean ok) {
        PacketHelper.sendTo(player, new StatusS2CPacket(DevStatus.REGENERATE, message, ok));
    }

    @Override
    public int size() {
        return Double.BYTES + Byte.BYTES;
    }

    @Override
    public @NotNull PacketType<RegenerateC2SPacket> getType() {
        return TYPE;
    }
}
