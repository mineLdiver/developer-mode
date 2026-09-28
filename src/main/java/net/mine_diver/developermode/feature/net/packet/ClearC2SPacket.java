package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.feature.Aim;
import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.net.Ops;
import net.mine_diver.developermode.feature.world.Clearing;
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
 * A request to take away one kind of thing from around the sender.
 *
 * <p>Answered under the kind of clearing it asked for, since the two are
 * separate slots and each says only its own. Clearing nothing is reported as a
 * failure: the slot has no other way to say that it did go through, and there
 * was simply nothing there.
 */
public class ClearC2SPacket extends Packet implements ManagedPacket<ClearC2SPacket> {
    public static final PacketType<ClearC2SPacket> TYPE =
            PacketType.builder(false, true, ClearC2SPacket::new).build();

    public byte what;
    public double reach;


    public ClearC2SPacket() {}

    public ClearC2SPacket(byte what, double reach) {
        this.what = what;
        this.reach = reach;
    }

    @Override
    public void read(DataInputStream in) {
        try {
            what = in.readByte();
            reach = in.readDouble();
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void write(DataOutputStream out) {
        try {
            out.writeByte(what);
            out.writeDouble(reach);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void apply(NetworkHandler handler) {
        PlayerEntity player = PlayerHelper.getPlayerFromPacketHandler(handler);
        if (player == null) return;

        if (!Clearing.legal(what)) return;
        String kind = what == Clearing.MOBS ? DevStatus.PURGE : DevStatus.SWEEP;

        if (!Ops.allows(player)) {
            reply(player, kind, "Requires operator", false);
            return;
        }
        if (!Aim.sensible(reach)) {
            reply(player, kind, "Nonsense reach", false);
            return;
        }

        int cleared = Clearing.clear(player, what, Aim.clamp(reach));
        reply(player, kind, cleared == 0 ? "Nothing in sight" : "Cleared " + cleared, cleared > 0);
    }

    private static void reply(PlayerEntity player, String kind, String text, boolean ok) {
        PacketHelper.sendTo(player, new StatusS2CPacket(kind, text, ok));
    }

    @Override
    public int size() {
        return 1 + Double.BYTES;
    }

    @Override
    public @NotNull PacketType<ClearC2SPacket> getType() {
        return TYPE;
    }
}
