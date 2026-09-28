package net.mine_diver.developermode.mixin;

import net.mine_diver.developermode.feature.world.LockedSky;
import net.mine_diver.developermode.feature.world.LockingWorld;
import net.mine_diver.developermode.feature.world.Locks;
import net.minecraft.world.World;
import net.minecraft.world.WorldProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * What is locked in a world, and the two places it is carried out.
 *
 * <p>The sun is locked where Beta turns the clock into a position in the sky.
 * Everything that cares what time of day it is asks there rather than reading
 * the clock: the sky and the celestial bodies, the light, the mobs that spawn
 * by it, and whether it is night enough to sleep. The clock itself is left
 * running for everything that only cares how long it has been.
 *
 * <p>The weather is locked at the top of its tick, before the counts that
 * would change it are looked at. A server's own weather tick calls this one
 * before telling its players about a change, so locked weather also never
 * tells them anything.
 *
 * @see Locks
 */
@Mixin(World.class)
abstract class WorldLocksMixin implements LockingWorld {
    @Shadow
    protected WorldProperties properties;

    @Unique
    private LockedSky developermode_locks;

    @Override
    public LockedSky developermode_locks() {
        return developermode_locks;
    }

    @Override
    public void developermode_locks(LockedSky locks) {
        developermode_locks = locks;
    }

    @Inject(method = "getTime(F)F", at = @At("HEAD"), cancellable = true)
    private void developermode_lockSun(float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (developermode_locks == null || developermode_locks.time() == Locks.NONE) return;
        // No partial tick: the point is that it does not move between them.
        cir.setReturnValue(((World) (Object) this).dimension.getTimeOfDay(developermode_locks.time(), 0));
    }

    @Inject(method = "updateWeatherCycles()V", at = @At("HEAD"))
    private void developermode_lockWeather(CallbackInfo ci) {
        Locks.keepWeather((World) (Object) this, properties);
    }
}
