package net.mine_diver.developermode.feature.net;

import net.mine_diver.developermode.DeveloperMode;
import net.mine_diver.developermode.feature.net.packet.GiveC2SPacket;
import net.mine_diver.developermode.feature.net.packet.StatusS2CPacket;
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
        event.register(DeveloperMode.NAMESPACE.id("status"), StatusS2CPacket.TYPE);
    }
}
