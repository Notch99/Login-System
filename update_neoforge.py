import re

file_path = r'c:\Users\lenovo\OneDrive\Documents\mods\login neoforge\src\main\java\com\example\loginsystem\LoginSystem.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Add variables
var_injection = '''
    // Custom Ban System
    public static java.util.Map<java.util.UUID, Long> tempBans = new java.util.concurrent.ConcurrentHashMap<>();
    public static java.util.Map<java.util.UUID, String> knownPlayerNames = new java.util.concurrent.ConcurrentHashMap<>();
    
    // تخزين كلمات المرور الأصلية للأدمن'''
content = re.sub(r'\s*// تخزين كلمات المرور الأصلية للأدمن', var_injection, content, count=1)

# 2. Add loadBans/saveBans/ban/unban/isBanned after forceChangePassword
# Wait, let's find forceChangePassword
methods_injection = '''
    private void loadBans() {
        try {
            java.nio.file.Path bansFile = java.nio.file.Paths.get("config/loginsystem_bans.json");
            if (java.nio.file.Files.exists(bansFile)) {
                String json = java.nio.file.Files.readString(bansFile);
                com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : obj.entrySet()) {
                    tempBans.put(java.util.UUID.fromString(entry.getKey()), entry.getValue().getAsLong());
                }
            }
        } catch (Exception e) {}
        
        try {
            java.nio.file.Path namesFile = java.nio.file.Paths.get("config/loginsystem_names.json");
            if (java.nio.file.Files.exists(namesFile)) {
                String json = java.nio.file.Files.readString(namesFile);
                com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : obj.entrySet()) {
                    knownPlayerNames.put(java.util.UUID.fromString(entry.getKey()), entry.getValue().getAsString());
                }
            }
        } catch (Exception e) {}
    }

    private void saveBans() {
        try {
            com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
            for (java.util.Map.Entry<java.util.UUID, Long> entry : tempBans.entrySet()) {
                obj.addProperty(entry.getKey().toString(), entry.getValue());
            }
            java.nio.file.Files.writeString(java.nio.file.Paths.get("config/loginsystem_bans.json"), obj.toString());
        } catch (Exception e) {}
        
        try {
            com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
            for (java.util.Map.Entry<java.util.UUID, String> entry : knownPlayerNames.entrySet()) {
                obj.addProperty(entry.getKey().toString(), entry.getValue());
            }
            java.nio.file.Files.writeString(java.nio.file.Paths.get("config/loginsystem_names.json"), obj.toString());
        } catch (Exception e) {}
    }

    public void banPlayer(java.util.UUID uuid, String reason, int durationDays) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> {
                String playerName = getPlayerName(server, uuid);
                long expires = durationDays > 0 ? System.currentTimeMillis() + (durationDays * 86400000L) : Long.MAX_VALUE;
                tempBans.put(uuid, expires);
                saveBans();
                
                if (playerName != null && !playerName.equals("Unknown")) {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "ban " + playerName + " " + reason);
                }
                
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    String msg = "You are banned: " + reason;
                    if (durationDays > 0) msg += " for " + durationDays + " days.";
                    player.connection.disconnect(net.minecraft.network.chat.Component.literal(msg));
                }
            });
        }
    }

    public void unbanPlayer(java.util.UUID uuid) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> {
                String playerName = getPlayerName(server, uuid);
                if (playerName != null && !playerName.equals("Unknown")) {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "pardon " + playerName);
                } else {
                    try {
                        com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(uuid, null);
                        server.getPlayerList().getBans().remove(profile);
                    } catch(Throwable t) {}
                }
                
                tempBans.remove(uuid);
                saveBans();
            });
        }
    }

    public boolean isBanned(java.util.UUID uuid) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (tempBans.containsKey(uuid)) {
            if (tempBans.get(uuid) > System.currentTimeMillis()) {
                return true;
            } else {
                tempBans.remove(uuid);
                saveBans();
                if (server != null) {
                    String name = getPlayerName(server, uuid);
                    if (name != null && !name.equals("Unknown")) {
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "pardon " + name);
                    }
                }
            }
        }
        
        if (server != null) {
            try {
                String playerName = getPlayerName(server, uuid);
                com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(uuid, playerName != null && !playerName.equals("Unknown") ? playerName : null);
                return server.getPlayerList().getBans().contains(profile);
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }
'''

content = re.sub(
    r'(public void kickPlayer.*?})', 
    r'\1\n' + methods_injection, 
    content, 
    flags=re.DOTALL, count=1
)

# 3. Add loadBans() to constructor / init
content = re.sub(
    r'loadLastLogins\(\);',
    r'loadLastLogins();\n        loadBans();',
    content,
    count=1
)

# 4. Modify onPlayerLogin to update knownPlayerNames and check if banned
on_login_mod = '''
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        ServerPlayer newPlayer = (ServerPlayer) event.getEntity();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        java.util.UUID newPlayerUUID = newPlayer.getUUID();
        
        knownPlayerNames.put(newPlayerUUID, newPlayer.getName().getString());
        saveBans();

        if (isBanned(newPlayerUUID)) {
            long expire = tempBans.getOrDefault(newPlayerUUID, Long.MAX_VALUE);
            String reason = "You are banned from this server.";
            if (expire != Long.MAX_VALUE) {
                long hoursLeft = (expire - System.currentTimeMillis()) / 3600000L;
                reason += " Expires in ~" + (hoursLeft > 24 ? (hoursLeft / 24) + " days" : hoursLeft + " hours") + ".";
            }
            newPlayer.connection.disconnect(net.minecraft.network.chat.Component.literal(reason));
            return;
        }
'''
content = re.sub(
    r'@SubscribeEvent\s*public void onPlayerLogin\(PlayerEvent\.PlayerLoggedInEvent event\) \{.*?java\.util\.UUID newPlayerUUID = newPlayer\.getUUID\(\);',
    on_login_mod.strip(),
    content,
    flags=re.DOTALL, count=1
)

# 5. Fix getPlayerName in NeoForge
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
content = re.sub(
    r'public String getPlayerName\(MinecraftServer server, UUID uuid\) \{.*?return "Unknown";\s*\}',
    get_player_name_mod.strip(),
    content,
    flags=re.DOTALL, count=1
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated NeoForge LoginSystem.java")
