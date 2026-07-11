package com.example.loginsystem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.HashMap;

/**
 * Cross-version NBT helper using reflection.
 * Supports both 1.21.1 (direct return types) and 1.21.11+ (Optional return types, no type param on getList).
 */
public class NBTHelper {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final HashMap<String, Method> methodCache = new HashMap<>();

    private static Method findMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        String key = clazz.getName() + "#" + methodName + "#" + parameterTypes.length;
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
        // Try 1.21.11+ getIntOr(String, int) first
        Method m = findMethod(tag.getClass(), "getIntOr", String.class, int.class);
        if (m != null) {
            try { return (int) m.invoke(tag, key, 0); } catch (Exception e) {}
        }
        // Fall back to 1.21.1 getInt(String) which returns int directly
        m = findMethod(tag.getClass(), "getInt", String.class);
        if (m != null) {
            try {
                Object result = m.invoke(tag, key);
                if (result instanceof java.util.Optional<?> opt) {
                    return opt.isPresent() ? (Integer) opt.get() : 0;
                }
                return (int) result;
            } catch (Exception e) {}
        }
        return 0;
    }

    public static void putInt(CompoundTag tag, String key, int value) {
        try { tag.putInt(key, value); } catch (Exception e) {
            LOGGER.error("Error invoking putInt", e);
        }
    }

    public static String getString(CompoundTag tag, String key) {
        // Try 1.21.11+ getStringOr(String, String) first
        Method m = findMethod(tag.getClass(), "getStringOr", String.class, String.class);
        if (m != null) {
            try { return (String) m.invoke(tag, key, ""); } catch (Exception e) {}
        }
        // Fall back to 1.21.1 getString(String)
        m = findMethod(tag.getClass(), "getString", String.class);
        if (m != null) {
            try {
                Object result = m.invoke(tag, key);
                if (result instanceof java.util.Optional<?> opt) {
                    return opt.isPresent() ? (String) opt.get() : "";
                }
                return (String) result;
            } catch (Exception e) {}
        }
        return "";
    }

    public static void putString(CompoundTag tag, String key, String value) {
        try { tag.putString(key, value); } catch (Exception e) {
            LOGGER.error("Error invoking putString", e);
        }
    }

    public static boolean contains(CompoundTag tag, String key) {
        try { return tag.contains(key); } catch (Exception e) {
            LOGGER.error("Error invoking contains", e);
        }
        return false;
    }

    public static ListTag getList(CompoundTag tag, String key, int type) {
        // Try 1.21.11+ getListOrEmpty(String)
        Method m = findMethod(tag.getClass(), "getListOrEmpty", String.class);
        if (m != null) {
            try { return (ListTag) m.invoke(tag, key); } catch (Exception e) {}
        }
        // Try 1.21.11+ getList(String) -> Optional<ListTag>
        m = findMethod(tag.getClass(), "getList", String.class);
        if (m != null) {
            try {
                Object result = m.invoke(tag, key);
                if (result instanceof java.util.Optional<?> opt) {
                    return opt.isPresent() ? (ListTag) opt.get() : new ListTag();
                }
                if (result instanceof ListTag) return (ListTag) result;
            } catch (Exception e) {}
        }
        // Try 1.21.1 getList(String, int)
        m = findMethod(tag.getClass(), "getList", String.class, int.class);
        if (m != null) {
            try {
                Object result = m.invoke(tag, key, type);
                if (result instanceof ListTag) return (ListTag) result;
            } catch (Exception e) {}
        }
        return new ListTag();
    }

    public static CompoundTag getCompound(ListTag list, int index) {
        // Try 1.21.11+ getCompoundOrEmpty(int)
        Method m = findMethod(list.getClass(), "getCompoundOrEmpty", int.class);
        if (m != null) {
            try { return (CompoundTag) m.invoke(list, index); } catch (Exception e) {}
        }
        // Try getCompound(int) which may return Optional or CompoundTag
        m = findMethod(list.getClass(), "getCompound", int.class);
        if (m != null) {
            try {
                Object result = m.invoke(list, index);
                if (result instanceof java.util.Optional<?> opt) {
                    return opt.isPresent() ? (CompoundTag) opt.get() : new CompoundTag();
                }
                return (CompoundTag) result;
            } catch (Exception e) {}
        }
        return new CompoundTag();
    }

    public static int size(ListTag list) {
        try { return list.size(); } catch (Exception e) {
            LOGGER.error("Error invoking size", e);
        }
        return 0;
    }
}
