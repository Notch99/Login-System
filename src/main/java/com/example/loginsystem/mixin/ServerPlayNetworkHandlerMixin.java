package com.example.loginsystem.mixin;

import com.example.loginsystem.callback.DropItemCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {

    @Shadow public ServerPlayerEntity player;

    @Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
    private void onPlayerAction(PlayerActionC2SPacket packet, CallbackInfo ci) {
        if (packet.getAction() == PlayerActionC2SPacket.Action.DROP_ITEM || packet.getAction() == PlayerActionC2SPacket.Action.DROP_ALL_ITEMS) {
            boolean result = DropItemCallback.EVENT.invoker().interact(player, player.getMainHandStack());
            if (result) {
                ci.cancel();
                // Send update to fix desync
                if (com.example.loginsystem.LoginSystem.serverInstance != null) {
                    com.example.loginsystem.LoginSystem.serverInstance.execute(() -> {
                        player.playerScreenHandler.updateToClient();
                        if (player.currentScreenHandler != player.playerScreenHandler) {
                            player.currentScreenHandler.updateToClient();
                        }
                    });
                }
            }
        }
    }

    @Inject(method = "onClickSlot", at = @At("HEAD"), cancellable = true)
    private void onClickSlot(ClickSlotC2SPacket packet, CallbackInfo ci) {
        // Prevent dropping via GUI (Q key over slot, or clicking outside with cursor item)
        if (packet.getActionType() == SlotActionType.THROW || (packet.getActionType() == SlotActionType.PICKUP && packet.getSlot() == -999)) {
            boolean result = DropItemCallback.EVENT.invoker().interact(player, ItemStack.EMPTY);
            if (result) {
                ci.cancel();
                // Send update to fix desync
                if (com.example.loginsystem.LoginSystem.serverInstance != null) {
                    com.example.loginsystem.LoginSystem.serverInstance.execute(() -> {
                        player.currentScreenHandler.updateToClient();
                        if (player.playerScreenHandler != player.currentScreenHandler) {
                            player.playerScreenHandler.updateToClient();
                        }
                    });
                }
            }
        }
    }
}
