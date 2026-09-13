package net.mine_diver.developermode.feature;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * Puts a stack in the player's inventory.
 *
 * <p>Beta has no creative mode and no packet that lets a client conjure items,
 * so on a server this needs a mod side counterpart to actually take effect.
 * Until that exists, multiplayer is reported as unsupported rather than
 * silently handing out items that the server rolls straight back.
 */
public final class Give {
    public enum Result {
        GIVEN,
        INVENTORY_FULL,
        NEEDS_SERVER,
        NO_WORLD;

        public String message() {
            switch (this) {
                case GIVEN: return "Given";
                case INVENTORY_FULL: return "Inventory full";
                case NEEDS_SERVER: return "Server side giving is not implemented yet";
                default: return "Not in a world";
            }
        }
    }

    private Give() {}

    public static Result give(ItemStack stack) {
        Minecraft minecraft = Minecraft.INSTANCE;
        if (minecraft == null || minecraft.world == null) return Result.NO_WORLD;

        PlayerEntity player = minecraft.player;
        if (player == null) return Result.NO_WORLD;

        if (minecraft.isWorldRemote()) return Result.NEEDS_SERVER;

        return player.inventory.addStack(stack.copy()) ? Result.GIVEN : Result.INVENTORY_FULL;
    }
}
