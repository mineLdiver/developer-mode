package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.api.Message;
import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.net.Ops;
import net.mine_diver.developermode.feature.world.Time;
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
 * A request to move the sun on to a time of day.
 *
 * <p>Carries the time of day rather than a world time, so a client whose clock
 * has drifted from the server's still lands on the time it meant. Which day is
 * the world's to work out, and it is always the next one to read that time.
 */
public class TimeC2SPacket extends Packet implements ManagedPacket<TimeC2SPacket> {
    public static final PacketType<TimeC2SPacket> TYPE =
            PacketType.builder(false, true, TimeC2SPacket::new).build();

    public int timeOfDay;

    public TimeC2SPacket() {}

    public TimeC2SPacket(int timeOfDay) {
        this.timeOfDay = timeOfDay;
    }

    @Override
    public void read(DataInputStream in) {
        try {
            timeOfDay = in.readInt();
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void write(DataOutputStream out) {
        try {
            out.writeInt(timeOfDay);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void apply(NetworkHandler handler) {
        PlayerEntity player = PlayerHelper.getPlayerFromPacketHandler(handler);
        if (player == null) return;

        if (!Ops.allows(player)) {
            reply(player, Message.of("message.developermode.requires_operator"), false);
            return;
        }
        if (timeOfDay < 0 || timeOfDay >= Time.DAY) {
            reply(player, Message.of("message.developermode.nonsense_time"), false);
            return;
        }

        Message failure = Time.set(player.world, timeOfDay);
        reply(player, failure == null ? Message.of("message.developermode.time_moved") : failure, failure == null);
    }

    private static void reply(PlayerEntity player, Message message, boolean ok) {
        PacketHelper.sendTo(player, new StatusS2CPacket(DevStatus.TIME, message, ok));
    }

    @Override
    public int size() {
        return Integer.BYTES;
    }

    @Override
    public @NotNull PacketType<TimeC2SPacket> getType() {
        return TYPE;
    }
}
