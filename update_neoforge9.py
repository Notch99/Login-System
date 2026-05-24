import re

file_path = r'c:\Users\lenovo\OneDrive\Documents\mods\login neoforge\src\main\java\com\example\loginsystem\LoginSystem.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Replace the getResourceKey().get().location().toString() with the safe reflection/toString parsing
safe_id_extraction = '''
                        String itemId = "minecraft:air";
                        try {
                            Object registryKey = net.minecraft.core.registries.BuiltInRegistries.ITEM.getResourceKey(stack.getItem()).orElse(null);
                            if (registryKey != null) {
                                String str = registryKey.toString();
                                if (str.contains(" / ")) {
                                    itemId = str.split(" / ")[1].replace("]", "");
                                }
                            }
                        } catch (Exception e) {}
                        itemObj.addProperty("id", itemId);
'''

# The current code looks like:
# itemObj.addProperty("id", net.minecraft.core.registries.BuiltInRegistries.ITEM.getResourceKey(stack.getItem()).get().location().toString());

content = content.replace('itemObj.addProperty("id", net.minecraft.core.registries.BuiltInRegistries.ITEM.getResourceKey(stack.getItem()).get().location().toString());', safe_id_extraction.strip())

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Applied safe ResourceKey parsing")
