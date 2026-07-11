package com.example.loginsystem.callback;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * Callback for when a player attempts to drop an item.
 * Return true to cancel the drop.
 * Return false to allow normal processing.
 */
public interface DropItemCallback {
    Event<DropItemCallback> EVENT = EventFactory.createArrayBacked(DropItemCallback.class,
            (listeners) -> (player, stack) -> {
                for (DropItemCallback listener : listeners) {
                    boolean result = listener.interact(player, stack);
                    if (result) {
                        return true;
                    }
                }
                return false;
            });

    boolean interact(PlayerEntity player, ItemStack stack);
}
