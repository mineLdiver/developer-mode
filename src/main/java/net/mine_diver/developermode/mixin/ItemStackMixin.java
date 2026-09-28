package net.mine_diver.developermode.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.mine_diver.developermode.feature.player.Power;
import net.mine_diver.developermode.feature.player.Powers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Using an item without using it up.
 *
 * <p>Every item decides for itself how much of a stack a use costs, and each
 * one takes it off in its own code: a block when it is placed, a snowball when
 * it is thrown, a bite when it is eaten. So the stack is not asked to spend
 * less. It is put back afterwards to exactly what it was, around the two doors
 * a held item is used through, on a block and in the air, which covers every
 * item that spends itself there, including ones this mod has never heard of.
 * Using one on an entity is the entity's code taking from the player's hand
 * rather than the stack's own, so that is put back on the player instead.
 *
 * <p>A use that hands back something else in its place, a bucket emptied or
 * filled or a bowl left over from soup, has the replacement refused as well.
 * What was in hand is what stays there, which is the whole of the promise: a
 * water bucket pours forever, and an empty one never fills.
 *
 * <p>Wear is a third door of its own, since tools wear from breaking blocks
 * and hitting things rather than from being used, and armor from being hit.
 * All of it goes through one method, so that is where it is refused.
 *
 * <p>Both sides run all three with the same answer, because a client and the
 * server it plays on each spend the stack on their own and then compare notes.
 */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {
    @Shadow
    public int count;

    @Shadow
    private int damage;

    @WrapMethod(method = "useOnBlock(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/world/World;IIII)Z")
    private boolean developermode_keepPlacing(PlayerEntity player, World world, int x, int y, int z, int side,
                                              Operation<Boolean> original) {
        if (!Powers.has(player, Power.ENDLESS)) return original.call(player, world, x, y, z, side);

        int count = this.count;
        int damage = this.damage;
        boolean used = original.call(player, world, x, y, z, side);
        this.count = count;
        this.damage = damage;
        return used;
    }

    /**
     * Answers with this same stack, restored, so whoever asked puts back what
     * was already there.
     */
    @WrapMethod(method = "use(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/item/ItemStack;")
    private ItemStack developermode_keepUsing(World world, PlayerEntity user, Operation<ItemStack> original) {
        if (!Powers.has(user, Power.ENDLESS)) return original.call(world, user);

        int count = this.count;
        int damage = this.damage;
        original.call(world, user);
        this.count = count;
        this.damage = damage;
        return (ItemStack) (Object) this;
    }

    @Inject(method = "damage(ILnet/minecraft/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void developermode_keepEdge(int amount, Entity entity, CallbackInfo ci) {
        if (entity instanceof PlayerEntity player && Powers.has(player, Power.ENDLESS)) ci.cancel();
    }
}
