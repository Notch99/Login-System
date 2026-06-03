package com.example.loginsystem.mixin;

import com.example.loginsystem.callback.DropItemCallback;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerEntityMixin {

    @Inject(method = "dropItem(Lnet.minecraft.world.item.ItemStack;ZZ)Lnet.minecraft.world.entity.item.ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void onDropItem(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
        Player player = (Player) (Object) this;
        
        InteractionResult result = DropItemCallback.EVENT.invoker().interact(player, stack);
        
        if (result == InteractionResult.FAIL) {
            // Cancel the drop and return null (item stays in inventory)
            cir.setReturnValue(null);
        }
    }
}


