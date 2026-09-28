package net.mine_diver.developermode.api.setting;

import com.mojang.serialization.Lifecycle;
import net.mine_diver.developermode.DeveloperMode;
import net.modificationstation.stationapi.api.registry.Registries;
import net.modificationstation.stationapi.api.registry.RegistryKey;
import net.modificationstation.stationapi.api.registry.SimpleRegistry;

/**
 * Every {@link Setting} there is, in the order they were registered, which is
 * the order they are offered in.
 *
 * <p>Filled once, from a
 * {@link net.mine_diver.developermode.api.event.setting.SettingRegistryEvent},
 * with Developer Mode's own settings first.
 *
 * <p>Not synced. A setting is sent by its identifier and looked up again on
 * arrival, so neither side has to agree on numbers, only on names, and a
 * setting the other side does not have is refused by name rather than
 * mistaken for another one.
 */
public final class SettingRegistry extends SimpleRegistry<Setting> {
    public static final RegistryKey<SettingRegistry> KEY =
            RegistryKey.ofRegistry(DeveloperMode.NAMESPACE.id("settings"));
    public static final SettingRegistry INSTANCE =
            Registries.create(KEY, new SettingRegistry(), Lifecycle.experimental());

    private SettingRegistry() {
        super(KEY, Lifecycle.experimental(), false);
    }
}
