package net.mine_diver.developermode;

import net.mine_diver.unsafeevents.listener.EventListener;
import net.modificationstation.stationapi.api.event.mod.InitEvent;
import net.modificationstation.stationapi.api.mod.entrypoint.EntrypointManager;
import net.modificationstation.stationapi.api.util.Namespace;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandles;

/**
 * Shared mod handle. Everything client side lives under
 * {@link net.mine_diver.developermode.client}.
 *
 * <p>Entry point classes are instantiated by the loader, so this needs to keep
 * its implicit public constructor.
 */
public final class DeveloperMode {
    static {
        EntrypointManager.registerLookup(MethodHandles.lookup());
    }

    @SuppressWarnings("UnstableApiUsage")
    public static final Namespace NAMESPACE = Namespace.resolve();

    public static final Logger LOGGER = NAMESPACE.getLogger();

    @EventListener
    private static void init(InitEvent event) {
        LOGGER.info("Developer Mode loaded.");
    }
}
