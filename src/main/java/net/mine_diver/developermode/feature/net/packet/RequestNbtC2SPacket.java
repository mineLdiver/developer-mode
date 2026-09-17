package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.net.NbtTarget;
import net.mine_diver.developermode.feature.net.Ops;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
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
 * Asks the world what it is actually holding for a target.
 */
public class RequestNbtC2SPacket extends Packet implements ManagedPacket<RequestNbtC2SPacket> {
    public static final PacketType<RequestNbtC2SPacket> TYPE =
            PacketType.builder(false, true, RequestNbtC2SPacket::new).build();

    public NbtTarget target = NbtTarget.entity(-1);

    public RequestNbtC2SPacket() {}

    public RequestNbtC2SPacket(NbtTarget target) {
        this.target = target;
    }

    @Override
    public void read(DataInputStream in) {
        try {
            target = NbtTarget.read(in);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void write(DataOutputStream out) {
        try {
            target.write(out);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void apply(NetworkHandler handler) {
        PlayerEntity player = PlayerHelper.getPlayerFromPacketHandler(handler);
        if (player == null) return;

        if (!Ops.allows(player)) {
            PacketHelper.sendTo(player, new StatusS2CPacket(DevStatus.ENTITY, "Requires operator", false));
            return;
        }

        NbtCompound nbt = target.dump(player);
        if (nbt == null) {
            PacketHelper.sendTo(player,
                    new StatusS2CPacket(DevStatus.ENTITY, "No " + target.describe() + " to read", false));
            return;
        }
        PacketHelper.sendTo(player, new NbtS2CPacket(target, nbt));
    }

    @Override
    public int size() {
        return NbtTarget.SIZE;
    }

    @Override
    public @NotNull PacketType<RequestNbtC2SPacket> getType() {
        return TYPE;
    }
}
