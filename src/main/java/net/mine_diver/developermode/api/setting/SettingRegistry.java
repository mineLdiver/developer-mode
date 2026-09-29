package net.mine_diver.developermode.api.setting;

import com.mojang.serialization.Lifecycle;
import net.mine_diver.developermode.DeveloperMode;
import net.modificationstation.stationapi.api.event.registry.RegistryAttribute;
import net.modificationstation.stationapi.api.event.registry.RegistryAttributeHolder;
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
 * <p>Synced, so a setting goes over the wire by its raw ID. A client missing
 * a setting the server has cannot join, the way it cannot without a block
 * the server has, since a setting is read and offered on the client and
 * one it does not know is one nobody there can use. A setting only the
 * client has is given a raw ID past the server's, and refused when it is
 * asked for.
 */
public final class SettingRegistry extends SimpleRegistry<Setting<?>> {
    public static final RegistryKey<SettingRegistry> KEY =
            RegistryKey.ofRegistry(DeveloperMode.NAMESPACE.id("settings"));
    public static final SettingRegistry INSTANCE =
            Registries.create(KEY, new SettingRegistry(), Lifecycle.experimental());

    private SettingRegistry() {
        super(KEY, Lifecycle.experimental(), false);
        RegistryAttributeHolder.get(this).addAttribute(RegistryAttribute.SYNCED);
    }
}
