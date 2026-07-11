package com.example.loginsystem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.HashMap;

/**
 * Cross-version NBT helper using reflection.
 * Handles API differences and class hierarchy changes between Minecraft 1.21.1 and 1.21.11+
 * where direct invokevirtual calls may fail with NoSuchMethodError.
 */
public class NBTHelper {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final HashMap<String, Method> methodCache = new HashMap<>();

    private static Method getMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        String key = clazz.getName() + "#" + methodName;
        if (methodCache.containsKey(key)) {
            return methodCache.get(key);
        }

        try {
            Method m = clazz.getMethod(methodName, parameterTypes);
            m.setAccessible(true);
            methodCache.put(key, m);
            return m;
        } catch (Exception e) {
            // Only log at debug level since this is often an expected fallback
            LOGGER.debug("Failed to find method " + methodName + " on " + clazz.getSimpleName());
            methodCache.put(key, null);
            return null;
        }
    }

    public static int getInt(CompoundTag tag, String key) {
        Method m = getMethod(tag.getClass(), "getInt", String.class);
        if (m != null) {
            try {
                Object result = m.invoke(tag, key);
                if (result instanceof java.util.Optional<?> opt) {
                    return opt.isPresent() ? (Integer) opt.get() : 0;
                } else if (result instanceof Integer) {
                    return (Integer) result;
                }
            } catch (Exception e) {
                LOGGER.error("Error invoking getInt", e);
            }
        }
        return 0;
    }

    public static void putInt(CompoundTag tag, String key, int value) {
        Method m = getMethod(tag.getClass(), "putInt", String.class, int.class);
        if (m != null) {
            try {
                m.invoke(tag, key, value);
            } catch (Exception e) {
                LOGGER.error("Error invoking putInt", e);
            }
        }
    }

    public static String getString(CompoundTag tag, String key) {
        Method m = getMethod(tag.getClass(), "getString", String.class);
        if (m != null) {
            try {
                Object result = m.invoke(tag, key);
                if (result instanceof java.util.Optional<?> opt) {
                    return opt.isPresent() ? (String) opt.get() : "";
                } else if (result instanceof String) {
                    return (String) result;
                }
            } catch (Exception e) {
                LOGGER.error("Error invoking getString", e);
            }
        }
        return "";
    }

    public static void putString(CompoundTag tag, String key, String value) {
        Method m = getMethod(tag.getClass(), "putString", String.class, String.class);
        if (m != null) {
            try {
                m.invoke(tag, key, value);
            } catch (Exception e) {
                LOGGER.error("Error invoking putString", e);
            }
        }
    }

    public static boolean contains(CompoundTag tag, String key) {
        Method m = getMethod(tag.getClass(), "contains", String.class);
        if (m != null) {
            try {
                return (boolean) m.invoke(tag, key);
            } catch (Exception e) {
                LOGGER.error("Error invoking contains", e);
            }
        }
        return false;
    }

    public static ListTag getList(CompoundTag tag, String key, int type) {
        Method m = getMethod(tag.getClass(), "getList", String.class, int.class);
        if (m == null) {
            // Try getList without the type parameter
            m = getMethod(tag.getClass(), "getList", String.class);
        }
        if (m != null) {
            try {
                Object result = m.getParameterCount() == 2 ? m.invoke(tag, key, type) : m.invoke(tag, key);
                if (result instanceof java.util.Optional<?> opt) {
                    return opt.isPresent() ? (ListTag) opt.get() : new ListTag();
                } else if (result instanceof ListTag) {
                    return (ListTag) result;
                }
            } catch (Exception e) {
                LOGGER.error("Error invoking getList", e);
            }
        }
        return new ListTag();
    }

    public static CompoundTag getCompound(ListTag list, int index) {
        Method m = getMethod(list.getClass(), "getCompound", int.class);
        if (m != null) {
            try {
                Object result = m.invoke(list, index);
                if (result instanceof java.util.Optional<?> opt) {
                    return opt.isPresent() ? (CompoundTag) opt.get() : new CompoundTag();
                } else if (result instanceof CompoundTag) {
                    return (CompoundTag) result;
                }
            } catch (Exception e) {
                LOGGER.error("Error invoking getCompound", e);
            }
        }
        return new CompoundTag();
    }

    public static int size(ListTag list) {
        Method m = getMethod(list.getClass(), "size");
        if (m != null) {
            try {
                return (int) m.invoke(list);
            } catch (Exception e) {
                LOGGER.error("Error invoking size", e);
            }
        }
        return 0;
    }
}
