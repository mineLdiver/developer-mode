package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.feature.net.NbtInbox;
import net.mine_diver.developermode.feature.net.NbtTarget;
import net.mine_diver.developermode.feature.net.PacketNbt;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.NetworkHandler;
import net.minecraft.network.packet.Packet;
import net.modificationstation.stationapi.api.network.packet.ManagedPacket;
import net.modificationstation.stationapi.api.network.packet.PacketType;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * NBT as the world that owns it actually holds it.
 */
public class NbtS2CPacket extends Packet implements ManagedPacket<NbtS2CPacket> {
    public static final PacketType<NbtS2CPacket> TYPE =
            PacketType.builder(true, false, NbtS2CPacket::new).build();

    public NbtTarget target = NbtTarget.entity(-1);
    public byte[] nbt = PacketNbt.NONE;

    public NbtS2CPacket() {}

    public NbtS2CPacket(NbtTarget target, NbtCompound compound) {
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
        NbtInbox.set(target, PacketNbt.fromBytes(nbt));
    }

    @Override
    public int size() {
        return NbtTarget.SIZE + Integer.BYTES + nbt.length;
    }

    @Override
    public @NotNull PacketType<NbtS2CPacket> getType() {
        return TYPE;
    }
}
