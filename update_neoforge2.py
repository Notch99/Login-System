import re

file_path = r'c:\Users\lenovo\OneDrive\Documents\mods\login neoforge\src\main\java\com\example\loginsystem\LoginSystem.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Add missing variables
var_injection = '''
    public static java.util.Set<java.util.UUID> mutedPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static java.util.Map<java.util.UUID, String> plainTextPasswords = new java.util.concurrent.ConcurrentHashMap<>();
'''
content = re.sub(r'public static java.util.Map<java.util.UUID, String> knownPlayerNames.*?;', r'\g<0>\n' + var_injection, content, count=1)

# 2. Add missing methods
methods_injection = '''
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
    r'(public void unbanPlayer.*?})', 
    r'\1\n' + methods_injection, 
    content, 
    flags=re.DOTALL, count=1
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated missing methods in NeoForge LoginSystem.java")
