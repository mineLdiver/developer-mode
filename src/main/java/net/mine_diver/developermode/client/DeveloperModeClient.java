package net.mine_diver.developermode.client;

import net.mine_diver.developermode.client.gui.radial.RadialMenu;
import net.mine_diver.developermode.client.gui.radial.RadialScreen;
import net.mine_diver.developermode.feature.entity.EntityTargeting;
import net.mine_diver.unsafeevents.listener.EventListener;
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
     * Held to inspect entities in the world.
     *
     * <p>Tab, because it is under the left hand, unbound in Beta, and unlike
     * the obvious choice of Alt it is not stolen by window managers that use
     * Alt plus click to drag windows. Rebindable, and entirely skippable: the
     * radial's Inspect slot does the same thing.
     */
    public static final KeyBinding INSPECT_KEY = new KeyBinding("key.developermode.inspect", Keyboard.KEY_TAB);

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

        Minecraft minecraft = Minecraft.INSTANCE;
        if (minecraft == null || minecraft.world == null || minecraft.player == null) return;

        // Last chance to see where the crosshair is pointing: the screen that
        // is about to open takes the mouse, and aiming stops being possible.
        EntityTargeting.capture();
        RadialScreen.open(null);
    }
}
