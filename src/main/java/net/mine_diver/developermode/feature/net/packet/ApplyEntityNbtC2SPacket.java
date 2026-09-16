package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.feature.entity.Entities;
import net.mine_diver.developermode.feature.entity.EntityNbt;
import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.net.Ops;
import net.mine_diver.developermode.feature.net.PacketNbt;
import net.minecraft.entity.Entity;
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
 * Writes edited NBT back into an entity.
 *
 * <p>The answer carries a fresh dump as well as a message, because what an
 * entity accepts is not always what was typed at it.
 */
public class ApplyEntityNbtC2SPacket extends Packet implements ManagedPacket<ApplyEntityNbtC2SPacket> {
    public static final PacketType<ApplyEntityNbtC2SPacket> TYPE =
            PacketType.builder(false, true, ApplyEntityNbtC2SPacket::new).build();

    public int entityId;
    public byte[] nbt = PacketNbt.NONE;

    public ApplyEntityNbtC2SPacket() {}

    public ApplyEntityNbtC2SPacket(int entityId, NbtCompound compound) {
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
        PlayerEntity player = PlayerHelper.getPlayerFromPacketHandler(handler);
        if (player == null) return;

        if (!Ops.allows(player)) {
            reply(player, "Requires operator", false);
            return;
        }

        Entity entity = Entities.byId(player.world, entityId);
        if (entity == null) {
            reply(player, "That entity is gone", false);
            return;
        }

        NbtCompound compound = PacketNbt.fromBytes(nbt);
        if (compound == null) {
            reply(player, "Unreadable NBT", false);
            return;
        }

        String failure = EntityNbt.apply(entity, compound);
        reply(player, failure == null ? "Applied" : failure, failure == null);
        PacketHelper.sendTo(player, new EntityNbtS2CPacket(entityId, EntityNbt.dump(entity)));
    }

    private static void reply(PlayerEntity player, String text, boolean ok) {
        PacketHelper.sendTo(player, new StatusS2CPacket(DevStatus.ENTITY, text, ok));
    }

    @Override
    public int size() {
        return Integer.BYTES * 2 + nbt.length;
    }

    @Override
    public @NotNull PacketType<ApplyEntityNbtC2SPacket> getType() {
        return TYPE;
    }
}
