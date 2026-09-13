package net.mine_diver.developermode.mixin;

import net.mine_diver.developermode.feature.entity.FrozenEntities;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses ticking for frozen entities, so an entity being edited does not
 * wander off and leave its coordinates stale.
 *
 * <p>This is the whole tick for that entity: no movement, no AI, no despawn,
 * and no chunk reassignment, which is fine precisely because it is not moving.
 */
@Mixin(World.class)
class WorldMixin {
    @Inject(method = "updateEntity(Lnet/minecraft/entity/Entity;Z)V", at = @At("HEAD"), cancellable = true)
    private void developermode_holdFrozenEntitiesStill(Entity entity, boolean requireLoaded, CallbackInfo ci) {
        if (FrozenEntities.isFrozen(entity)) ci.cancel();
    }
}
