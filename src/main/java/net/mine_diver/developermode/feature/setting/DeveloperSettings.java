package net.mine_diver.developermode.feature.setting;

import net.mine_diver.developermode.DeveloperMode;
import net.mine_diver.developermode.api.event.setting.SettingRegistryEvent;
import net.mine_diver.developermode.api.setting.Setting;
import net.mine_diver.developermode.api.setting.SettingRegistry;
import net.mine_diver.developermode.api.setting.SwitchSetting;
import net.mine_diver.developermode.feature.player.Power;
import net.mine_diver.unsafeevents.listener.EventListener;
import net.modificationstation.stationapi.api.StationAPI;
import net.modificationstation.stationapi.api.event.registry.AfterBlockAndItemRegisterEvent;
import net.modificationstation.stationapi.api.mod.entrypoint.EntrypointManager;
import net.modificationstation.stationapi.api.registry.Registry;

import java.lang.invoke.MethodHandles;

/**
 * Developer Mode's own settings, and the event everybody else registers
 * theirs in.
 *
 * <p>These are registered just before the event is posted rather than from a
 * listener to it, so they come first in the registry whatever order the
 * listeners run in, and so first in every list of settings.
 *
 * <p>Instantiated by the loader, so this keeps its implicit public constructor.
 */
public final class DeveloperSettings {
    static {
        EntrypointManager.registerLookup(MethodHandles.lookup());
    }

    public static final SwitchSetting GOD = new PowerSetting(Power.GOD);
    public static final SwitchSetting FLIGHT = new PowerSetting(Power.FLIGHT);
    public static final SwitchSetting NOCLIP = new PowerSetting(Power.NOCLIP);
    public static final SwitchSetting INSTANT_BREAK = new PowerSetting(Power.INSTANT_BREAK);
    public static final SwitchSetting ENDLESS = new PowerSetting(Power.ENDLESS);
    public static final Setting TIME = new TimeSetting();
    public static final Setting WEATHER = new WeatherSetting();

    @EventListener
    private static void registerSettings(AfterBlockAndItemRegisterEvent event) {
        Registry.register(SettingRegistry.INSTANCE, DeveloperMode.NAMESPACE)
                .accept("god", GOD)
                .accept("flight", FLIGHT)
                .accept("noclip", NOCLIP)
                .accept("insta_break", INSTANT_BREAK)
                .accept("endless", ENDLESS)
                .accept("time", TIME)
                .accept("weather", WEATHER);
        StationAPI.EVENT_BUS.post(new SettingRegistryEvent());
    }
}
