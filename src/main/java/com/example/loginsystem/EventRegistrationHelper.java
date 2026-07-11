package com.example.loginsystem;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Cross-version event registration helper.
 * Supports both:
 * - EventBus 7 (Forge 1.21.11 / 61.x): Per-event static BUS fields with addListener(Consumer/Predicate)
 * - EventBus 6 (Forge 1.21.1 / 52.x): MinecraftForge.EVENT_BUS with addListener(Priority, receiveCancelled, Class, Consumer)
 */
public class EventRegistrationHelper {
    private static final Logger LOGGER = LogManager.getLogger();
    private static boolean isNewEventSystem = false;

    public static void registerAllEvents(Object modInstance) {
        // Detect which event system is available
        try {
            Class.forName("net.minecraftforge.eventbus.api.bus.EventBus");
            isNewEventSystem = true;
            LOGGER.info("✅ Detected EventBus 7 (Forge 1.21.5+) - using per-event BUS registration");
        } catch (ClassNotFoundException e) {
            isNewEventSystem = false;
            LOGGER.info("✅ Detected EventBus 6 (Forge 1.21.1) - using MinecraftForge.EVENT_BUS registration");
        }

        // Non-cancellable events
        registerEvent(modInstance, "net.minecraftforge.event.RegisterCommandsEvent", "onRegisterCommands", false);
        registerEvent(modInstance, "net.minecraftforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent", "onPlayerLogin", false);
        registerEvent(modInstance, "net.minecraftforge.event.entity.player.PlayerEvent$PlayerLoggedOutEvent", "onPlayerLogout", false);
        registerEvent(modInstance, "net.minecraftforge.event.server.ServerStartingEvent", "onServerStarting", false);
        registerEvent(modInstance, "net.minecraftforge.event.server.ServerStoppingEvent", "onServerStopping", false);

        // Cancellable events
        registerEvent(modInstance, "net.minecraftforge.event.ServerChatEvent", "onServerChat", true);
        registerEvent(modInstance, "net.minecraftforge.event.ServerChatEvent", "onChat", true);
        registerEvent(modInstance, "net.minecraftforge.event.CommandEvent", "onCommand", true);
        registerEvent(modInstance, "net.minecraftforge.event.entity.item.ItemTossEvent", "onPlayerDropItem", true);
        registerEvent(modInstance, "net.minecraftforge.event.level.BlockEvent$BreakEvent", "onBlockBreak", true);
        registerEvent(modInstance, "net.minecraftforge.event.level.BlockEvent$EntityPlaceEvent", "onBlockPlace", true);
        registerEvent(modInstance, "net.minecraftforge.event.entity.player.AttackEntityEvent", "onAttackEntity", true);
        registerEvent(modInstance, "net.minecraftforge.event.entity.living.LivingAttackEvent", "onLivingAttack", true);
        registerEvent(modInstance, "net.minecraftforge.event.entity.player.EntityItemPickupEvent", "onItemPickup", true);

        // PlayerInteractEvent sub-events (cancellable but we use setCancellationResult, not return value)
        registerEvent(modInstance, "net.minecraftforge.event.entity.player.PlayerInteractEvent$RightClickBlock", "onPlayerRightClickBlock", false);
        registerEvent(modInstance, "net.minecraftforge.event.entity.player.PlayerInteractEvent$RightClickItem", "onPlayerRightClickItem", false);
        registerEvent(modInstance, "net.minecraftforge.event.entity.player.PlayerInteractEvent$LeftClickBlock", "onPlayerLeftClickBlock", false);
        registerEvent(modInstance, "net.minecraftforge.event.entity.player.PlayerInteractEvent$EntityInteract", "onPlayerEntityInteract", false);
        registerEvent(modInstance, "net.minecraftforge.event.entity.player.PlayerInteractEvent$EntityInteractSpecific", "onPlayerEntityInteractSpecific", false);

        // Tick events - try Post variants first (1.21.11+), then base variants (1.21.1)
        boolean serverTickRegistered = registerEvent(modInstance, "net.minecraftforge.event.TickEvent$ServerTickEvent$Post", "onServerTick", false);
        if (!serverTickRegistered) {
            registerTickEventOldStyle(modInstance, "net.minecraftforge.event.TickEvent$ServerTickEvent", "onServerTick");
        }
        boolean playerTickRegistered = registerEvent(modInstance, "net.minecraftforge.event.TickEvent$PlayerTickEvent$Post", "onPlayerTick", false);
        if (!playerTickRegistered) {
            registerTickEventOldStyle(modInstance, "net.minecraftforge.event.TickEvent$PlayerTickEvent", "onPlayerTick");
        }
    }

