package net.mine_diver.developermode.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The two pieces of an entity's condition that Beta keeps to itself.
 *
 * <p>Both are wanted by code that puts a player back in one piece, and neither
 * is reachable from outside the entity hierarchy. How long a breath lasts is
 * the entity's own answer rather than a constant, so a mod that lengthens it
 * is still healed correctly.
 */
@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("maxAir")
    int getMaxAir();

    @Accessor("fallDistance")
    void setFallDistance(float fallDistance);
}
