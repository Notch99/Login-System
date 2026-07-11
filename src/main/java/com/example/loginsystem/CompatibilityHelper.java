package com.example.loginsystem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;
import com.mojang.authlib.GameProfile;

public class CompatibilityHelper {

    public static String getStringSafely(CompoundTag tag, String key) {
        try {
            Method m = CompoundTag.class.getMethod("getString", String.class);
            Object result = m.invoke(tag, key);
            if (result instanceof Optional) {
                return ((Optional<String>) result).orElse("");
            }
            return (String) result;
        } catch (Exception e) {}
        return "";
    }

    public static int getIntSafely(CompoundTag tag, String key) {
        try {
            Method m = CompoundTag.class.getMethod("getInt", String.class);
            Object result = m.invoke(tag, key);
            if (result instanceof Optional) {
                return ((Optional<Integer>) result).orElse(0);
            }
            return (int) result;
        } catch (Exception e) {}
        return 0;
    }

    public static Object getListSafely(CompoundTag tag, String key, int type) {
        try {
            Method m2 = CompoundTag.class.getMethod("getList", String.class, int.class);
            Object result = m2.invoke(tag, key, type);
            if (result instanceof Optional) {
                Object o = ((Optional<?>) result).orElse(null);
                return o != null ? o : new net.minecraft.nbt.ListTag();
            }
            return result;
        } catch (Exception e) {}
        try {
            Method m1 = CompoundTag.class.getMethod("getList", String.class);
            Object result = m1.invoke(tag, key);
            if (result instanceof Optional) {
                Object o = ((Optional<?>) result).orElse(null);
                return o != null ? o : new net.minecraft.nbt.ListTag();
            }
            return result;
        } catch (Exception e) {}
        return new net.minecraft.nbt.ListTag();
    }

    public static net.minecraft.nbt.Tag saveItemStackSafely(ItemStack stack, net.minecraft.core.HolderLookup.Provider provider) {
        try {
            try { return (net.minecraft.nbt.Tag) ItemStack.class.getMethod("saveOptional", net.minecraft.core.HolderLookup.Provider.class).invoke(stack, provider); } catch (Exception e) {}
            return (net.minecraft.nbt.Tag) ItemStack.class.getMethod("save", net.minecraft.core.HolderLookup.Provider.class).invoke(stack, provider);
        } catch (Exception e) { return new CompoundTag(); }
    }

    public static Optional<ItemStack> parseItemStackSafely(net.minecraft.core.HolderLookup.Provider provider, CompoundTag tag) {
        try {
            try { return (Optional<ItemStack>) ItemStack.class.getMethod("parse", net.minecraft.core.HolderLookup.Provider.class, net.minecraft.nbt.Tag.class).invoke(null, provider, tag); } catch (Exception e) {}
            try { return Optional.of((ItemStack) ItemStack.class.getMethod("parseOptional", net.minecraft.core.HolderLookup.Provider.class, CompoundTag.class).invoke(null, provider, tag)); } catch (Exception e) {}
            // Older 1.20 method if wrapped in 1.21 compat
            return Optional.of((ItemStack) ItemStack.class.getMethod("of", CompoundTag.class).invoke(null, tag));
        } catch (Exception e) { return Optional.empty(); }
    }

    public static CompoundTag getCompoundSafely(net.minecraft.nbt.ListTag list, int index) {
        try {
            Method m = net.minecraft.nbt.ListTag.class.getMethod("getCompound", int.class);
            Object result = m.invoke(list, index);
            if (result instanceof Optional) {
                Object opt = ((Optional<?>) result).orElse(null);
                if (opt instanceof CompoundTag) return (CompoundTag) opt;
            }
            return (CompoundTag) result;
        } catch (Exception e) {}
        return new CompoundTag();
    }

