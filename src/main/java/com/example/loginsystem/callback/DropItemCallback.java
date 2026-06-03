package com.example.loginsystem.callback;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;

/**
 * Callback for when a player attempts to drop an item.
 * Return InteractionResult.FAIL to cancel the drop.
 * Return InteractionResult.PASS to allow normal processing.
 */
public interface DropItemCallback {
    Event<DropItemCallback> EVENT = EventFactory.createArrayBacked(DropItemCallback.class,
            (listeners) -> (player, stack) -> {
                for (DropItemCallback listener : listeners) {
                    InteractionResult result = listener.interact(player, stack);
                    if (result != InteractionResult.PASS) {
                        return result;
                    }
                }
                return InteractionResult.PASS;
            });

    InteractionResult interact(Player player, ItemStack stack);
}
