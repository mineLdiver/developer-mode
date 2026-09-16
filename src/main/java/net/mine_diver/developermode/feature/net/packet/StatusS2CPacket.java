package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.feature.net.DevStatus;
import net.minecraft.network.NetworkHandler;
import net.minecraft.network.packet.Packet;
import net.modificationstation.stationapi.api.network.packet.ManagedPacket;
import net.modificationstation.stationapi.api.network.packet.PacketType;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * What came of a request, in words meant for the window that made it.
 *
 * <p>Carries the kind of request it answers, since several windows are waiting
 * and only one of them asked.
 */
public class StatusS2CPacket extends Packet implements ManagedPacket<StatusS2CPacket> {
    public static final PacketType<StatusS2CPacket> TYPE =
            PacketType.builder(true, false, StatusS2CPacket::new).build();

    private static final int MAX_LENGTH = 256;

    public String kind = "";
    public String text = "";
    public boolean ok;

    public StatusS2CPacket() {}

    public StatusS2CPacket(String kind, String text, boolean ok) {
        this.kind = kind;
        this.text = text;
        this.ok = ok;
    }

    @Override
    public void read(DataInputStream in) {
        try {
            kind = readString(in, MAX_LENGTH);
            text = readString(in, MAX_LENGTH);
            ok = in.readBoolean();
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void write(DataOutputStream out) {
        try {
            writeString(kind, out);
            writeString(text, out);
            out.writeBoolean(ok);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void apply(NetworkHandler handler) {
        DevStatus.set(kind, text, ok);
    }

    @Override
    public int size() {
        return Short.BYTES + kind.length() * Character.BYTES
                + Short.BYTES + text.length() * Character.BYTES
                + 1;
    }

    @Override
    public @NotNull PacketType<StatusS2CPacket> getType() {
        return TYPE;
    }
}
