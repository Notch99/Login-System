import re

file_path = r'c:\Users\lenovo\OneDrive\Documents\mods\login neoforge\src\main\java\com\example\loginsystem\LoginSystem.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Fix save() -> saveOptional()
content = content.replace('CompoundTag itemTag = (CompoundTag) stack.save(registries);', 'net.minecraft.nbt.Tag itemTag = stack.saveOptional(registries);')
content = content.replace('NBTHelper.putInt(itemTag, "Slot", i);', 'if (itemTag instanceof CompoundTag) NBTHelper.putInt((CompoundTag)itemTag, "Slot", i);')
content = content.replace('itemList.add(itemTag);', 'itemList.add(itemTag);')

# 2. Fix getInventoryData getKey() -> getResourceKey()
content = content.replace('net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()', 'net.minecraft.core.registries.BuiltInRegistries.ITEM.getResourceKey(stack.getItem()).get().location().toString()')

# 3. Add ServerChatEvent to enforce mute
chat_event = '''
    @net.neoforged.bus.api.SubscribeEvent
    public void onServerChat(net.neoforged.neoforge.event.ServerChatEvent event) {
        if (isMuted(event.getPlayer().getUUID())) {
            event.setCanceled(true);
            event.getPlayer().sendSystemMessage(net.minecraft.network.chat.Component.literal("You are muted and cannot speak.").withStyle(net.minecraft.ChatFormatting.RED));
        }
    }
'''
if "public void onServerChat" not in content:
    content = re.sub(r'(public void onPlayerLogin)', chat_event.strip() + r'\n\n    \1', content, count=1)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Applied fixes for saveOptional, getResourceKey, and ServerChatEvent")
