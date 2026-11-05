package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.feature.Message;
import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.net.Ops;
import net.mine_diver.developermode.feature.world.Regenerate;
import net.mine_diver.developermode.feature.world.transform.Stack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.NetworkHandler;
import net.minecraft.network.packet.Packet;
import net.modificationstation.stationapi.api.entity.player.PlayerHelper;
import net.modificationstation.stationapi.api.network.packet.ManagedPacket;
import net.modificationstation.stationapi.api.network.packet.PacketHelper;
import net.modificationstation.stationapi.api.network.packet.PacketType;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * A request to regenerate a previewed square of chunks with the transforms
 * it was previewed with, so the result can be walked around in.
 *
 * <p>The transforms live on the client that tuned them, so they travel with
 * the request, and the side that owns the world lays them over its own
 * generator's noise. The square is named by its middle chunk and radius
 * rather than aimed, since the preview it came from was built around wherever
 * its window was opened.
 */
public class ApplyPreviewC2SPacket extends Packet implements ManagedPacket<ApplyPreviewC2SPacket> {
    public static final PacketType<ApplyPreviewC2SPacket> TYPE =
            PacketType.builder(false, true, ApplyPreviewC2SPacket::new).build();

    /** As far out as a preview reaches. */
    public static final int MAX_RADIUS = 6;
    /** Far more than any stack a person tunes by hand writes to. */
    private static final int MAX_STACK_BYTES = 16384;

    public int chunkX;
    public int chunkZ;
    public int radius;
    /** The stack as {@link Stack#write} writes it. */
    public byte[] stack = new byte[0];

    public ApplyPreviewC2SPacket() {}

    public ApplyPreviewC2SPacket(int chunkX, int chunkZ, int radius, Stack stack) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.radius = radius;
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            stack.write(new DataOutputStream(buffer));
            this.stack = buffer.toByteArray();
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void read(DataInputStream in) {
        try {
            chunkX = in.readInt();
            chunkZ = in.readInt();
            radius = in.readUnsignedByte();
            int length = in.readInt();
            if (length < 0 || length > MAX_STACK_BYTES) throw new IOException("A stack of " + length + " bytes");
            stack = new byte[length];
            in.readFully(stack);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void write(DataOutputStream out) {
        try {
            out.writeInt(chunkX);
            out.writeInt(chunkZ);
            out.writeByte(radius);
            out.writeInt(stack.length);
            out.write(stack);
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
        if (radius > MAX_RADIUS) {
            reply(player, Message.of("message.developermode.nonsense_radius"), false);
            return;
        }
        Stack transforms;
        try {
            transforms = Stack.read(new DataInputStream(new ByteArrayInputStream(stack)));
        } catch (IOException error) {
            reply(player, Message.of("message.developermode.unreadable_transforms"), false);
            return;
        }

        Message failure = Regenerate.regenerate(player.world, chunkX, chunkZ, radius, transforms);
        reply(player, failure == null ? Message.of("message.developermode.applied_preview") : failure, failure == null);
    }

    private static void reply(PlayerEntity player, Message message, boolean ok) {
        PacketHelper.sendTo(player, new StatusS2CPacket(DevStatus.REGENERATE, message, ok));
    }

    @Override
    public int size() {
        return Integer.BYTES * 3 + Byte.BYTES + stack.length;
    }

    @Override
    public @NotNull PacketType<ApplyPreviewC2SPacket> getType() {
        return TYPE;
    }
}
