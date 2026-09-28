package net.mine_diver.developermode.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.mine_diver.developermode.feature.player.Power;
import net.mine_diver.developermode.feature.player.Powers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Using a held item on an entity without using it up.
 *
 * <p>Here it is the entity that decides what the use costs, and it takes it
 * straight out of the player's hand: a saddle put on a pig, a bone fed to a
 * wolf, coal put in a minecart, a bucket swapped for a bucket of milk. There
 * is no stack method in between to put right afterwards, so the hand itself is
 * put back to the stack that was in it, as it was.
 *
 * <p>Both sides run this with the same answer, like the uses on a block and in
 * the air in {@link ItemStackMixin}.
 */
@Mixin(PlayerEntity.class)
abstract class PlayerInteractMixin {
    @Shadow
    public PlayerInventory inventory;

    @WrapMethod(method = "interact(Lnet/minecraft/entity/Entity;)V")
    private void developermode_keepInteracting(Entity entity, Operation<Void> original) {
        ItemStack held = inventory.getSelectedItem();
        if (held == null || !Powers.has((PlayerEntity) (Object) this, Power.ENDLESS)) {
            original.call(entity);
            return;
        }

        int slot = inventory.selectedSlot;
        int count = held.count;
        int damage = held.getDamage();
        original.call(entity);
        held.count = count;
        held.setDamage(damage);
        inventory.main[slot] = held;
    }
}
