package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.feature.entity.Entities;
import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.net.FreezeMode;
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
 * A request to stop an entity ticking, or to let it go again.
 *
 * <p>The freeze is recorded against the sender, so that a client which drops
 * does not leave an entity standing still with nobody left who can see it on
 * their HUD or press the button that releases it.
 */
public class FreezeC2SPacket extends Packet implements ManagedPacket<FreezeC2SPacket> {
    public static final PacketType<FreezeC2SPacket> TYPE =
            PacketType.builder(false, true, FreezeC2SPacket::new).build();

    public int entityId;
    public byte mode;

    public FreezeC2SPacket() {}

    public FreezeC2SPacket(int entityId, byte mode) {
        this.entityId = entityId;
        this.mode = mode;
    }

    @Override
    public void read(DataInputStream in) {
        try {
            entityId = in.readInt();
            mode = in.readByte();
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void write(DataOutputStream out) {
        try {
            out.writeInt(entityId);
            out.writeByte(mode);
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

        Entity entity = FreezeMode.needsEntity(mode) ? Entities.byId(player.world, entityId) : null;
        if (FreezeMode.needsEntity(mode) && entity == null) return;

        FreezeMode.apply(mode, entity, player);
        // Echoed rather than assumed, so the client mirrors what happened.
        PacketHelper.sendTo(player, new FrozenS2CPacket(entityId, mode));
    }

    @Override
    public int size() {
        return Integer.BYTES + 1;
    }

    @Override
    public @NotNull PacketType<FreezeC2SPacket> getType() {
        return TYPE;
    }
}
