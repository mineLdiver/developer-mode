package net.mine_diver.developermode.feature.net;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * NBT as a length prefixed blob, for packets that carry a whole compound.
 *
 * <p>Packets hold the bytes rather than the compound, so {@code size()} is the
 * real number of bytes on the wire instead of a guess, and so an unreadable
 * blob fails where it is decoded rather than where it is counted.
 *
 * <p>An absent compound is an empty array. Beta's format has no way to say
 * "nothing", and a zero length is cheaper than a presence flag.
 */
public final class PacketNbt {
    public static final byte[] NONE = new byte[0];

    private PacketNbt() {}

    public static byte[] toBytes(NbtCompound nbt) {
        if (nbt == null) return NONE;
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            NbtIo.writeCompressed(nbt, buffer);
            return buffer.toByteArray();
        } catch (Throwable error) {
            return NONE;
        }
    }

    /** @return null if there was nothing to read, or it could not be read */
    public static NbtCompound fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try {
            return NbtIo.readCompressed(new ByteArrayInputStream(bytes));
        } catch (Throwable error) {
            return null;
        }
    }
}
