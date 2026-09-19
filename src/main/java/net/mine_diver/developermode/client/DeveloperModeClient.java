package net.mine_diver.developermode.client;

import net.mine_diver.developermode.client.gui.radial.RadialMenu;
import net.mine_diver.developermode.client.gui.radial.RadialScreen;
import net.mine_diver.unsafeevents.listener.EventListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.option.KeyBinding;
import net.modificationstation.stationapi.api.client.event.keyboard.KeyStateChangedEvent;
import net.modificationstation.stationapi.api.client.event.option.KeyBindingRegisterEvent;
import net.modificationstation.stationapi.api.mod.entrypoint.EntrypointManager;
import org.lwjgl.input.Keyboard;

import java.lang.invoke.MethodHandles;

/**
 * Client entry point. Owns the one key that opens everything.
 *
 * <p>Instantiated by the loader, so it keeps its implicit public constructor.
 */
public final class DeveloperModeClient {
    static {
        EntrypointManager.registerLookup(MethodHandles.lookup());
    }

    /**
     * The one key the mod has. Held, not tapped: everything the mod does
     * happens inside one press, and letting go puts you back in the game.
     *
     * <p>Control, because holding it means "modify what I am doing", which is
     * what it does. Unbound in Beta, and unlike the obvious choice of Alt it is
     * not stolen by window managers that use Alt plus click to drag windows,
     * which matters for a mode driven by clicking. Rebindable.
     */
    public static final KeyBinding OPEN_KEY =
            new KeyBinding("key.developermode.open", Keyboard.KEY_LCONTROL);

    /**
     * The running client.
     *
     * <p>Null until the game has built it, so anything that can run before then
     * has to check. Once a screen is up it cannot be null, and the call sites
     * inside one do not check.
     *
     * <p>The loader marks its game instance experimental rather than pointing
     * anywhere else, so the warning is answered here, at the one call, instead
     * of at each of the places that want the client.
     */
    @SuppressWarnings("deprecation")
    public static Minecraft minecraft() {
        return (Minecraft) FabricLoader.getInstance().getGameInstance();
    }

    @EventListener
    private static void registerKeyBindings(KeyBindingRegisterEvent event) {
        event.register(OPEN_KEY);
        RadialMenu.bootstrap();
    }

    /**
     * Opens the ring from the world.
     *
     * <p>The screen it opens watches the key itself, so holding and letting go
     * are one gesture rather than two events that could get out of step.
     */
    @EventListener
    private static void openKeyChanged(KeyStateChangedEvent event) {
        if (event.environment != KeyStateChangedEvent.Environment.IN_GAME) return;
        if (!Keyboard.getEventKeyState() || Keyboard.getEventKey() != OPEN_KEY.code) return;

        Minecraft minecraft = minecraft();
        if (minecraft == null || minecraft.world == null || minecraft.player == null) return;

        RadialScreen.open(null);
    }
}
