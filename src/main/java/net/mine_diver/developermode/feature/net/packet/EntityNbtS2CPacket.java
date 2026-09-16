package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.feature.net.EntityNbtInbox;
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
 * An entity's NBT, as the world that owns it actually holds it.
 */
public class EntityNbtS2CPacket extends Packet implements ManagedPacket<EntityNbtS2CPacket> {
    public static final PacketType<EntityNbtS2CPacket> TYPE =
            PacketType.builder(true, false, EntityNbtS2CPacket::new).build();

    public int entityId;
    public byte[] nbt = PacketNbt.NONE;

    public EntityNbtS2CPacket() {}

    public EntityNbtS2CPacket(int entityId, NbtCompound compound) {
        this.entityId = entityId;
        this.nbt = PacketNbt.toBytes(compound);
    }

    @Override
    public void read(DataInputStream in) {
        try {
            entityId = in.readInt();
            nbt = new byte[in.readInt()];
            in.readFully(nbt);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void write(DataOutputStream out) {
        try {
            out.writeInt(entityId);
            out.writeInt(nbt.length);
            out.write(nbt);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void apply(NetworkHandler handler) {
        EntityNbtInbox.set(entityId, PacketNbt.fromBytes(nbt));
    }

    @Override
    public int size() {
        return Integer.BYTES * 2 + nbt.length;
    }

    @Override
    public @NotNull PacketType<EntityNbtS2CPacket> getType() {
        return TYPE;
    }
}
