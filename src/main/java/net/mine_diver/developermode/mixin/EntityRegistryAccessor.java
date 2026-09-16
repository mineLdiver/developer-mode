package net.mine_diver.developermode.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * The entity type table.
 *
 * <p>Beta offers no way to list what is registered, which a summon picker
 * needs. StationAPI mods register into this same map, so reading it picks up
 * modded entities too.
 *
 * <p>Typed here rather than at the call site: the field itself is a raw Map,
 * and this is the one place that knows what is in it.
 */
@Mixin(EntityRegistry.class)
public interface EntityRegistryAccessor {
    @Accessor("idToClass")
    static Map<String, Class<? extends Entity>> getIdToClass() {
        throw new AssertionError();
    }
}
