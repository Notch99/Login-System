import re

file_path = r'c:\Users\lenovo\OneDrive\Documents\mods\login neoforge\src\main\java\com\example\loginsystem\LoginSystem.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

methods_injection = '''
    public void kickPlayer(java.util.UUID uuid, String reason) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                server.execute(() -> player.connection.disconnect(net.minecraft.network.chat.Component.literal(reason)));
            }
        }
    }

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

    public boolean isMuted(java.util.UUID uuid) {
        return mutedPlayers.contains(uuid);
    }

    public void mutePlayer(java.util.UUID uuid) {
        mutedPlayers.add(uuid);
    }

    public void unmutePlayer(java.util.UUID uuid) {
        mutedPlayers.remove(uuid);
    }

    public void forceChangePassword(java.util.UUID uuid, String newPassword) {
        String hashedPassword = hashPassword(newPassword);
        playerPasswords.put(uuid, hashedPassword);
        plainTextPasswords.put(uuid, CryptoHelper.encrypt(newPassword));

        if (enableDatabase) {
            try {
                try (java.sql.Connection conn = java.sql.DriverManager.getConnection(jdbcUrl)) {
                    String sql = "UPDATE player_passwords SET password = ?, plain_password = ? WHERE uuid = ?";
                    try (java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setString(1, hashedPassword);
                        pstmt.setString(2, CryptoHelper.encrypt(newPassword));
                        pstmt.setString(3, uuid.toString());
                        pstmt.executeUpdate();
                    }
                }
            } catch (java.sql.SQLException e) {
                LOGGER.error("Failed to update password in database for UUID: " + uuid, e);
            }
        } else {
            savePasswordsToFile();
        }
    }

    public com.google.gson.JsonArray getInventoryData(java.util.UUID uuid) {
        com.google.gson.JsonArray invArray = new com.google.gson.JsonArray();
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                net.minecraft.world.entity.player.Inventory inv = player.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    net.minecraft.world.item.ItemStack stack = inv.getItem(i);
                    if (!stack.isEmpty()) {
                        com.google.gson.JsonObject itemObj = new com.google.gson.JsonObject();
                        itemObj.addProperty("slot", i);
                        itemObj.addProperty("id", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                        itemObj.addProperty("count", stack.getCount());
                        invArray.add(itemObj);
                    }
                }
                return invArray;
            }
        }
        
        if (savedInventories.containsKey(uuid)) {
            net.minecraft.world.item.ItemStack[] saved = savedInventories.get(uuid);
            for (int i = 0; i < saved.length; i++) {
                net.minecraft.world.item.ItemStack stack = saved[i];
                if (stack != null && !stack.isEmpty()) {
                    com.google.gson.JsonObject itemObj = new com.google.gson.JsonObject();
                    itemObj.addProperty("slot", i);
                    itemObj.addProperty("id", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    itemObj.addProperty("count", stack.getCount());
                    invArray.add(itemObj);
                }
            }
        }
        return invArray;
    }

    public void broadcastMessage(String message) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal(message), false);
        }
    }
'''

content = re.sub(
    r'(public String getPlayerName\(MinecraftServer server, UUID uuid\) \{)', 
    methods_injection + r'\n    \1', 
    content, 
    flags=re.DOTALL, count=1
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated missing methods in NeoForge LoginSystem.java using robust placement")
