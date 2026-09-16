package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.feature.entity.Entities;
import net.mine_diver.developermode.feature.net.FreezeMode;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.NetworkHandler;
import net.minecraft.network.packet.Packet;
import net.modificationstation.stationapi.api.entity.player.PlayerHelper;
import net.modificationstation.stationapi.api.network.packet.ManagedPacket;
import net.modificationstation.stationapi.api.network.packet.PacketType;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * What the world actually did about a freeze.
 *
 * <p>The client mirrors it onto its own copy of the entity, which is what stops
 * the copy walking on while the real one stands still.
 */
public class FrozenS2CPacket extends Packet implements ManagedPacket<FrozenS2CPacket> {
    public static final PacketType<FrozenS2CPacket> TYPE =
            PacketType.builder(true, false, FrozenS2CPacket::new).build();

    public int entityId;
    public byte mode;

    public FrozenS2CPacket() {}

    public FrozenS2CPacket(int entityId, byte mode) {
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
        // Nobody owns anything on a client: there is only one of you here, and
        // a release means every freeze this client knows about.
        FreezeMode.apply(mode, Entities.byId(player.world, entityId), null);
    }

    @Override
    public int size() {
        return Integer.BYTES + 1;
    }

    @Override
    public @NotNull PacketType<FrozenS2CPacket> getType() {
        return TYPE;
    }
}
