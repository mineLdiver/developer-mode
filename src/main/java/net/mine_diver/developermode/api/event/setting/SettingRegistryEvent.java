package net.mine_diver.developermode.api.event.setting;

import net.mine_diver.developermode.api.setting.Setting;
import net.mine_diver.developermode.api.setting.SettingRegistry;
import net.modificationstation.stationapi.api.event.registry.RegistryEvent;

/**
 * Where settings are registered.
 *
 * <p>Posted on both sides, once every block and item is registered, so a
 * setting can refer to either. Developer Mode's own settings are in the
 * registry by the time it is posted.
 */
public class SettingRegistryEvent extends RegistryEvent.EntryTypeBound<Setting, SettingRegistry> {
    public SettingRegistryEvent() {
        super(SettingRegistry.INSTANCE);
    }
}