    public static void sendSystemMessage(Player player, Component msg) {
        try {
            Method m2 = null;
            try {
                m2 = Player.class.getMethod("sendSystemMessage", Component.class, boolean.class);
                m2.invoke(player, msg, false);
                return;
            } catch (Exception e) {}
            
            Method m1 = Player.class.getMethod("sendSystemMessage", Component.class);
            m1.invoke(player, msg);
        } catch (Exception e) {}
    }

    public static ServerLevel getLevel(Player player) {
        try {
            try { return (ServerLevel) Player.class.getMethod("serverLevel").invoke(player); } catch (Exception e) {}
            try { return (ServerLevel) Player.class.getMethod("level").invoke(player); } catch (Exception e) {}
            return (ServerLevel) Player.class.getMethod("getCommandSenderWorld").invoke(player);
        } catch (Exception e) { return null; }
    }

    public static void teleportTo(ServerPlayer player, ServerLevel level, double x, double y, double z, float yRot, float xRot) {
        try {
            for (Method m : ServerPlayer.class.getMethods()) {
                if (m.getName().equals("teleportTo")) {
                    int pc = m.getParameterCount();
                    Class<?>[] pts = m.getParameterTypes();
                    if (pc >= 4 && pts[0] == ServerLevel.class) {
                        try {
                            if (pc == 4) m.invoke(player, level, x, y, z);
                            else if (pc == 6) m.invoke(player, level, x, y, z, yRot, xRot);
                            else if (pc == 8) m.invoke(player, level, x, y, z, java.util.Collections.emptySet(), yRot, xRot, false);
                            return;
                        } catch (Exception ignored) {}
                    }
                }
            }
            player.teleportTo(x, y, z);
        } catch (Exception e) {}
    }

    public static MinecraftServer getServer(Player player) {
        try {
            try { return (MinecraftServer) Player.class.getMethod("getServer").invoke(player); } catch (Exception e) {}
            try { return (MinecraftServer) Player.class.getMethod("server").invoke(player); } catch (Exception e) {}
            try {
                Field f = ServerPlayer.class.getField("server");
                return (MinecraftServer) f.get(player);
            } catch (Exception e) {}
        } catch (Exception e) {}
        return null;
    }

    public static boolean hasPermissions(Player player, int level) {
        try {
            try { return (boolean) Player.class.getMethod("hasPermissions", int.class).invoke(player, level); } catch (Exception e) {}
            return (boolean) Player.class.getMethod("hasPermissionLevel", int.class).invoke(player, level);
        } catch (Exception e) { return false; }
    }

