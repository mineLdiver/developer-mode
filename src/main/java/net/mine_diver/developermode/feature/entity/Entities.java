package net.mine_diver.developermode.feature.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityRegistry;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Naming entities for display.
 */
public final class Entities {
    private Entities() {}

    public static String name(Entity entity) {
        if (entity == null) return "nothing";
        if (entity instanceof PlayerEntity player) return player.name;

        // Players and a few internals are not in the registry at all, so fall
        // back to the class name rather than showing a blank row.
        String id = EntityRegistry.getId(entity);
        return id == null || id.isEmpty() ? entity.getClass().getSimpleName() : id;
    }
}
