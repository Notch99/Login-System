package com.example.loginsystem.callback;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;

/**
 * Callback for when a player attempts to drop an item.
 * Return ActionResult.FAIL to cancel the drop.
 * Return ActionResult.PASS to allow normal processing.
 */
public interface DropItemCallback {
    Event<DropItemCallback> EVENT = EventFactory.createArrayBacked(DropItemCallback.class,
            (listeners) -> (player, stack) -> {
                for (DropItemCallback listener : listeners) {
                    ActionResult result = listener.interact(player, stack);
                    if (result != ActionResult.PASS) {
                        return result;
                    }
                }
                return ActionResult.PASS;
            });

    ActionResult interact(PlayerEntity player, ItemStack stack);
}
