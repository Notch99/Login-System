package com.example.loginsystem.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginPacketListenerMixin {

    @Shadow @Final private MinecraftServer server;
    @Shadow public abstract void disconnect(Component reason);

    @Inject(method = "verifyLoginAndFinishConnectionSetup", at = @At("HEAD"), cancellable = true)
    private void onVerifyLogin(GameProfile profile, CallbackInfo ci) {
        if (profile != null) {
            String name = null;
            try {
                // Try old Authlib method
                name = (String) profile.getClass().getMethod("getName").invoke(profile);
            } catch (Exception e) {
                try {
                    // Try new Authlib method (if GameProfile became a record)
                    name = (String) profile.getClass().getMethod("name").invoke(profile);
                } catch (Exception ex) {}
            }

            if (name != null) {
                if (this.server.getPlayerList().getPlayerByName(name) != null) {
                    this.disconnect(Component.literal("A player with this name is already online!"));
                    ci.cancel();
                }
            }
        }
    }
}
