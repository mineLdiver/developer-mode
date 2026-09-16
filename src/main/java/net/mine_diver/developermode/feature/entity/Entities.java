package net.mine_diver.developermode.feature.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

/**
 * Naming entities, and finding them again.
 */
public final class Entities {
    private Entities() {}

    /**
     * The entity with this id, or null.
     *
     * <p>Beta keeps no id to entity map, so this is a scan. Ids are assigned by
     * whoever owns the world and sent to clients with the spawn packet, so the
     * same number means the same entity on both sides.
     */
    public static Entity byId(World world, int id) {
        if (world == null) return null;
        for (Object candidate : world.entities) {
            Entity entity = (Entity) candidate;
            if (entity.id == id) return entity;
        }
        return null;
    }

    public static String name(Entity entity) {
        if (entity == null) return "nothing";
        if (entity instanceof PlayerEntity player) return player.name;

        // Players and a few internals are not in the registry at all, so fall
        // back to the class name rather than showing a blank row.
        String id = EntityRegistry.getId(entity);
        return id == null || id.isEmpty() ? entity.getClass().getSimpleName() : id;
    }
}
