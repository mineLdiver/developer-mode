package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.net.Ops;
import net.mine_diver.developermode.feature.world.Weather;
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
 * A request for a kind of weather in the sender's world.
 *
 * <p>A grant is answered without words, since the choice lighting up in the
 * menu is the answer. The text is only ever a reason it did not.
 */
public class WeatherC2SPacket extends Packet implements ManagedPacket<WeatherC2SPacket> {
    public static final PacketType<WeatherC2SPacket> TYPE =
            PacketType.builder(false, true, WeatherC2SPacket::new).build();

    public byte weather;


    public WeatherC2SPacket() {}

    public WeatherC2SPacket(byte weather) {
        this.weather = weather;
    }

    @Override
    public void read(DataInputStream in) {
        try {
            weather = in.readByte();
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void write(DataOutputStream out) {
        try {
            out.writeByte(weather);
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

        String failure = Weather.set(player.world, weather);
        reply(player, failure == null ? "" : failure, failure == null);
    }

    private static void reply(PlayerEntity player, String text, boolean ok) {
        PacketHelper.sendTo(player, new StatusS2CPacket(DevStatus.WEATHER, text, ok));
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public @NotNull PacketType<WeatherC2SPacket> getType() {
        return TYPE;
    }
}
