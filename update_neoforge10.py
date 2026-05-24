import re

file_path = r'c:\Users\lenovo\OneDrive\Documents\mods\login neoforge\src\main\java\com\example\loginsystem\LoginSystem.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix 1: The inventory ID extraction (no registry mapping errors!)
safe_id_extraction = '''
                          String itemId = stack.getItem().getDescriptionId();
                          if (itemId.startsWith("block.")) itemId = itemId.substring(6);
                          else if (itemId.startsWith("item.")) itemId = itemId.substring(5);
                          itemId = itemId.replace(".", ":");
                          itemObj.addProperty("id", itemId);
'''

# Replace the complicated reflection block
old_block_pattern = r'String itemId = "minecraft:air";\s*try \{.*?catch \(Exception e\) \{\}\s*itemObj\.addProperty\("id", itemId\);'
content = re.sub(old_block_pattern, safe_id_extraction.strip(), content, flags=re.DOTALL)


# Fix 2: Add loginTimers and ServerTickEvent
timer_var = '''
    // Maps UUID to login timer (in ticks). 60 seconds = 1200 ticks.
    public static final java.util.Map<java.util.UUID, Integer> loginTimers = new java.util.concurrent.ConcurrentHashMap<>();
'''
content = re.sub(r'public static java\.util\.Map<java\.util\.UUID, Long> lastLogins.*?;', r'\g<0>\n' + timer_var, content, count=1)

tick_event = '''
    @net.neoforged.bus.api.SubscribeEvent
    public void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (java.util.UUID uuid : loginTimers.keySet()) {
                int timeLeft = loginTimers.get(uuid) - 1;
                if (timeLeft <= 0) {
                    loginTimers.remove(uuid);
                    ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                    if (player != null && !loggedInPlayers.contains(uuid)) {
                        player.connection.disconnect(net.minecraft.network.chat.Component.literal("Login timeout. You took too long to authenticate."));
                    }
                } else {
                    loginTimers.put(uuid, timeLeft);
                    // Send actionbar title warning when time is running out (e.g., < 15 seconds)
                    if (timeLeft % 20 == 0 && timeLeft <= 300) {
                        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                        if (player != null && !loggedInPlayers.contains(uuid)) {
                            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Time left to login: " + (timeLeft / 20) + "s").withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD), true);
                        }
                    }
                }
            }
        }
    }
'''
content = re.sub(r'(public void onPlayerLogin)', tick_event.strip() + r'\n\n    @net.neoforged.bus.api.SubscribeEvent\n    \1', content, count=1)

# Modify onPlayerLogin to start the timer
content = re.sub(
    r'(knownPlayerNames\.put\(newPlayerUUID, newPlayer\.getName\(\)\.getString\(\)\);\s*saveBans\(\);)', 
    r'\1\n        loginTimers.put(newPlayerUUID, 1200);', 
    content, 
    count=1
)

# Modify the successful login method to stop the timer
content = re.sub(
    r'(loggedInPlayers\.add\(uuid\);)',
    r'\1\n            loginTimers.remove(uuid);',
    content
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Applied fixes for inventory ID extraction and login timeout")
