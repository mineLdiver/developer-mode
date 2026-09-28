package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.feature.Message;
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
 *
 * <p>The words go as a {@link Message}, key and arguments, and are translated
 * by whatever shows them. Nested messages go as they are, up to a depth and a count that
 * no real reason comes near, so a bad packet is refused rather than followed.
 */
public class StatusS2CPacket extends Packet implements ManagedPacket<StatusS2CPacket> {
    public static final PacketType<StatusS2CPacket> TYPE =
            PacketType.builder(true, false, StatusS2CPacket::new).build();

    private static final int MAX_LENGTH = 256;
    private static final int MAX_ARGS = 8;
    private static final int MAX_DEPTH = 4;

    public String kind = "";
    /** Null when there is nothing to say beyond whether it worked. */
    public Message message;
    public boolean ok;

    public StatusS2CPacket() {}

    public StatusS2CPacket(String kind, Message message, boolean ok) {
        this.kind = kind;
        this.message = message;
        this.ok = ok;
    }

    @Override
    public void read(DataInputStream in) {
        try {
            kind = readString(in, MAX_LENGTH);
            message = in.readBoolean() ? readMessage(in, 0) : null;
            ok = in.readBoolean();
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void write(DataOutputStream out) {
        try {
            writeString(kind, out);
            out.writeBoolean(message != null);
            if (message != null) writeMessage(message, out);
            out.writeBoolean(ok);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void apply(NetworkHandler handler) {
        DevStatus.set(kind, message, ok);
    }

    private static Message readMessage(DataInputStream in, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("Status message nested too deep");
        if (in.readBoolean()) return Message.literal(readString(in, MAX_LENGTH));

        String key = readString(in, MAX_LENGTH);
        int count = in.readUnsignedByte();
        if (count > MAX_ARGS) throw new IOException("Status message has " + count + " arguments");
        Object[] args = new Object[count];
        for (int i = 0; i < count; i++)
            args[i] = in.readBoolean() ? readMessage(in, depth + 1) : readString(in, MAX_LENGTH);
        return Message.of(key, args);
    }

    private static void writeMessage(Message message, DataOutputStream out) throws IOException {
        Object[] args = message.args();
        out.writeBoolean(message.isLiteral());
        if (message.isLiteral()) {
            writeString((String) args[0], out);
            return;
        }

        writeString(message.key(), out);
        out.writeByte(args.length);
        for (Object arg : args) {
            out.writeBoolean(arg instanceof Message);
            if (arg instanceof Message inner) writeMessage(inner, out);
            else writeString((String) arg, out);
        }
    }

    private static int sizeOf(Message message) {
        Object[] args = message.args();
        if (message.isLiteral()) return 1 + sizeOf((String) args[0]);

        int size = 1 + sizeOf(message.key()) + 1;
        for (Object arg : args)
            size += 1 + (arg instanceof Message inner ? sizeOf(inner) : sizeOf((String) arg));
        return size;
    }

    private static int sizeOf(String text) {
        return Short.BYTES + text.length() * Character.BYTES;
    }

    @Override
    public int size() {
        return sizeOf(kind) + 1 + (message == null ? 0 : sizeOf(message)) + 1;
    }

    @Override
    public @NotNull PacketType<StatusS2CPacket> getType() {
        return TYPE;
    }
}
