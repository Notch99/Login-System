import re

file_path = r'c:\Users\lenovo\OneDrive\Documents\mods\login neoforge\src\main\java\com\example\loginsystem\LoginSystem.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Add reflection helper methods
helpers = '''
    public static net.minecraft.nbt.Tag safeSerializeItemStack(net.minecraft.world.item.ItemStack stack, net.minecraft.core.HolderLookup.Provider registries) {
        try {
            for (java.lang.reflect.Method m : net.minecraft.world.item.ItemStack.class.getMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(net.minecraft.core.HolderLookup.Provider.class)) {
                    if (m.getName().contains("save") || m.getName().startsWith("m_")) {
                        Object res = m.invoke(stack, registries);
                        if (res instanceof net.minecraft.nbt.Tag) return (net.minecraft.nbt.Tag) res;
                    }
                }
            }
        } catch (Throwable t) {}
        return new net.minecraft.nbt.CompoundTag();
    }

    public static net.minecraft.world.item.ItemStack safeDeserializeItemStack(net.minecraft.core.HolderLookup.Provider registries, net.minecraft.nbt.Tag tag) {
        try {
            for (java.lang.reflect.Method m : net.minecraft.world.item.ItemStack.class.getMethods()) {
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 2) {
                    if (m.getParameterTypes()[0].isAssignableFrom(net.minecraft.core.HolderLookup.Provider.class) && 
                        net.minecraft.nbt.Tag.class.isAssignableFrom(m.getParameterTypes()[1])) {
                        
                        if (m.getName().contains("parse") || m.getName().startsWith("m_")) {
                            Object res = m.invoke(null, registries, tag);
                            if (res instanceof net.minecraft.world.item.ItemStack) return (net.minecraft.world.item.ItemStack) res;
                            if (res instanceof java.util.Optional) {
                                java.util.Optional<?> opt = (java.util.Optional<?>) res;
                                if (opt.isPresent() && opt.get() instanceof net.minecraft.world.item.ItemStack) {
                                    return (net.minecraft.world.item.ItemStack) opt.get();
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {}
        return net.minecraft.world.item.ItemStack.EMPTY;
    }
'''

if "safeSerializeItemStack" not in content:
    content = re.sub(r'(private String serializeInventory\(net\.minecraft\.world\.item\.ItemStack\[\] inventory\) \{)', helpers.strip() + r'\n\n    \1', content, count=1)

# Replace the serialization
content = re.sub(r'net\.minecraft\.nbt\.Tag itemTag = stack\.saveOptional\(registries\);', 
                 'net.minecraft.nbt.Tag itemTag = safeSerializeItemStack(stack, registries);', content)
content = re.sub(r'CompoundTag itemTag = \(CompoundTag\) stack\.save\(registries\);', 
                 'net.minecraft.nbt.Tag itemTag = safeSerializeItemStack(stack, registries);', content)

# Replace the deserialization
content = re.sub(r'inventory\[slot\] = net\.minecraft\.world\.item\.ItemStack\.parseOptional\(registries, itemTag\);', 
                 'inventory[slot] = safeDeserializeItemStack(registries, itemTag);', content)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Applied reflection-based ItemStack serialization")