    public static void setPlayerProfile(ItemStack item, UUID uuid, String name) {
        try {
            GameProfile profile = new GameProfile(uuid, name);
            Object resolvableProfile = null;
            Class<?> rpClass = Class.forName("net.minecraft.world.item.component.ResolvableProfile");
            
            try { resolvableProfile = rpClass.getConstructor(GameProfile.class).newInstance(profile); } catch (Exception e) {}
            if (resolvableProfile == null) {
                try { resolvableProfile = rpClass.getMethod("of", GameProfile.class).invoke(null, profile); } catch (Exception e) {}
            }
            if (resolvableProfile == null) {
                try { resolvableProfile = rpClass.getConstructor(Optional.class, Optional.class, Class.forName("com.mojang.authlib.properties.PropertyMap"))
                            .newInstance(Optional.of(name), Optional.of(uuid), null); } catch (Exception e) {}
            }
            
            if (resolvableProfile != null) {
                for (Method m : ItemStack.class.getMethods()) {
                    if (m.getName().equals("set") && m.getParameterCount() == 2) {
                        try {
                            m.invoke(item, net.minecraft.core.component.DataComponents.PROFILE, resolvableProfile);
                            return;
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {}
    }

    public static String getPlayerNameFromCache(MinecraftServer server, UUID uuid) {
        try {
            Object cache = null;
            try { cache = MinecraftServer.class.getMethod("getProfileCache").invoke(server); } catch (Exception e) {}
            if (cache != null) {
                Object profileOptional = cache.getClass().getMethod("get", UUID.class).invoke(cache, uuid);
                if (profileOptional instanceof Optional) {
                    Object profile = ((Optional<?>) profileOptional).orElse(null);
                    if (profile instanceof GameProfile) {
                        try { return (String) GameProfile.class.getMethod("getName").invoke(profile); } catch (Exception ex) {
                            return (String) GameProfile.class.getMethod("name").invoke(profile);
                        }
                    }
                }
            }
        } catch (Exception e) {}
        return null;
    }

    public static boolean hasPermissionSource(Object source, int level) {
        try {
            try { return (boolean) source.getClass().getMethod("hasPermission", int.class).invoke(source, level); } catch (Exception e) {}
            return (boolean) source.getClass().getMethod("hasPermissionLevel", int.class).invoke(source, level);
        } catch (Exception e) { return false; }
    }

    public static void setCanceled(Object event, boolean canceled) {
        try {
            for (Method m : event.getClass().getMethods()) {
                if (m.getName().equals("setCanceled") && m.getParameterCount() == 1) {
                    m.invoke(event, canceled);
                    return;
                } else if (m.getName().equals("cancel") && m.getParameterCount() == 0 && canceled) {
                    m.invoke(event);
                    return;
                }
            }
        } catch (Exception e) {}
    }

    public static void registerAllEvents(Object modInstance, Object forgeEventBus) {
        registerDynamic(forgeEventBus, modInstance, "net.minecraftforge.event.RegisterCommandsEvent", "onRegisterCommands");
        registerDynamic(forgeEventBus, modInstance, "net.minecraftforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent", "onPlayerLogin");
        registerDynamic(forgeEventBus, modInstance, "net.minecraftforge.event.entity.player.PlayerEvent$PlayerLoggedOutEvent", "onPlayerLogout");
        registerDynamic(forgeEventBus, modInstance, "net.minecraftforge.event.entity.item.ItemTossEvent", "onPlayerDropItem");
        registerDynamic(forgeEventBus, modInstance, "net.minecraftforge.event.entity.living.LivingHurtEvent", "onPlayerHurt");
        registerDynamic(forgeEventBus, modInstance, "net.minecraftforge.event.level.BlockEvent$BreakEvent", "onBlockBreak");
        registerDynamic(forgeEventBus, modInstance, "net.minecraftforge.event.level.BlockEvent$EntityPlaceEvent", "onBlockPlace");
        registerDynamic(forgeEventBus, modInstance, "net.minecraftforge.event.entity.player.PlayerInteractEvent", "onPlayerInteract");
        registerDynamic(forgeEventBus, modInstance, "net.minecraftforge.event.entity.player.AttackEntityEvent", "onAttackEntity");
        registerDynamic(forgeEventBus, modInstance, "net.minecraftforge.event.entity.living.LivingAttackEvent", "onLivingAttack");
        registerDynamic(forgeEventBus, modInstance, "net.minecraftforge.event.entity.player.EntityItemPickupEvent", "onItemPickup");
        registerDynamic(forgeEventBus, modInstance, "net.minecraftforge.event.ServerChatEvent", "onChat");
        registerDynamic(forgeEventBus, modInstance, "net.minecraftforge.event.CommandEvent", "onCommand");
        
        // Server lifecycle events (if they run on this bus)
        registerDynamic(forgeEventBus, modInstance, "net.minecraftforge.event.server.ServerStartingEvent", "onServerStarting");
        registerDynamic(forgeEventBus, modInstance, "net.minecraftforge.event.server.ServerStoppingEvent", "onServerStopping");
        
        // TickEvent logic
        boolean registeredTick = registerDynamic(forgeEventBus, modInstance, "net.minecraftforge.event.TickEvent$PlayerTickEvent$Post", "onPlayerTick");
        if (!registeredTick) {
            registerDynamic(forgeEventBus, modInstance, "net.minecraftforge.event.TickEvent$PlayerTickEvent", "onPlayerTick");
        }
    }

    private static boolean registerDynamic(Object bus, Object instance, String className, String methodName) {
        try {
            Class<?> eventClass;
            try { 
                eventClass = Class.forName(className); 
            } catch (Exception e) { 
                com.example.loginsystem.LoginSystem.LOGGER.error("Event class not found: " + className);
                return false; 
            }
            
            java.lang.reflect.Method targetMethod = null;
            for (java.lang.reflect.Method m : instance.getClass().getMethods()) {
                if (m.getName().equals(methodName)) { targetMethod = m; break; }
            }
            if (targetMethod == null) {
                com.example.loginsystem.LoginSystem.LOGGER.error("Target method not found: " + methodName);
                return false;
            }
            final java.lang.reflect.Method m = targetMethod;
            
            java.util.function.Consumer<Object> consumer = (e) -> {
                if (eventClass.isInstance(e)) {
                    try { m.invoke(instance, e); } catch (Exception ex) { ex.printStackTrace(); }
                }
            };
            
            java.lang.reflect.Method bestAdd = null;
            for (java.lang.reflect.Method bM : bus.getClass().getMethods()) {
                if (bM.getName().equals("addListener")) {
                    if (bM.getParameterCount() == 4) {
                        Class<?>[] pts = bM.getParameterTypes();
                        if (pts[2] == Class.class && pts[3] == java.util.function.Consumer.class) {
                            try {
                                Class<?> priorityClass = pts[0];
                                Object normalPriority = Enum.valueOf((Class<Enum>) priorityClass, "NORMAL");
                                bM.invoke(bus, normalPriority, false, eventClass, consumer);
                                com.example.loginsystem.LoginSystem.LOGGER.info("Successfully registered " + className + " via 4-arg addListener");
                                return true;
                            } catch (Exception ex) {
                                com.example.loginsystem.LoginSystem.LOGGER.error("Failed to invoke 4-arg addListener for " + className, ex);
                            }
                        }
                    } else if (bM.getParameterCount() == 1 && bM.getParameterTypes()[0] == java.util.function.Consumer.class && bestAdd == null) {
                        bestAdd = bM; // Fallback
                    }
                }
            }
            
            if (bestAdd != null) {
                try {
                    bestAdd.invoke(bus, consumer);
                    com.example.loginsystem.LoginSystem.LOGGER.info("Successfully registered " + className + " via 1-arg addListener");
                    return true;
                } catch (Exception ex) {
                    com.example.loginsystem.LoginSystem.LOGGER.error("Failed to invoke 1-arg addListener for " + className, ex);
                }
            }
            com.example.loginsystem.LoginSystem.LOGGER.error("No valid addListener found for " + className);
            return false;
        } catch (Exception e) { 
            com.example.loginsystem.LoginSystem.LOGGER.error("Exception in registerDynamic", e);
            return false; 
        }
    }
    public static Object parseItemStack(Object registries, net.minecraft.nbt.CompoundTag tag) {
        try {
            java.lang.reflect.Method parseMethod = net.minecraft.world.item.ItemStack.class.getMethod("parse", net.minecraft.core.HolderLookup.Provider.class, net.minecraft.nbt.Tag.class);
            Object optionalResult = parseMethod.invoke(null, registries, tag);
            if (optionalResult instanceof java.util.Optional<?> opt) {
                return opt.isPresent() ? opt.get() : net.minecraft.world.item.ItemStack.EMPTY;
            }
        } catch (Throwable t) {}
        try {
            java.lang.reflect.Method parseOptionalMethod = net.minecraft.world.item.ItemStack.class.getMethod("parseOptional", net.minecraft.core.HolderLookup.Provider.class, net.minecraft.nbt.CompoundTag.class);
            return parseOptionalMethod.invoke(null, registries, tag);
        } catch (Throwable t2) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
    }
}
