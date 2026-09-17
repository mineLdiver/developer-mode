package net.mine_diver.developermode.client;

import net.mine_diver.developermode.client.gui.radial.RadialMenu;
import net.mine_diver.developermode.client.gui.radial.RadialScreen;
import net.mine_diver.developermode.client.inspect.InspectScreen;
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

    /** Held to summon the radial menu. Rebindable from the vanilla controls screen. */
    public static final KeyBinding MENU_KEY = new KeyBinding("key.developermode.menu", Keyboard.KEY_GRAVE);

    /**
     * Held to get a pointer and inspect what is in front of you. The way in.
     *
     * <p>Control, because holding it means "modify what I am doing", which is
     * what it does. Unbound in Beta, and unlike the obvious choice of Alt it is
     * not stolen by window managers that use Alt plus click to drag windows,
     * which matters for a mode that is driven by clicking. Rebindable.
     */
    public static final KeyBinding INSPECT_KEY =
            new KeyBinding("key.developermode.inspect", Keyboard.KEY_LCONTROL);

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
        event.register(MENU_KEY);
        event.register(INSPECT_KEY);
        RadialMenu.bootstrap();
    }

    /**
     * Opens the radial from the world. Opening it from inside the composer is
     * handled there, since a screen gets its own key events.
     */
    @EventListener
    private static void keyStateChanged(KeyStateChangedEvent event) {
        if (event.environment != KeyStateChangedEvent.Environment.IN_GAME) return;
        if (!Keyboard.getEventKeyState() || Keyboard.getEventKey() != MENU_KEY.code) return;

        Minecraft minecraft = minecraft();
        if (minecraft == null || minecraft.world == null || minecraft.player == null) return;

        RadialScreen.open(null);
    }

    /**
     * Opens the pointer state from the world.
     *
     * <p>The screen it opens watches the key itself, so holding and letting go
     * are one gesture rather than two events that could get out of step.
     */
    @EventListener
    private static void inspectKeyChanged(KeyStateChangedEvent event) {
        if (event.environment != KeyStateChangedEvent.Environment.IN_GAME) return;
        if (!Keyboard.getEventKeyState() || Keyboard.getEventKey() != INSPECT_KEY.code) return;

        Minecraft minecraft = minecraft();
        if (minecraft == null || minecraft.world == null || minecraft.player == null) return;

        InspectScreen.open();
    }
}
