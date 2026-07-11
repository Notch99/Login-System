package com.example.loginsystem;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

/**
 * Cross-version permission checker using reflection.
 * Handles API differences between Minecraft 1.21.1 and 1.21.11+
 * where permission-checking methods may have been renamed or removed.
 */
public class PermissionHelper {
    private static final Logger LOGGER = LogManager.getLogger();

    // Cached reflection methods - resolved once at first use
    private static volatile boolean resolved = false;

    // For CommandSourceStack
    private static Method cssHasPermission = null;

    // For Player / ServerPlayer / Entity
    private static Method playerHasPermissions = null;

    // For PlayerList.isOp
    private static Method playerListIsOp = null;

    /**
     * Resolve all possible permission-checking methods via reflection.
     * Called once, results are cached.
     */
    private static synchronized void resolve() {
        if (resolved) return;
        resolved = true;

        // --- CommandSourceStack methods ---
        try {
            Class<?> cssClass = CommandSourceStack.class;
            // Try hasPermission(int) first
            for (Method m : cssClass.getMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == int.class
                        && m.getReturnType() == boolean.class
                        && (m.getName().equals("hasPermission") || m.getName().equals("hasPermissions"))) {
                    cssHasPermission = m;
                    LOGGER.info("[PermissionHelper] Found CSS method: {}", m.getName());
                    break;
                }
            }
        } catch (Throwable e) {
            LOGGER.warn("[PermissionHelper] Failed to resolve CommandSourceStack permission method", e);
        }

        // --- Player / Entity methods ---
        try {
            // Search through the class hierarchy for any permission-checking method
            Class<?> clazz = ServerPlayer.class;
            while (clazz != null && clazz != Object.class) {
                for (Method m : clazz.getDeclaredMethods()) {
                    if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == int.class
                            && m.getReturnType() == boolean.class
                            && (m.getName().equals("hasPermissions") || m.getName().equals("hasPermission"))) {
                        m.setAccessible(true);
                        playerHasPermissions = m;
                        LOGGER.info("[PermissionHelper] Found Player method: {} on {}", m.getName(), clazz.getSimpleName());
                        break;
                    }
                }
                if (playerHasPermissions != null) break;
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable e) {
            LOGGER.warn("[PermissionHelper] Failed to resolve Player permission method", e);
        }

        // --- PlayerList.isOp ---
        try {
            Class<?> plClass = net.minecraft.server.players.PlayerList.class;
            for (Method m : plClass.getMethods()) {
                if (m.getName().equals("isOp") && m.getParameterCount() == 1
                        && m.getReturnType() == boolean.class) {
                    playerListIsOp = m;
                    LOGGER.info("[PermissionHelper] Found PlayerList.isOp method");
                    break;
                }
            }
        } catch (Throwable e) {
            LOGGER.warn("[PermissionHelper] Failed to resolve PlayerList.isOp method", e);
        }

        LOGGER.info("[PermissionHelper] Resolution complete. CSS={}, Player={}, isOp={}",
                cssHasPermission != null, playerHasPermissions != null, playerListIsOp != null);
    }

    /**
     * Check if a CommandSourceStack has the given permission level.
     * Used in command .requires() predicates.
     */
    public static boolean hasPermission(CommandSourceStack source, int level) {
        resolve();

        // Strategy 1: Use CommandSourceStack's own permission method
        if (cssHasPermission != null) {
            try {
                return (boolean) cssHasPermission.invoke(source, level);
            } catch (Throwable e) {
                LOGGER.debug("[PermissionHelper] CSS method failed, trying fallback", e);
            }
        }

        // Strategy 2: Get the player entity and check permissions on it
        try {
            if (source.getEntity() instanceof ServerPlayer player) {
                return hasPermissions(player, source.getServer(), level);
            }
        } catch (Throwable e) {
            LOGGER.debug("[PermissionHelper] Player entity fallback failed", e);
        }

        // Strategy 3: Non-player source (console, command block) - allow
        try {
            if (source.getEntity() == null) {
                return true; // Console always has permission
            }
        } catch (Throwable ignored) {}

        // Default: deny
        return false;
    }

    /**
     * Check if a Player/ServerPlayer has the given permission level.
     * Used in admin checks and GUI validation.
     */
    public static boolean hasPermissions(Player player, net.minecraft.server.MinecraftServer server, int level) {
        resolve();

        // Strategy 1: Direct method on player
        if (playerHasPermissions != null) {
            try {
                return (boolean) playerHasPermissions.invoke(player, level);
            } catch (Throwable e) {
                LOGGER.debug("[PermissionHelper] Player method failed, trying fallback", e);
            }
        }

        // Strategy 2: PlayerList.isOp (only checks op status, not specific levels, but good enough for level 2+)
        if (playerListIsOp != null && server != null) {
            try {
                Object playerList = server.getPlayerList();
                return (boolean) playerListIsOp.invoke(playerList, player.getGameProfile());
            } catch (Throwable e) {
                LOGGER.debug("[PermissionHelper] isOp fallback failed, trying ops list", e);
            }
        }

        // Strategy 3: Check ops.json entries via the server's op list directly
        try {
            if (server != null) {
                var ops = server.getPlayerList().getOps();
                // ops.get() - try NameAndId (1.21.11+) then GameProfile (1.21.1)
                Object key;
                try {
                    Class<?> nameAndIdClass = Class.forName("net.minecraft.server.players.NameAndId");
                    key = nameAndIdClass.getConstructor(com.mojang.authlib.GameProfile.class).newInstance(player.getGameProfile());
                } catch (Throwable t2) {
                    key = player.getGameProfile();
                }
                Object entry = null; try { entry = ops.getClass().getMethod("get", Object.class).invoke(ops, key); } catch(Throwable e){}
                return entry != null;
            }
        } catch (Throwable e) {
            LOGGER.debug("[PermissionHelper] Ops list check failed", e);
        }

        // Strategy 4: The Ultimate Fallback - Read ops.json directly!
        // This avoids ALL Minecraft methods and works perfectly on any obfuscated server
        try {
            java.io.File opsFile = new java.io.File("ops.json");
            if (opsFile.exists() && opsFile.canRead()) {
                String content = new String(java.nio.file.Files.readAllBytes(opsFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                // ops.json contains an array of objects with "uuid" fields
                // E.g. "uuid": "123e4567-e89b-12d3-a456-426614174000"
                if (content.contains(player.getUUID().toString())) {
                    LOGGER.info("[PermissionHelper] Found player {} in ops.json!", player.getName().getString());
                    return true;
                }
            }
        } catch (Throwable e) {
            LOGGER.debug("[PermissionHelper] Direct ops.json check failed", e);
        }

        // Default: deny
        return false;
    }
}
