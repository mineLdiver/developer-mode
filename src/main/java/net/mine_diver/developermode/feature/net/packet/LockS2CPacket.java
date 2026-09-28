package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.feature.world.Locks;
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
 * Everything locked in the receiver's world, sent whole.
 *
 * <p>Whole rather than as a change, so a client that missed one is put right
 * by the next. The sky is drawn by the client, so it has to know where the
 * sun is locked to draw it there, and the ring has to know which slots to
 * show as locked.
 */
public class LockS2CPacket extends Packet implements ManagedPacket<LockS2CPacket> {
    public static final PacketType<LockS2CPacket> TYPE =
            PacketType.builder(true, false, LockS2CPacket::new).build();

    public int lockedTime;
    public int lockedWeather;

    public LockS2CPacket() {}

    public LockS2CPacket(int lockedTime, int lockedWeather) {
        this.lockedTime = lockedTime;
        this.lockedWeather = lockedWeather;
    }

    @Override
    public void read(DataInputStream in) {
        try {
            lockedTime = in.readInt();
            lockedWeather = in.readInt();
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void write(DataOutputStream out) {
        try {
            out.writeInt(lockedTime);
            out.writeInt(lockedWeather);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void apply(NetworkHandler handler) {
        PlayerEntity player = PlayerHelper.getPlayerFromPacketHandler(handler);
        if (player == null || player.world == null) return;
        Locks.mirror(player.world, lockedTime, lockedWeather);
    }

    @Override
    public int size() {
        return Integer.BYTES * 2;
    }

    @Override
    public @NotNull PacketType<LockS2CPacket> getType() {
        return TYPE;
    }
}
