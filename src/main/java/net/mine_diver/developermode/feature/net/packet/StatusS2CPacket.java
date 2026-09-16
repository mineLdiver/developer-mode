package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.feature.net.DevStatus;
import net.minecraft.network.NetworkHandler;
import net.minecraft.network.packet.Packet;
import net.modificationstation.stationapi.api.network.packet.ManagedPacket;
import net.modificationstation.stationapi.api.network.packet.PacketType;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;

/**
 * What came of a request, in words meant for the window that made it.
 */
public class StatusS2CPacket extends Packet implements ManagedPacket<StatusS2CPacket> {
    public static final PacketType<StatusS2CPacket> TYPE =
            PacketType.builder(true, false, StatusS2CPacket::new).build();

    private static final int MAX_LENGTH = 256;

    public String text = "";

    public StatusS2CPacket() {}

    public StatusS2CPacket(String text) {
        this.text = text;
    }

    @Override
    public void read(DataInputStream in) {
        text = readString(in, MAX_LENGTH);
    }

    @Override
    public void write(DataOutputStream out) {
        writeString(text, out);
    }

    @Override
    public void apply(NetworkHandler handler) {
        DevStatus.set(text);
    }

    @Override
    public int size() {
        return Short.BYTES + text.length() * Character.BYTES;
    }

    @Override
    public @NotNull PacketType<StatusS2CPacket> getType() {
        return TYPE;
    }
}
