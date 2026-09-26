package net.mine_diver.developermode.feature.net;

import net.mine_diver.developermode.DeveloperMode;
import net.mine_diver.developermode.feature.net.packet.ApplyNbtC2SPacket;
import net.mine_diver.developermode.feature.net.packet.BlastC2SPacket;
import net.mine_diver.developermode.feature.net.packet.ClearC2SPacket;
import net.mine_diver.developermode.feature.net.packet.NbtS2CPacket;
import net.mine_diver.developermode.feature.net.packet.FreezeC2SPacket;
import net.mine_diver.developermode.feature.net.packet.FrozenS2CPacket;
import net.mine_diver.developermode.feature.net.packet.GiveC2SPacket;
import net.mine_diver.developermode.feature.net.packet.GrowC2SPacket;
import net.mine_diver.developermode.feature.net.packet.HealC2SPacket;
import net.mine_diver.developermode.feature.net.packet.PowersC2SPacket;
import net.mine_diver.developermode.feature.net.packet.PowersS2CPacket;
import net.mine_diver.developermode.feature.net.packet.RequestNbtC2SPacket;
import net.mine_diver.developermode.feature.net.packet.SmiteC2SPacket;
import net.mine_diver.developermode.feature.net.packet.StatusS2CPacket;
import net.mine_diver.developermode.feature.net.packet.SummonC2SPacket;
import net.mine_diver.developermode.feature.net.packet.TimeC2SPacket;
import net.mine_diver.developermode.feature.net.packet.WarpC2SPacket;
import net.mine_diver.developermode.feature.net.packet.WeatherC2SPacket;
import net.mine_diver.unsafeevents.listener.EventListener;
import net.modificationstation.stationapi.api.event.network.packet.PacketRegisterEvent;
import net.modificationstation.stationapi.api.mod.entrypoint.EntrypointManager;

import java.lang.invoke.MethodHandles;

/**
 * Every packet the tools speak, named and registered.
 *
 * <p>Instantiated by the loader, so this keeps its implicit public constructor.
 */
public final class DevPackets {
    static {
        EntrypointManager.registerLookup(MethodHandles.lookup());
    }

    @EventListener
    private static void registerPackets(PacketRegisterEvent event) {
        event.register(DeveloperMode.NAMESPACE.id("give"), GiveC2SPacket.TYPE);
        event.register(DeveloperMode.NAMESPACE.id("summon"), SummonC2SPacket.TYPE);
        event.register(DeveloperMode.NAMESPACE.id("request_nbt"), RequestNbtC2SPacket.TYPE);
        event.register(DeveloperMode.NAMESPACE.id("apply_nbt"), ApplyNbtC2SPacket.TYPE);
        event.register(DeveloperMode.NAMESPACE.id("nbt"), NbtS2CPacket.TYPE);
        event.register(DeveloperMode.NAMESPACE.id("freeze"), FreezeC2SPacket.TYPE);
        event.register(DeveloperMode.NAMESPACE.id("frozen"), FrozenS2CPacket.TYPE);
        event.register(DeveloperMode.NAMESPACE.id("status"), StatusS2CPacket.TYPE);
        event.register(DeveloperMode.NAMESPACE.id("powers"), PowersC2SPacket.TYPE);
        event.register(DeveloperMode.NAMESPACE.id("granted"), PowersS2CPacket.TYPE);
        event.register(DeveloperMode.NAMESPACE.id("heal"), HealC2SPacket.TYPE);
        event.register(DeveloperMode.NAMESPACE.id("warp"), WarpC2SPacket.TYPE);
        event.register(DeveloperMode.NAMESPACE.id("time"), TimeC2SPacket.TYPE);
        event.register(DeveloperMode.NAMESPACE.id("weather"), WeatherC2SPacket.TYPE);
        event.register(DeveloperMode.NAMESPACE.id("grow"), GrowC2SPacket.TYPE);
        event.register(DeveloperMode.NAMESPACE.id("blast"), BlastC2SPacket.TYPE);
        event.register(DeveloperMode.NAMESPACE.id("smite"), SmiteC2SPacket.TYPE);
        event.register(DeveloperMode.NAMESPACE.id("clear"), ClearC2SPacket.TYPE);
    }
}
