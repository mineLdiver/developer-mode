package net.mine_diver.developermode.feature.entity;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityRegistry;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.entity.decoration.painting.PaintingVariants;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Listing entity types and putting new ones in the world.
 */
public final class EntitySummoning {
    private EntitySummoning() {}

    /**
     * Every registered entity id that can actually be built, sorted.
     *
     * <p>Read straight off {@code EntityRegistry}'s private table, since Beta
     * exposes no way to enumerate it. Mods register through StationAPI into
     * that same map, so they show up here without any extra work.
     *
     * <p>Registry entries that cannot be instantiated are dropped rather than
     * listed and then failing. "Mob" is the vanilla example: it maps to the
     * abstract LivingEntity, and asking Beta to construct it only earns a stack
     * trace on stdout.
     */
    @SuppressWarnings("unchecked")
    public static List<String> types() {
        Map<String, Class<? extends Entity>> registry =
                (Map<String, Class<? extends Entity>>) EntityRegistry.idToClass;

        List<String> ids = new ArrayList<>();
        for (Map.Entry<String, Class<? extends Entity>> entry : registry.entrySet()) {
            if (isSummonable(entry.getValue())) ids.add(entry.getKey());
        }
        Collections.sort(ids);
        return ids;
    }

    /** Mirrors exactly what {@code EntityRegistry.create} needs to succeed. */
    private static boolean isSummonable(Class<? extends Entity> type) {
        if (type == null || Modifier.isAbstract(type.getModifiers())) return false;
        try {
            type.getConstructor(World.class);
            return true;
        } catch (NoSuchMethodException missing) {
            return false;
        }
    }

    /**
     * Builds an entity without putting it in the world. Used for previews.
     *
     * @return null if the type has no usable {@code (World)} constructor
     */
    public static Entity create(String id, World world) {
        try {
            Entity entity = EntityRegistry.create(id, world);
            if (entity != null) fillRequiredState(entity);
            return entity;
        } catch (Throwable error) {
            return null;
        }
    }

    /**
     * Supplies the state Beta expects something else to have set.
     *
     * <p>{@code EntityRegistry.create} is really only the first half of the NBT
     * load path: vanilla calls {@code read} immediately after it, and that is
     * where these fields normally come from. Constructed on its own, a few
     * entities come back in a state their own renderer dereferences straight
     * through, which is a crash rather than a blank preview.
     */
    private static void fillRequiredState(Entity entity) {
        if (entity instanceof ItemEntity item) {
            if (item.stack == null) item.stack = new ItemStack(Block.STONE);
        } else if (entity instanceof FallingBlockEntity falling) {
            if (falling.blockId == 0) falling.blockId = Block.SAND.id;
        } else if (entity instanceof PaintingEntity painting) {
            if (painting.variant == null) painting.variant = PaintingVariants.KEBAB;
        }
    }

    /**
     * @return null on success, or a message saying why nothing was summoned
     */
    public static String summon(String id, double x, double feetY, double z, float yaw) {
        Minecraft minecraft = Minecraft.INSTANCE;
        if (minecraft == null || minecraft.world == null) return "Not in a world";
        if (minecraft.isWorldRemote()) return "Server side summoning is not implemented yet";

        Entity entity = create(id, minecraft.world);
        if (entity == null) return "Could not build a " + id;

        place(entity, x, feetY, z, yaw);

        return minecraft.world.spawnEntity(entity) ? null : "The world refused it, is that chunk loaded?";
    }

    /**
     * Positions an entity so its feet sit at {@code feetY}, with every
     * interpolation field collapsed onto the new position.
     *
     * <p>Beta's bounding box hangs off {@code y} minus {@code standingEyeHeight},
     * and a fresh entity's previous position is still at the origin, so without
     * both of these a summon renders streaking in from world zero.
     */
    public static void place(Entity entity, double x, double feetY, double z, float yaw) {
        entity.setPosition(x, feetY + entity.standingEyeHeight, z);

        // Paintings hang off block coordinates rather than their own position,
        // and would otherwise stay anchored at the world origin and tear
        // themselves down on the first tick. Placement is still approximate:
        // they are built to be attached to a wall, not dropped at a point.
        if (entity instanceof PaintingEntity painting) {
            painting.attachmentX = MathHelper.floor(x);
            painting.attachmentY = MathHelper.floor(feetY);
            painting.attachmentZ = MathHelper.floor(z);
        }

        entity.lastTickX = entity.prevX = entity.x;
        entity.lastTickY = entity.prevY = entity.y;
        entity.lastTickZ = entity.prevZ = entity.z;

        entity.yaw = entity.prevYaw = yaw;
        entity.pitch = entity.prevPitch = 0;
        if (entity instanceof LivingEntity living) {
            living.bodyYaw = living.lastBodyYaw = yaw;
        }
    }
}
