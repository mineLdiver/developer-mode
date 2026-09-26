package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.net.Ops;
import net.mine_diver.developermode.feature.world.Locks;
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
 * A request to lock the sun or the weather in the sender's world to one
 * value, or to unlock it.
 *
 * <p>Sent as the state wanted rather than as a flip, so two requests that
 * cross on the wire cannot leave it the opposite of what either asked for.
 * Answered under the kind it locks, since that is the level the slot sits on,
 * and without words when it goes through: the slot's band is the answer.
 */
public class LockC2SPacket extends Packet implements ManagedPacket<LockC2SPacket> {
    public static final PacketType<LockC2SPacket> TYPE =
            PacketType.builder(false, true, LockC2SPacket::new).build();

    public byte what;
    public boolean lock;
    public int value;

    public LockC2SPacket() {}

    public LockC2SPacket(byte what, boolean lock, int value) {
        this.what = what;
        this.lock = lock;
        this.value = value;
    }

    @Override
    public void read(DataInputStream in) {
        try {
            what = in.readByte();
            lock = in.readBoolean();
            value = in.readInt();
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void write(DataOutputStream out) {
        try {
            out.writeByte(what);
            out.writeBoolean(lock);
            out.writeInt(value);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void apply(NetworkHandler handler) {
        PlayerEntity player = PlayerHelper.getPlayerFromPacketHandler(handler);
        if (player == null) return;
        String kind = what == Locks.WEATHER ? DevStatus.WEATHER : DevStatus.TIME;

        if (!Ops.allows(player)) {
            reply(player, kind, "Requires operator", false);
            return;
        }

        String failure = Locks.lock(player.world, what, lock, value);
        reply(player, kind, failure == null ? "" : failure, failure == null);
    }

    private static void reply(PlayerEntity player, String kind, String text, boolean ok) {
        PacketHelper.sendTo(player, new StatusS2CPacket(kind, text, ok));
    }

    @Override
    public int size() {
        return 2 + Integer.BYTES;
    }

    @Override
    public @NotNull PacketType<LockC2SPacket> getType() {
        return TYPE;
    }
}
