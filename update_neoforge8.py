import re

file_path = r'c:\Users\lenovo\OneDrive\Documents\mods\login neoforge\src\main\java\com\example\loginsystem\LoginSystem.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix double SubscribeEvent
content = content.replace('@SubscribeEvent\n    @net.neoforged.bus.api.SubscribeEvent', '@SubscribeEvent')

# If it was actually @net.neoforged.bus.api.SubscribeEvent followed by another SubscribeEvent?
# Let's just fix it directly.
content = re.sub(r'@SubscribeEvent\s+@net\.neoforged\.bus\.api\.SubscribeEvent', '@SubscribeEvent', content)
content = re.sub(r'@net\.neoforged\.bus\.api\.SubscribeEvent\s+@SubscribeEvent', '@SubscribeEvent', content)

# Wait, let's just make sure there are no double SubscribeEvents anywhere
content = re.sub(r'(@net\.neoforged\.bus\.api\.SubscribeEvent\s+){2,}', r'\1', content)
content = re.sub(r'(@SubscribeEvent\s+){2,}', r'\1', content)

# But what if they are different types?
content = content.replace('@SubscribeEvent\n    @net.neoforged.bus.api.SubscribeEvent', '@SubscribeEvent')
content = content.replace('@net.neoforged.bus.api.SubscribeEvent\n    @SubscribeEvent', '@SubscribeEvent')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Fixed duplicate annotations")
