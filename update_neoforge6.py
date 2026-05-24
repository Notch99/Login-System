import re

file_path = r'c:\Users\lenovo\OneDrive\Documents\mods\login neoforge\src\main\java\com\example\loginsystem\LoginSystem.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

get_player_name_mod = '''
    public String getPlayerName(MinecraftServer server, java.util.UUID uuid) {
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.getUUID().equals(uuid)) {
                    knownPlayerNames.put(uuid, player.getName().getString());
                    saveBans();
                    return player.getName().getString();
                }
            }
        }
        if (knownPlayerNames.containsKey(uuid)) return knownPlayerNames.get(uuid);
        return "Unknown";
    }
'''

# Find the start of getPlayerName
start_idx = content.find("public String getPlayerName(MinecraftServer server, UUID uuid) {")
if start_idx != -1:
    # Find the matching closing brace
    brace_count = 0
    end_idx = -1
    for i in range(start_idx, len(content)):
        if content[i] == '{':
            brace_count += 1
        elif content[i] == '}':
            brace_count -= 1
            if brace_count == 0:
                end_idx = i
                break
    
    if end_idx != -1:
        content = content[:start_idx] + get_player_name_mod.strip() + content[end_idx+1:]
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        print("Successfully replaced getPlayerName")
    else:
        print("Could not find end of getPlayerName")
else:
    print("Could not find start of getPlayerName")
