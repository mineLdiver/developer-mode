package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.feature.entity.Entities;
import net.mine_diver.developermode.feature.entity.EntityNbt;
import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.net.Ops;
import net.minecraft.entity.Entity;
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
 * Asks the world that owns an entity what it is actually holding.
 */
public class RequestEntityNbtC2SPacket extends Packet implements ManagedPacket<RequestEntityNbtC2SPacket> {
    public static final PacketType<RequestEntityNbtC2SPacket> TYPE =
            PacketType.builder(false, true, RequestEntityNbtC2SPacket::new).build();

    public int entityId;

    public RequestEntityNbtC2SPacket() {}

    public RequestEntityNbtC2SPacket(int entityId) {
        this.entityId = entityId;
    }

    @Override
    public void read(DataInputStream in) {
        try {
            entityId = in.readInt();
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void write(DataOutputStream out) {
        try {
            out.writeInt(entityId);
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

        Entity entity = Entities.byId(player.world, entityId);
        if (entity == null) {
            PacketHelper.sendTo(player, new StatusS2CPacket(DevStatus.ENTITY, "That entity is gone", false));
            return;
        }
        PacketHelper.sendTo(player, new EntityNbtS2CPacket(entityId, EntityNbt.dump(entity)));
    }

    @Override
    public int size() {
        return Integer.BYTES;
    }

    @Override
    public @NotNull PacketType<RequestEntityNbtC2SPacket> getType() {
        return TYPE;
    }
}
