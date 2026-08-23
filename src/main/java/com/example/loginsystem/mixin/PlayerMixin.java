package com.example.loginsystem.mixin;

import com.example.loginsystem.LoginSystem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

@Mixin(ServerPlayer.class)
public class PlayerMixin {

    @Inject(method = {"drop", "method_7336", "dropItem"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void onDropItem(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
        ServerPlayer serverPlayer = (ServerPlayer) (Object) this;
        
        if (!serverPlayer.level().isClientSide()) {
            UUID playerId = serverPlayer.getUUID();
            if (!LoginSystem.isLoggedInPlayer(playerId)) {
                if (stack != null && !stack.isEmpty()) {
                    ItemStack copy = stack.copy();
                    boolean added = serverPlayer.getInventory().add(copy);
                    serverPlayer.getInventory().setChanged();
                    serverPlayer.containerMenu.sendAllDataToRemote();
                    
                    if (added) {
                        String msg = LoginSystem.instance.getLanguageManager().getMessage(playerId, "restrict.drop");
                        serverPlayer.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.YELLOW));
                        LoginSystem.instance.showActionBar(serverPlayer, msg);
                    } else {
                        serverPlayer.sendSystemMessage(
                            Component.literal("Your inventory is full, so the item couldn't be returned.")
                                .withStyle(ChatFormatting.RED));
                    }
                }
                
                // Cancel the drop and return null to prevent item entity creation
                cir.setReturnValue(null);
            }
        }
    }
}
