package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.net.Ops;
import net.mine_diver.developermode.feature.player.Heal;
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

/**
 * A request to be put back together, carrying nothing.
 *
 * <p>Who is asking is the connection it came in on, and what full health is
 * belongs to the player it is asked about. There is nothing left to send, and
 * nothing a client could get wrong.
 */
public class HealC2SPacket extends Packet implements ManagedPacket<HealC2SPacket> {
    public static final PacketType<HealC2SPacket> TYPE =
            PacketType.builder(false, true, HealC2SPacket::new).build();

    public HealC2SPacket() {}

    @Override
    public void read(DataInputStream in) {}

    @Override
    public void write(DataOutputStream out) {}

    @Override
    public void apply(NetworkHandler handler) {
        PlayerEntity player = PlayerHelper.getPlayerFromPacketHandler(handler);
        if (player == null) return;

        if (!Ops.allows(player)) {
            reply(player, "Requires operator", false);
            return;
        }

        String failure = Heal.heal(player);
        reply(player, failure == null ? "Healed" : failure, failure == null);
    }

    private static void reply(PlayerEntity player, String text, boolean ok) {
        PacketHelper.sendTo(player, new StatusS2CPacket(DevStatus.HEAL, text, ok));
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public @NotNull PacketType<HealC2SPacket> getType() {
        return TYPE;
    }
}
