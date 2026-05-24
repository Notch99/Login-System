import re

file_path = r'c:\Users\lenovo\OneDrive\Documents\mods\login neoforge\src\main\java\com\example\loginsystem\LoginSystem.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

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

if "public static net.minecraft.nbt.Tag safeSerializeItemStack" not in content:
    content = re.sub(r'(private String serializeInventory\(ItemStack\[\] inventory\) \{)', helpers.strip() + r'\n\n    \1', content, count=1)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Injected helpers")
