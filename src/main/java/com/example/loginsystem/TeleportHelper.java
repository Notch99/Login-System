package com.example.loginsystem;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

/**
 * Cross-version teleport helper using reflection.
 * Handles API differences and class hierarchy changes between Minecraft 1.21.1 and 1.21.11+
 * where direct invokevirtual calls may fail with NoSuchMethodError due to return type changes.
 */
public class TeleportHelper {
    private static final Logger LOGGER = LogManager.getLogger();
    private static Method teleportToMethod = null;
    private static boolean initialized = false;

    private static void init() {
        if (initialized) return;
        initialized = true;

        try {
            Class<?> clazz = ServerPlayer.class;
            while (clazz != null && clazz != Object.class) {
                for (Method m : clazz.getDeclaredMethods()) {
                    if (m.getName().equals("teleportTo")) {
                        Class<?>[] params = m.getParameterTypes();
                        // Look for teleportTo(ServerLevel, double, double, double, Set, float, float) -> 7 args
                        if (params.length == 7 && params[0] == ServerLevel.class && params[1] == double.class) {
                            m.setAccessible(true);
                            teleportToMethod = m;
                            LOGGER.info("[TeleportHelper] Found 7-arg teleportTo in " + clazz.getSimpleName());
                            return;
                        }
                        // Look for teleportTo(ServerLevel, double, double, double, float, float) -> 6 args
                        else if (params.length == 6 && params[0] == ServerLevel.class && params[1] == double.class) {
                            m.setAccessible(true);
                            teleportToMethod = m;
                            LOGGER.info("[TeleportHelper] Found 6-arg teleportTo in " + clazz.getSimpleName());
                            return;
                        }
                        // Look for teleportTo(double, double, double) -> 3 args
                        else if (params.length == 3 && params[0] == double.class && params[1] == double.class && params[2] == double.class) {
                            m.setAccessible(true);
                            teleportToMethod = m;
                            LOGGER.info("[TeleportHelper] Found 3-arg teleportTo in " + clazz.getSimpleName());
                            // Don't return immediately, keep looking for a better one, but save this as fallback
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
            if (teleportToMethod == null) {
                LOGGER.warn("[TeleportHelper] Could not find any teleportTo method!");
            }
        } catch (Exception e) {
            LOGGER.error("[TeleportHelper] Failed to find teleportTo method", e);
        }
    }

    public static void teleport(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        init();
        if (teleportToMethod != null) {
            try {
                int paramCount = teleportToMethod.getParameterCount();
                if (paramCount == 7) {
                    teleportToMethod.invoke(player, level, x, y, z, java.util.Collections.emptySet(), yaw, pitch);
                } else if (paramCount == 6) {
                    teleportToMethod.invoke(player, level, x, y, z, yaw, pitch);
                } else if (paramCount == 3) {
                    // For teleportTo(double, double, double)
                    teleportToMethod.invoke(player, x, y, z);
                    player.setYRot(yaw);
                    player.setXRot(pitch);
                }
            } catch (Exception e) {
                LOGGER.error("[TeleportHelper] Error invoking teleportTo", e);
            }
        } else {
            // Fallback for extreme cases: use simpler teleport
            try {
                player.setPos(x, y, z);
                player.setYRot(yaw);
                player.setXRot(pitch);
            } catch (Exception e) {
                LOGGER.error("[TeleportHelper] Fallback teleport failed", e);
            }
        }
    }
}