    /**
     * Register a single event handler using either new or old event bus system.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean registerEvent(Object modInstance, String eventClassName, String handlerMethodName, boolean isCancellable) {
        try {
            Class<?> eventClass;
            try {
                eventClass = Class.forName(eventClassName);
            } catch (ClassNotFoundException e) {
                LOGGER.debug("Event class not found (expected on some versions): " + eventClassName);
                return false;
            }

            // Find the handler method on the mod instance
            Method handlerMethod = null;
            for (Method m : modInstance.getClass().getMethods()) {
                if (m.getName().equals(handlerMethodName)) {
                    handlerMethod = m;
                    break;
                }
            }
            if (handlerMethod == null) {
                LOGGER.error("Handler method not found: " + handlerMethodName);
                return false;
            }

            final Method handler = handlerMethod;
            final boolean handlerReturnsBoolean = handler.getReturnType() == boolean.class;

            if (isNewEventSystem) {
                return registerOnNewBus(modInstance, eventClass, handler, handlerMethodName, isCancellable, handlerReturnsBoolean);
            } else {
                return registerOnOldBus(modInstance, eventClass, handler, handlerMethodName, isCancellable, handlerReturnsBoolean);
            }
        } catch (Throwable e) {
            LOGGER.error("Failed to register event: " + eventClassName + " -> " + handlerMethodName, e);
            return false;
        }
    }

    /**
     * Register on EventBus 7 (1.21.11+) using per-event static BUS fields.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean registerOnNewBus(Object modInstance, Class<?> eventClass, Method handler,
                                            String handlerMethodName, boolean isCancellable, boolean handlerReturnsBoolean) {
        try {
            // Get the static BUS field from the event class
            Field busField = eventClass.getField("BUS");
            Object bus = busField.get(null);

            if (isCancellable && handlerReturnsBoolean) {
                // Use Predicate for cancellable events where handler returns boolean
                Predicate<Object> predicate = (event) -> {
                    try {
                        return (boolean) handler.invoke(modInstance, event);
                    } catch (Exception e) {
                        LOGGER.error("Error in event handler " + handlerMethodName, e);
                        return false;
                    }
                };
                // Find addListener(Predicate) method
                for (Method m : bus.getClass().getMethods()) {
                    if (m.getName().equals("addListener") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == Predicate.class) {
                        m.invoke(bus, predicate);
                        LOGGER.info("Registered (new/predicate): " + handlerMethodName);
                        return true;
                    }
                }
            }

            // Use Consumer for non-cancellable events or fallback
            Consumer<Object> consumer = (event) -> {
                try {
                    handler.invoke(modInstance, event);
                } catch (Exception e) {
                    LOGGER.error("Error in event handler " + handlerMethodName, e);
                }
            };
            for (Method m : bus.getClass().getMethods()) {
                if (m.getName().equals("addListener") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == Consumer.class) {
                    m.invoke(bus, consumer);
                    LOGGER.info("Registered (new/consumer): " + handlerMethodName);
                    return true;
                }
            }

            LOGGER.error("No suitable addListener found on BUS for: " + handlerMethodName);
            return false;
        } catch (NoSuchFieldException e) {
            LOGGER.debug("No BUS field on " + eventClass.getName() + " (expected on some versions)");
            return false;
        } catch (Throwable e) {
            LOGGER.error("Failed to register on new bus: " + handlerMethodName, e);
            return false;
        }
    }

    /**
     * Register on EventBus 6 (1.21.1) using MinecraftForge.EVENT_BUS.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean registerOnOldBus(Object modInstance, Class<?> eventClass, Method handler,
                                            String handlerMethodName, boolean isCancellable, boolean handlerReturnsBoolean) {
        try {
            // Get MinecraftForge.EVENT_BUS via reflection
            Class<?> minecraftForgeClass = Class.forName("net.minecraftforge.common.MinecraftForge");
            Field eventBusField = minecraftForgeClass.getField("EVENT_BUS");
            Object eventBus = eventBusField.get(null);

            // Create a Consumer wrapper
            Consumer<Object> consumer = (event) -> {
                try {
                    Object result = handler.invoke(modInstance, event);
                    // If handler returns true (wants to cancel), try to cancel the event
                    if (handlerReturnsBoolean && result instanceof Boolean && (Boolean) result) {
                        CompatibilityHelper.setCanceled(event, true);
                    }
                } catch (Exception e) {
                    LOGGER.error("Error in event handler " + handlerMethodName, e);
                }
            };

            // Try 4-arg addListener(EventPriority, boolean, Class, Consumer) first
            for (Method m : eventBus.getClass().getMethods()) {
                if (m.getName().equals("addListener") && m.getParameterCount() == 4) {
                    Class<?>[] paramTypes = m.getParameterTypes();
                    if (paramTypes[2] == Class.class && paramTypes[3] == Consumer.class) {
                        try {
                            Object normalPriority = Enum.valueOf((Class<Enum>) paramTypes[0], "NORMAL");
                            m.invoke(eventBus, normalPriority, false, eventClass, consumer);
                            LOGGER.info("Registered (old/4-arg): " + handlerMethodName + " for " + eventClass.getSimpleName());
                            return true;
                        } catch (Exception e) {
                            LOGGER.debug("4-arg addListener failed for " + handlerMethodName, e);
                        }
                    }
                }
            }

            // Fallback: try addListener(Consumer) - may not work without type info
            for (Method m : eventBus.getClass().getMethods()) {
                if (m.getName().equals("addListener") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == Consumer.class) {
                    try {
                        m.invoke(eventBus, consumer);
                        LOGGER.info("Registered (old/1-arg): " + handlerMethodName);
                        return true;
                    } catch (Exception e) {
                        LOGGER.debug("1-arg addListener failed for " + handlerMethodName, e);
                    }
                }
            }

            LOGGER.error("No suitable addListener found on old EVENT_BUS for: " + handlerMethodName);
            return false;
        } catch (Throwable e) {
            LOGGER.error("Failed to register on old bus: " + handlerMethodName, e);
            return false;
        }
    }

    /**
     * Register tick events for old Forge (1.21.1) where there's no .Post variant.
     * Filters for Phase.END to match the behavior of the .Post event.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean registerTickEventOldStyle(Object modInstance, String eventClassName, String handlerMethodName) {
        try {
            Class<?> eventClass = Class.forName(eventClassName);

            Method handlerMethod = null;
            for (Method m : modInstance.getClass().getMethods()) {
                if (m.getName().equals(handlerMethodName)) {
                    handlerMethod = m;
                    break;
                }
            }
            if (handlerMethod == null) return false;

            final Method handler = handlerMethod;

            // Create a Consumer that filters for Phase.END
            Consumer<Object> consumer = (event) -> {
                try {
                    // Check if event.phase == TickEvent.Phase.END
                    Object phase = event.getClass().getField("phase").get(event);
                    if (phase != null && phase.toString().equals("END")) {
                        handler.invoke(modInstance, event);
                    }
                } catch (Exception e) {
                    // If no phase field, just invoke directly
                    try { handler.invoke(modInstance, event); } catch (Exception ex) {}
                }
            };

            // Get the old event bus
            Class<?> minecraftForgeClass = Class.forName("net.minecraftforge.common.MinecraftForge");
            Object eventBus = minecraftForgeClass.getField("EVENT_BUS").get(null);

            // Register with 4-arg addListener
            for (Method m : eventBus.getClass().getMethods()) {
                if (m.getName().equals("addListener") && m.getParameterCount() == 4) {
                    Class<?>[] paramTypes = m.getParameterTypes();
                    if (paramTypes[2] == Class.class && paramTypes[3] == Consumer.class) {
                        try {
                            Object normalPriority = Enum.valueOf((Class<Enum>) paramTypes[0], "NORMAL");
                            m.invoke(eventBus, normalPriority, false, eventClass, consumer);
                            LOGGER.info("Registered (old/tick): " + handlerMethodName + " for " + eventClass.getSimpleName() + " (Phase.END)");
                            return true;
                        } catch (Exception e) {}
                    }
                }
            }
            return false;
        } catch (Throwable e) {
            LOGGER.error("Failed to register old-style tick event: " + handlerMethodName, e);
            return false;
        }
    }
}
