package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.feature.entity.EntitySummoning;
import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.net.Ops;
import net.mine_diver.developermode.feature.net.PacketNbt;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
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
 * A request to put a new entity in the world, optionally loaded with NBT.
 *
 * <p>The position is the one the sender was pointing at, so it is checked
 * against where the sender actually is. An operator may spawn whatever they
 * like, but a position that is not a number would leave the world holding an
 * entity nothing can find again.
 */
public class SummonC2SPacket extends Packet implements ManagedPacket<SummonC2SPacket> {
    public static final PacketType<SummonC2SPacket> TYPE =
            PacketType.builder(false, true, SummonC2SPacket::new).build();

    private static final int MAX_TYPE_LENGTH = 256;
    /** Generous next to the picker's own 48 block reach, and still finite. */
    private static final double MAX_DISTANCE = 128;

    public String type = "";
    public double x;
    public double y;
    public double z;
    public float yaw;
    public byte[] nbt = PacketNbt.NONE;

    public SummonC2SPacket() {}

    public SummonC2SPacket(String type, double x, double y, double z, float yaw, NbtCompound preset) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.nbt = PacketNbt.toBytes(preset);
    }

    @Override
    public void read(DataInputStream in) {
        try {
            type = readString(in, MAX_TYPE_LENGTH);
            x = in.readDouble();
            y = in.readDouble();
            z = in.readDouble();
            yaw = in.readFloat();
            nbt = new byte[in.readInt()];
            in.readFully(nbt);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void write(DataOutputStream out) {
        try {
            writeString(type, out);
            out.writeDouble(x);
            out.writeDouble(y);
            out.writeDouble(z);
            out.writeFloat(yaw);
            out.writeInt(nbt.length);
            out.write(nbt);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void apply(NetworkHandler handler) {
        PlayerEntity player = PlayerHelper.getPlayerFromPacketHandler(handler);
        if (player == null) return;

        if (!Ops.allows(player)) {
            reply(player, "Requires operator", false);
            return;
        }
        if (!finite(x) || !finite(y) || !finite(z) || !finite(yaw)) {
            reply(player, "Nonsense position", false);
            return;
        }
        if (player.getSquaredDistance(x, y, z) > MAX_DISTANCE * MAX_DISTANCE) {
            reply(player, "Too far away", false);
            return;
        }

        String failure = EntitySummoning.summon(player.world, type, x, y, z, yaw, PacketNbt.fromBytes(nbt));
        reply(player, failure == null ? "Summoned " + type : failure, failure == null);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static void reply(PlayerEntity player, String text, boolean ok) {
        PacketHelper.sendTo(player, new StatusS2CPacket(DevStatus.SUMMON, text, ok));
    }

    @Override
    public int size() {
        return Short.BYTES + type.length() * Character.BYTES
                + Double.BYTES * 3 + Float.BYTES
                + Integer.BYTES + nbt.length;
    }

    @Override
    public @NotNull PacketType<SummonC2SPacket> getType() {
        return TYPE;
    }
}
