package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.net.NbtTarget;
import net.mine_diver.developermode.feature.net.Ops;
import net.mine_diver.developermode.feature.net.PacketNbt;
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
 * Writes edited NBT back into whatever the target points at.
 *
 * <p>The answer carries a fresh dump as well as a message, because what a
 * thing accepts is not always what was typed at it.
 */
public class ApplyNbtC2SPacket extends Packet implements ManagedPacket<ApplyNbtC2SPacket> {
    public static final PacketType<ApplyNbtC2SPacket> TYPE =
            PacketType.builder(false, true, ApplyNbtC2SPacket::new).build();

    public NbtTarget target = NbtTarget.entity(-1);
    public byte[] nbt = PacketNbt.NONE;

    public ApplyNbtC2SPacket() {}

    public ApplyNbtC2SPacket(NbtTarget target, NbtCompound compound) {
        this.target = target;
        this.nbt = PacketNbt.toBytes(compound);
    }

    @Override
    public void read(DataInputStream in) {
        try {
            target = NbtTarget.read(in);
            nbt = new byte[in.readInt()];
            in.readFully(nbt);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void write(DataOutputStream out) {
        try {
            target.write(out);
            out.writeInt(nbt.length);
            out.write(nbt);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void apply(NetworkHandler handler) {
        PlayerEntity player = PlayerHelper.getPlayerFromPacketHandler(handler);
        if (player == null) return;

        if (!Ops.allows(player)) {
            reply(player, "Requires operator", false);
            return;
        }

        NbtCompound compound = PacketNbt.fromBytes(nbt);
        if (compound == null) {
            reply(player, "Unreadable NBT", false);
            return;
        }

        String failure = target.apply(player, compound);
        reply(player, failure == null ? "Applied" : failure, failure == null);

        NbtCompound fresh = target.dump(player);
        if (fresh != null) PacketHelper.sendTo(player, new NbtS2CPacket(target, fresh));
    }

    private static void reply(PlayerEntity player, String text, boolean ok) {
        PacketHelper.sendTo(player, new StatusS2CPacket(DevStatus.ENTITY, text, ok));
    }

    @Override
    public int size() {
        return NbtTarget.SIZE + Integer.BYTES + nbt.length;
    }

    @Override
    public @NotNull PacketType<ApplyNbtC2SPacket> getType() {
        return TYPE;
    }
}
