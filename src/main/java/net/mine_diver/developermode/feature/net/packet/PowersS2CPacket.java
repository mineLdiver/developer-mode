package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.feature.player.Powers;
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
 * What the sender actually has, which is the only thing the client may act on.
 *
 * <p>The client mirrors this rather than what it asked for, because it is the
 * side that does the flying: a grant that was turned down and then flown on
 * anyway is what gets a player thrown off a server for floating.
 */
public class PowersS2CPacket extends Packet implements ManagedPacket<PowersS2CPacket> {
    public static final PacketType<PowersS2CPacket> TYPE =
            PacketType.builder(true, false, PowersS2CPacket::new).build();

    public int mask;

    public PowersS2CPacket() {}

    public PowersS2CPacket(int mask) {
        this.mask = mask;
    }

    @Override
    public void read(DataInputStream in) {
        try {
            mask = in.readInt();
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void write(DataOutputStream out) {
        try {
            out.writeInt(mask);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void apply(NetworkHandler handler) {
        PlayerEntity player = PlayerHelper.getPlayerFromPacketHandler(handler);
        if (player == null) return;
        Powers.set(player, mask);
    }

    @Override
    public int size() {
        return Integer.BYTES;
    }

    @Override
    public @NotNull PacketType<PowersS2CPacket> getType() {
        return TYPE;
    }
}
