package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.net.Ops;
import net.mine_diver.developermode.feature.player.Power;
import net.mine_diver.developermode.feature.player.Powers;
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
 * Everything the sender would like to be, in one mask.
 *
 * <p>Sent whole rather than as a change, so a request carries no assumption
 * about what the world currently thinks the sender has. Whatever arrives is
 * put through {@link Power#legal} before it means anything: the bits came off
 * a socket, and being an operator is permission to fly, not permission to be
 * believed about what a combination of powers is.
 *
 * <p>A grant is answered without words, since the switch coming on in the menu
 * is the answer. The text is only ever a reason it did not.
 */
public class PowersC2SPacket extends Packet implements ManagedPacket<PowersC2SPacket> {
    public static final PacketType<PowersC2SPacket> TYPE =
            PacketType.builder(false, true, PowersC2SPacket::new).build();

    public int mask;

    public PowersC2SPacket() {}

    public PowersC2SPacket(int mask) {
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

        if (!Ops.allows(player)) {
            reply(player, "Requires operator", false);
            // What they have rather than what they asked for, so a client that
            // was hoping is put straight rather than left hoping.
            PacketHelper.sendTo(player, new PowersS2CPacket(Powers.of(player)));
            return;
        }

        int granted = Power.legal(mask);
        Powers.set(player, granted);
        reply(player, "", true);
        PacketHelper.sendTo(player, new PowersS2CPacket(granted));
    }

    private static void reply(PlayerEntity player, String text, boolean ok) {
        PacketHelper.sendTo(player, new StatusS2CPacket(DevStatus.POWERS, text, ok));
    }

    @Override
    public int size() {
        return Integer.BYTES;
    }

    @Override
    public @NotNull PacketType<PowersC2SPacket> getType() {
        return TYPE;
    }
}
