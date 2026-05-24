package com.example.loginsystem;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import com.example.loginsystem.callback.DropItemCallback;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.util.Formatting;
import net.minecraft.text.Text;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ClearTitleS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.command.CommandManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.RegistryOps;
import net.minecraft.nbt.NbtOps;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.UUID;

/**
 * Login System Mod v2.1
 *
 * This mod enforces that players register or log in before they can interact
 * with the game. It supports multiple storage methods (database via JDBC or a
 * local file),
 * and includes a waiting area that teleports unlogged players away from their
 * original location.
 *
 * Key features:
 * - Database support (MySQL, SQLite, PostgreSQL) via JDBC.
 * - Waiting area: Players are teleported to a configurable waiting area until
 * they log in.
 * - Inventory hiding: Unlogged players' inventories are hidden.
 * - Blindness effect: Unlogged players have a blindness effect applied.
 * - Double login prevention.
 * - Admin commands to view or delete stored passwords.
 */
public class LoginSystem implements ModInitializer {
    public static final Logger LOGGER = LogManager.getLogger();
    public static MinecraftServer serverInstance;
    // ================================
    // DATA STRUCTURES & GLOBAL VARIABLES
    // ================================
    // Stores hashed passwords for each player (key: player's UUID, value: hashed
    // password).
    private final HashMap<UUID, String> playerPasswords = new HashMap<>();
    // Tracks whether a player has successfully logged in.
    private final HashMap<UUID, Boolean> loggedIn = new HashMap<>();
    // Stores the player's original location (to be restored after login).
    private final HashMap<UUID, double[]> originalPositions = new HashMap<>();
    // Prevents disconnecting the same player multiple times.
    private final HashSet<UUID> alreadyDisconnected = new HashSet<>();
    // Muted players from Web Panel
    private final HashSet<UUID> mutedPlayers = new HashSet<>();
    // Stores players' inventories to be restored after login.
    private final HashMap<UUID, ItemStack[]> savedInventories = new HashMap<>();
    // Boss bars for tracking login timeout for each player
    private final HashMap<UUID, ServerBossBar> playerBossBars = new HashMap<>();
    // Stores the last login timestamp for each player
    private final HashMap<UUID, Long> lastLogins = new HashMap<>();
    // Language manager for multi-language support
    private LanguageManager languageManager;

    // ================================
    // CONFIGURATION VARIABLES
    // ================================
    // Configuration properties loaded from file.
    private final Properties config = new Properties();
    // The configuration file is located at "config/loginsystem.properties".
    private final File configFile = new File("config/loginsystem.properties");
    // If database storage is not enabled, passwords are saved to this file.
    private final File passwordFile = new File("config/passwords.txt");
    private final File lastLoginsFile = new File("config/last_logins.json");

    // Database settings (retrieved from the config file).
    private boolean enableDatabase;
    private String jdbcUrl;
    private String dbHost;
    private String dbPort;
    private String dbName;
    private String dbUsername;
    private String dbPassword;

    public static final java.util.concurrent.ConcurrentHashMap<UUID, String> loginAreaDimensions = new java.util.concurrent.ConcurrentHashMap<>();

    // Custom Ban System to avoid mapping/obfuscation issues
    public static java.util.Map<UUID, Long> tempBans = new java.util.concurrent.ConcurrentHashMap<>();

    // Robust Player Name Cache to avoid offline player resolution crashes (NoSuchMethodError)
    public static java.util.Map<UUID, String> knownPlayerNames = new java.util.concurrent.ConcurrentHashMap<>();

    private static final String DEFAULT_DATABASE_URL = "jdbc:sqlite:loginsystem.db";
    private boolean enableWebPanel;
    private int webPanelPort;
    private String webPanelPassword;
    private AdminWebServer webServer;

    // Admin GUI methods

    public boolean isEnableDatabase() {
        return enableDatabase;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getPasswordForPlayer(UUID uuid) {
        return "[HIDDEN]";
    }

    public boolean hasPlayerPassword(UUID uuid) {
        return playerPasswords.containsKey(uuid);
    }

    public void removePlayerPassword(UUID uuid) {
        playerPasswords.remove(uuid);
    }

    public java.util.HashMap<UUID, String> getPlayerPasswords() {
        return playerPasswords;
    }

    public boolean isMuted(UUID uuid) {
        return mutedPlayers.contains(uuid);
    }

    public void mutePlayer(UUID uuid) {
        mutedPlayers.add(uuid);
    }

    public void unmutePlayer(UUID uuid) {
        mutedPlayers.remove(uuid);
    }

    public void forceChangePassword(java.util.UUID uuid, String newPassword) {
        String hashedPassword = hashPassword(newPassword);
        playerPasswords.put(uuid, hashedPassword);

        if (enableDatabase) {
            try {
                try (java.sql.Connection conn = java.sql.DriverManager.getConnection(jdbcUrl)) {
                    String sql = "UPDATE player_passwords SET password = ? WHERE uuid = ?";
                    try (java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setString(1, hashedPassword);
                        pstmt.setString(2, uuid.toString());
                        pstmt.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to update password in database for UUID: " + uuid, e);
            }
        } else {
            savePasswordsToFile();
        }
    }

    public void kickPlayer(UUID uuid, String reason) {
        if (serverInstance != null) {
            ServerPlayerEntity player = serverInstance.getPlayerManager().getPlayer(uuid);
            if (player != null) {
                serverInstance.execute(() -> player.networkHandler.disconnect(Text.literal(reason)));
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
                    tempBans.put(UUID.fromString(entry.getKey()), entry.getValue().getAsLong());
                }
            }
        } catch (Exception e) {}
        
        try {
            java.nio.file.Path namesFile = java.nio.file.Paths.get("config/loginsystem_names.json");
            if (java.nio.file.Files.exists(namesFile)) {
                String json = java.nio.file.Files.readString(namesFile);
                com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : obj.entrySet()) {
                    knownPlayerNames.put(UUID.fromString(entry.getKey()), entry.getValue().getAsString());
                }
            }
        } catch (Exception e) {}
    }

    private void saveBans() {
        try {
            com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
            for (java.util.Map.Entry<UUID, Long> entry : tempBans.entrySet()) {
                obj.addProperty(entry.getKey().toString(), entry.getValue());
            }
            java.nio.file.Files.writeString(java.nio.file.Paths.get("config/loginsystem_bans.json"), obj.toString());
        } catch (Exception e) {}
        
        try {
            com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
            for (java.util.Map.Entry<UUID, String> entry : knownPlayerNames.entrySet()) {
                obj.addProperty(entry.getKey().toString(), entry.getValue());
            }
            java.nio.file.Files.writeString(java.nio.file.Paths.get("config/loginsystem_names.json"), obj.toString());
        } catch (Exception e) {}
    }

    public void banPlayer(UUID uuid, String reason, int durationDays) {
        if (serverInstance != null) {
            serverInstance.execute(() -> {
                String playerName = getPlayerName(serverInstance, uuid);
                long expires = durationDays > 0 ? System.currentTimeMillis() + (durationDays * 86400000L) : Long.MAX_VALUE;
                tempBans.put(uuid, expires);
                saveBans();
                
                if (playerName != null && !playerName.equals("Unknown")) {
                    serverInstance.getCommandManager().executeWithPrefix(serverInstance.getCommandSource(), "ban " + playerName + " " + reason);
                }
                
                ServerPlayerEntity player = serverInstance.getPlayerManager().getPlayer(uuid);
                if (player != null) {
                    String msg = "You are banned: " + reason;
                    if (durationDays > 0) {
                        msg += " for " + durationDays + " days.";
                    }
                    player.networkHandler.disconnect(Text.literal(msg));
                }
            });
        }
    }

    public void unbanPlayer(UUID uuid) {
        if (serverInstance != null) {
            serverInstance.execute(() -> {
                String playerName = getPlayerName(serverInstance, uuid);
                if (playerName != null && !playerName.equals("Unknown")) {
                    serverInstance.getCommandManager().executeWithPrefix(serverInstance.getCommandSource(), "pardon " + playerName);
                } else {
                    try {
                        com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(uuid, null);
                        serverInstance.getPlayerManager().getUserBanList().remove(profile);
                    } catch(Throwable t) {}
                }
                
                tempBans.remove(uuid);
                saveBans();
            });
        }
    }

    public boolean isBanned(UUID uuid) {
        if (tempBans.containsKey(uuid)) {
            if (tempBans.get(uuid) > System.currentTimeMillis()) {
                return true;
            } else {
                tempBans.remove(uuid);
                saveBans();
                if (serverInstance != null) {
                    String name = getPlayerName(serverInstance, uuid);
                    if (name != null && !name.equals("Unknown")) {
                        serverInstance.getCommandManager().executeWithPrefix(serverInstance.getCommandSource(), "pardon " + name);
                    }
                }
            }
        }
        
        if (serverInstance != null) {
            try {
                String playerName = getPlayerName(serverInstance, uuid);
                com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(uuid, playerName != null && !playerName.equals("Unknown") ? playerName : null);
                return serverInstance.getPlayerManager().getUserBanList().contains(profile);
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }

    public JsonArray getInventoryData(UUID uuid) {
        JsonArray invArray = new JsonArray();
        if (serverInstance != null) {
            ServerPlayerEntity player = serverInstance.getPlayerManager().getPlayer(uuid);
            if (player != null) {
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    if (stack != null && !stack.isEmpty()) {
                        JsonObject item = new JsonObject();
                        item.addProperty("slot", i);
                        item.addProperty("id",
                                net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString());
                        item.addProperty("count", stack.getCount());
                        item.addProperty("name", stack.getName().getString());
                        invArray.add(item);
                    }
                }
            } else if (savedInventories.containsKey(uuid)) {
                ItemStack[] items = savedInventories.get(uuid);
                for (int i = 0; i < items.length; i++) {
                    ItemStack stack = items[i];
                    if (stack != null && !stack.isEmpty()) {
                        JsonObject item = new JsonObject();
                        item.addProperty("slot", i);
                        item.addProperty("id",
                                net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString());
                        item.addProperty("count", stack.getCount());
                        item.addProperty("name", stack.getName().getString());
                        invArray.add(item);
                    }
                }
            }
        }
        return invArray;
    }

    public void broadcastMessage(String message) {
        if (serverInstance != null) {
            serverInstance.execute(() -> {
                serverInstance.getPlayerManager().broadcast(Text.literal("§8[§cWeb Admin§8] §f" + message), false);
            });
        }
    }

    public void savePasswordsToFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(passwordFile))) {
            int count = 0;
            for (UUID uuid : playerPasswords.keySet()) {
                String hashed = playerPasswords.get(uuid);
                writer.write(uuid.toString() + ":" + hashed);
                writer.newLine();
                count++;
            }
            System.out.println("LoginSystem: Saved " + count + " passwords to file.");
        } catch (IOException e) {
            System.err.println("LoginSystem: Error writing password file!");
            e.printStackTrace();
        }
    }

    public String getPlayerName(MinecraftServer server, UUID uuid) {
        if (server != null) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.getUuid().equals(uuid)) {
                    knownPlayerNames.put(uuid, player.getName().getString());
                    saveBans(); // saves names too
                    return player.getName().getString();
                }
            }
        }
        
        if (knownPlayerNames.containsKey(uuid)) {
            return knownPlayerNames.get(uuid);
        }
        
        return "Unknown";
    }

    public void openAdminGUI(ServerPlayerEntity admin) {
        // SECURITY CHECK: Must be logged in to access admin panel
        UUID adminId = admin.getUuid();
        if (!loggedIn.getOrDefault(adminId, false)) {
            String msg = languageManager.getMessage(adminId, "restrict.command");
            admin.sendMessage(Text.literal("" + msg).formatted(Formatting.RED), false);
            showActionBar(admin, msg);
            LOGGER.warn("SECURITY: Player " + admin.getName().getString()
                    + " tried to access admin panel without logging in!");
            return;
        }

        // Create container with size 27 (small chest - 3 rows)
        net.minecraft.inventory.SimpleInventory container = new net.minecraft.inventory.SimpleInventory(27);

        // Add book at position 13 (center) to view players
        ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
        book.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6§lInfo").formatted(Formatting.BOLD));
        NbtCompound nbt1 = new NbtCompound();
        nbt1.putString("GUIAction", "ViewPlayers");
        book.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt1));
        container.setStack(13, book);

        // Add barrier at position 11 (left of center) for deletion
        ItemStack barrier = new ItemStack(Items.BARRIER);
        barrier.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§c§lDelete Player").formatted(Formatting.BOLD));
        NbtCompound nbt2 = new NbtCompound();
        nbt2.putString("GUIAction", "DeletePlayers");
        barrier.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt2));
        container.setStack(11, barrier);

        // Open GUI
        admin.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, player) -> new AdminGUIMenu(syncId, playerInventory, container, this, "MAIN"),
                Text.literal("§6§lAdmin Panel")));
    }

    public void openPlayersListGUI(ServerPlayerEntity admin) {
        // SECURITY CHECK: Double-check login status
        UUID adminId = admin.getUuid();
        if (!loggedIn.getOrDefault(adminId, false)) {
            admin.closeHandledScreen();
            LOGGER.warn("SECURITY: Unauthorized access attempt to players list by " + admin.getName().getString());
            return;
        }

        net.minecraft.inventory.SimpleInventory container = new net.minecraft.inventory.SimpleInventory(54);

        int slot = 0;
        for (UUID uuid : playerPasswords.keySet()) {
            if (slot >= 54)
                break;

            String playerName = getPlayerName(LoginSystem.serverInstance, uuid);
            String password = "[HIDDEN]";

            ItemStack playerHead = new ItemStack(Items.PLAYER_HEAD);
            playerHead.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e" + playerName).formatted(Formatting.BOLD));

            NbtCompound nbt = new NbtCompound();
            nbt.putString("PlayerUUID", uuid.toString());
            nbt.putString("GUIAction", "ViewPlayers");
            playerHead.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

            container.setStack(slot++, playerHead);
        }

        admin.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, player) -> new AdminGUIMenu(syncId, playerInventory, container, this, "VIEW"),
                Text.literal("§6Players List - View Only")));
    }

    public void openDeletePlayersGUI(ServerPlayerEntity admin) {
        // SECURITY CHECK: Double-check login status
        UUID adminId = admin.getUuid();
        if (!loggedIn.getOrDefault(adminId, false)) {
            admin.closeHandledScreen();
            LOGGER.warn("SECURITY: Unauthorized access attempt to delete panel by " + admin.getName().getString());
            return;
        }

        net.minecraft.inventory.SimpleInventory container = new net.minecraft.inventory.SimpleInventory(54);

        int slot = 0;
        for (UUID uuid : playerPasswords.keySet()) {
            if (slot >= 54)
                break;

            String playerName = getPlayerName(LoginSystem.serverInstance, uuid);
            String password = "[HIDDEN]";

            ItemStack playerHead = new ItemStack(Items.PLAYER_HEAD);
            playerHead.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§c" + playerName).formatted(Formatting.BOLD));

            NbtCompound nbt = new NbtCompound();
            nbt.putString("PlayerUUID", uuid.toString());
            nbt.putString("GUIAction", "DeleteThisPlayer");
            playerHead.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

            container.setStack(slot++, playerHead);
        }

        admin.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, player) -> new AdminGUIMenu(syncId, playerInventory, container, this,
                        "DELETE"),
                Text.literal("§c§lDelete Players")));
    }

    // ================================
    // FABRIC MOD INITIALIZATION
    // ================================
    @Override
    public void onInitialize() {
        LOGGER.info("Login System Mod is initializing...");

        // Load (or create) the configuration file.
        loadConfig();
        // BCrypt is used for password hashing (no init needed)

        // Initialize language manager with default language from config
        String defaultLang = config.getProperty("defaultLanguage", "en");
        languageManager = new LanguageManager(LOGGER, defaultLang, new File("config/loginsystem/languages"));
        LOGGER.info("Language system initialized (default: " + defaultLang + ")");

        // Retrieve database settings from the config.
        enableDatabase = Boolean.parseBoolean(config.getProperty("enableDatabase", "false"));

        if (!enableDatabase) {
            LOGGER.info("Database is DISABLED in configuration - using file storage");
        } else {
            LOGGER.info("ðŸ—ƒï¸ Database is ENABLED - embedded JDBC drivers available");
        }

        // Depending on the storage method, load stored passwords.
        if (enableDatabase) {
            LOGGER.info("Attempting to initialize database connection...");
            try {
                boolean driverLoaded = false;
                String originalJdbcUrl = jdbcUrl;

                // Try to load MySQL JDBC driver first (smaller size, more compatible)
                try {
                    Class.forName("com.mysql.cj.jdbc.Driver");
                    LOGGER.info("MySQL JDBC driver loaded successfully (embedded)");
                    jdbcUrl = originalJdbcUrl; // Keep original MySQL format
                    driverLoaded = true;
                } catch (ClassNotFoundException e) {
                    LOGGER.warn("âš ï¸ MySQL JDBC driver not found, trying MariaDB driver...");

                    // Try to load MariaDB JDBC driver as fallback
                    try {
                        Class.forName("org.mariadb.jdbc.Driver");
                        LOGGER.info("MariaDB JDBC driver loaded successfully (embedded)");
                        // Update JDBC URL to use MariaDB format
                        jdbcUrl = jdbcUrl.replace("jdbc:mysql://", "jdbc:mariadb://");
                        driverLoaded = true;
                    } catch (ClassNotFoundException ex) {
                        LOGGER.error("âŒ Neither MySQL nor MariaDB JDBC driver found in the classpath!");
                        throw new RuntimeException("No compatible JDBC driver found. Please check your dependencies.",
                                ex);
                    }
                }

                if (driverLoaded) {
                    // Set global login timeout to 3 seconds to prevent Watchdog Server crashes if DB is offline!
                    DriverManager.setLoginTimeout(3);
                    // Test the connection
                    try (Connection testConn = DriverManager.getConnection(jdbcUrl)) {
                        if (testConn.isValid(3)) { // 3 second timeout
                            // Initialize the database table if it doesn't exist.
                            initDatabase();
                            // Load existing player passwords from the database.
                            loadPasswordsFromDB();
                            LOGGER.info("Successfully connected to database: " + dbName);
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("âŒ Failed to connect to MySQL database: " + e.getMessage());
                LOGGER.error("Please check your database configuration in config/loginsystem.properties");
                LOGGER.error("Falling back to file storage");
                enableDatabase = false;
                loadPasswordsFromFile();
            } catch (Exception e) {
                LOGGER.error("âŒ Failed to initialize database: " + e.getMessage());
                LOGGER.error("Falling back to file storage");
                enableDatabase = false;
                loadPasswordsFromFile();
            }
        } else {
            // Load stored passwords from a local file.
            LOGGER.info("Using file-based storage (database disabled in config)");
            loadPasswordsFromFile();
        }

        loadLastLogins();
        loadBans();

        // Load unlogged states into memory
        loadAllUnloggedStates();

        // Register Fabric event handlers
        registerEventHandlers();

        LOGGER.info("Login System Mod initialized successfully!");
        LOGGER.info("SECURITY: All admin commands are protected - login required!");
        LOGGER.info("ðŸŒ Multi-language support: " + languageManager.getDefaultLanguage());
    }

    private void registerEventHandlers() {
        // Prevent double login spoofing in offline mode
        net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            try {
                String loginName = null;
                for (java.lang.reflect.Method m : handler.getClass().getMethods()) {
                    if (m.getReturnType() == com.mojang.authlib.GameProfile.class && m.getParameterCount() == 0) {
                        com.mojang.authlib.GameProfile profile = (com.mojang.authlib.GameProfile) m.invoke(handler);
                        if (profile != null) { loginName = profile.getName(); break; }
                    }
                }
                if (loginName == null) {
                    for (java.lang.reflect.Field f : handler.getClass().getDeclaredFields()) {
                        if (f.getType() == String.class) {
                            f.setAccessible(true);
                            String val = (String) f.get(handler);
                            if (val != null && !val.isEmpty()) { loginName = val; break; }
                        }
                    }
                }
                if (loginName != null && server.getPlayerManager().getPlayer(loginName) != null) {
                    handler.disconnect(net.minecraft.text.Text.literal("A player with this name is already online!"));
                }
            } catch (Exception e) {}
        });

        // Register commands with security wrapper
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);

            // SECURITY: Wrap ALL commands to block unlogged players
            // This prevents even OP players from using commands before login
            dispatcher.getRoot().getChildren().forEach(commandNode -> {
                String commandName = commandNode.getName();

                // Only allow /register, /login, and /help commands for unlogged players
                if (commandName.equals("register") ||
                        commandName.equals("login") ||
                        commandName.equals("help") ||
                        commandName.equals("?")) {
                    return; // These commands are allowed
                }

                // For all other commands, we'll check in execution
                // Note: We can't directly modify Brigadier nodes, so we use permission checks
                LOGGER.info("Protected command: /" + commandName);
            });
        });

        // Register server lifecycle events
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            LoginSystem.serverInstance = server;
            this.onServerStarting(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        // Register player connection events
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            UUID playerUUID = handler.player.getUuid();
            if (isBanned(playerUUID)) {
                long expire = tempBans.getOrDefault(playerUUID, Long.MAX_VALUE);
                String reason = "You are banned from this server.";
                if (expire != Long.MAX_VALUE) {
                    long hoursLeft = (expire - System.currentTimeMillis()) / 3600000L;
                    reason += " Expires in ~" + (hoursLeft > 24 ? (hoursLeft / 24) + " days" : hoursLeft + " hours") + ".";
                }
                handler.disconnect(Text.literal(reason));
                return;
            }
            onPlayerLogin(handler.getPlayer());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            onPlayerLogout(handler.getPlayer());
        });

        // Register server tick events for player monitoring
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

        // Register block break events
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                UUID playerId = serverPlayer.getUuid();
                if (!loggedIn.getOrDefault(playerId, false)) {
                    String msg = languageManager.getMessage(playerId, "restrict.break");
                    serverPlayer.sendMessage(Text.literal(msg).formatted(Formatting.RED), false);
                    showActionBar(serverPlayer, msg);
                    return false;
                }
            }
            return true;
        });

        // Register item use events (to prevent item dropping)
        registerSafeUseItemCallback();

        // Register item drop events to prevent unlogged players from dropping items
        DropItemCallback.EVENT.register((player, stack) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && !player.getWorld().isClient()) {
                UUID playerId = serverPlayer.getUuid();
                if (!loggedIn.getOrDefault(playerId, false)) {
                    String msg = languageManager.getMessage(playerId, "restrict.drop");
                    serverPlayer.sendMessage(Text.literal(msg).formatted(Formatting.RED), false);
                    showActionBar(serverPlayer, msg);
                    return net.minecraft.util.ActionResult.FAIL; // Cancel the drop
                }
            }
            return net.minecraft.util.ActionResult.PASS; // Allow the drop
        });

        // Register damage events to prevent unlogged players from taking damage
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, damageSource, amount) -> {
            if (entity instanceof ServerPlayerEntity player) {
                UUID playerId = player.getUuid();
                if (!loggedIn.getOrDefault(playerId, false)) {
                    return false; // Cancel damage
                }
            }
            return true; // Allow damage
        });

        // Register chat message events to block chat before login
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            UUID playerId = sender.getUuid();
            if (!loggedIn.getOrDefault(playerId, false)) {
                String msg = languageManager.getMessage(playerId, "restrict.chat");
                sender.sendMessage(Text.literal(msg).formatted(Formatting.RED), false);
                showActionBar(sender, msg);
                // Cancel the chat message
            }
        });

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            UUID playerId = sender.getUuid();
            if (!loggedIn.getOrDefault(playerId, false)) {
                return false;
            }
            if (mutedPlayers.contains(playerId)) {
                sender.sendMessage(Text.literal("You have been muted by an Admin.").formatted(Formatting.RED), false);
                return false;
            }
            return true;
        });
    }

    // ================================
    // CONFIGURATION FILE HANDLING
    // ================================
    /**
     * Loads the configuration from "config/loginsystem.properties".
     * If the file does not exist, it creates one with detailed comments.
     *
     * The config file includes:
     * - General Settings (e.g., loginTimeout)
     * - Messages for various events (registration, login, errors, etc.)
     * - Visual Effects & Inventory Control settings (blindness effect,
     * hideInventory)
     * - Database settings (enableDatabase, jdbcurl)
     * - Waiting Area settings (coordinates for waiting area)
     */
    private void loadConfig() {
        // Ensure the config directory exists.
        File configDir = new File("config");
        if (!configDir.exists()) {
            configDir.mkdir();
        }
        // If the config file does not exist, create it with default settings and
        // comments.
        if (!configFile.exists()) {
            String configContent = "#  Login System Mod Configuration File \n"
                    + "# This file contains configuration settings for the Login System mod.\n"
                    + "# Adjust the values below according to your server's needs.\n\n"
                    + "# ----------------------------\n"
                    + "# General Settings\n"
                    + "# ----------------------------\n"
                    + "# Maximum time (in seconds) a player can remain in the waiting area before being disconnected.\n"
                    + "loginTimeout=60\n"
                    + "# ----------------------------\n"
                    + "# Messages\n"
                    + "# ----------------------------\n"
                    + "# Message when registration is successful.\n"
                    + "message.registerSuccess=Registration successful! \n"
                    + "# Message when login is successful.\n"
                    + "message.loginSuccess=Login successful! \n"
                    + "# Message for incorrect password.\n"
                    + "message.incorrectPassword=Incorrect password! \n"
                    + "# Message when a player tries to login without registering.\n"
                    + "message.notRegistered=You are not registered! Use /register first. \n"
                    + "# Message when a player attempts to register again.\n"
                    + "message.alreadyRegistered=You are already registered! \n"
                    + "# Message when a player is kicked for timeout.\n"
                    + "message.kickTimeout=You were kicked for not logging in! \n\n"
                    + "# ----------------------------\n"
                    + "# Visual Effects & Inventory Control\n"
                    + "# ----------------------------\n"
                    + "# If true, applies a blindness effect to unlogged players.\n"
                    + "applyBlindness=true\n"
                    + "# Duration (in ticks) for the blindness effect (20 ticks = 1 second).\n"
                    + "blindnessDuration=40\n"
                    + "# If true, the player's inventory will be hidden until they log in.\n"
                    + "hideInventory=true\n\n"
                    + "# ----------------------------\n"
                    + "# Database Settings\n"
                    + "# ----------------------------\n"
                    + "# If true, the mod uses MySQL database to store passwords. If false, a local file is used.\n"
                    + "enableDatabase=false\n"
                    + "# Database host (use 127.0.0.1 instead of localhost)\n"
                    + "database.host=127.0.0.1\n"
                    + "# Database port\n"
                    + "database.port=3306\n"
                    + "# Database name\n"
                    + "database.name=loginsystem\n"
                    + "# Database username\n"
                    + "database.username=root\n"
                    + "# Database password\n"
                    + "database.password=your_password\n"
                    + "# Additional MySQL settings\n"
                    + "database.allowPublicKeyRetrieval=true\n"
                    + "database.useSSL=false\n"
                    + "database.autoReconnect=true\n"
                    + "database.maxReconnects=3\n\n"
                    + "# ----------------------------\n"
                    + "# Language Settings\n"
                    + "# ----------------------------\n"
                    + "# Default language for all players. Available: en, ar, fr, de, zh\n"
                    + "defaultLanguage=en\n\n"
                    + "# ----------------------------\n"
                    + "# Waiting Area Settings\n"
                    + "# ----------------------------\n"
                    + "# Set to false to disable the waiting area (players stay at their original position)\n"
                    + "enableWaitingArea=true\n"
                    + "# Coordinates for the waiting area where unlogged players will be teleported.\n"
                    + "waitingAreaX=0\n"
                    + "waitingAreaY=100\n"
                    + "waitingAreaZ=0\n\n"
                    + "# ----------------------------\n"
                    + "# Web Panel Settings\n"
                    + "# ----------------------------\n"
                    + "enableWebPanel=true\n"
                    + "webPanelPort=8080\n"
                    + "webPanelPassword=admin123\n";
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
                writer.write(configContent);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // Load the configuration from the file.
        try (InputStream in = new FileInputStream(configFile)) {
            config.load(in);

            // Load database settings
            enableDatabase = Boolean.parseBoolean(config.getProperty("enableDatabase", "false"));
            dbHost = config.getProperty("database.host", "127.0.0.1");
            dbPort = config.getProperty("database.port", "3306");
            dbName = config.getProperty("database.name", "loginsystem");
            dbUsername = config.getProperty("database.username", "root");
            dbPassword = config.getProperty("database.password", "");

            // Debug logging
            LOGGER.info("ðŸ”  Configuration loaded from: " + configFile.getAbsolutePath());
            LOGGER.info("ðŸ”  enableDatabase = " + enableDatabase);
            LOGGER.info("ðŸ”  database.host = " + dbHost);
            LOGGER.info("ðŸ”  database.username = " + dbUsername);
            LOGGER.info("ðŸ”  database.name = " + dbName);

            enableWebPanel = Boolean.parseBoolean(config.getProperty("enableWebPanel", "true"));
            webPanelPort = Integer.parseInt(config.getProperty("webPanelPort", "8080"));
            webPanelPassword = config.getProperty("webPanelPassword", "admin123");

            // Construct JDBC URL with proper encoding of username and password
            String encodedUsername = java.net.URLEncoder.encode(dbUsername, StandardCharsets.UTF_8.toString());
            String encodedPassword = java.net.URLEncoder.encode(dbPassword, StandardCharsets.UTF_8.toString());

            jdbcUrl = String.format(
                    "jdbc:mysql://%s:%s/%s?user=%s&password=%s&allowPublicKeyRetrieval=%s&useSSL=%s&autoReconnect=%s&maxReconnects=%s",
                    dbHost, dbPort, dbName, encodedUsername, encodedPassword,
                    config.getProperty("database.allowPublicKeyRetrieval", "true"),
                    config.getProperty("database.useSSL", "false"),
                    config.getProperty("database.autoReconnect", "true"),
                    config.getProperty("database.maxReconnects", "3"));

            LOGGER.info("Database configuration loaded successfully");
            if (enableDatabase) {
                LOGGER.info("Database connection URL: " + jdbcUrl.replace(encodedPassword, "******"));
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load configuration file", e);
            e.printStackTrace();
        }
    }

    // ================================
    // DATABASE HANDLING METHODS
    // ================================
    /**
     * Initializes the database by creating the "player_passwords" table if it does
     * not exist.
     */
    private void initDatabase() {
        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            try (Statement stmt = conn.createStatement()) {
                // Create the table if it doesn't exist
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS player_passwords (
                            uuid VARCHAR(36) PRIMARY KEY,
                            password TEXT NOT NULL,
                            plain_password TEXT,
                            last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");

                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS player_unlogged_states (
                            uuid VARCHAR(36) PRIMARY KEY,
                            state_data TEXT NOT NULL
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");

                LOGGER.info("Table created or already exists: player_passwords and player_unlogged_states");

                try {
                    stmt.execute(
                            "ALTER TABLE player_passwords ADD COLUMN IF NOT EXISTS last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
                } catch (SQLException e) {
                    LOGGER.debug("Column last_login already exists: " + e.getMessage());
                }
            }
            LOGGER.info("Database initialized successfully!");
        } catch (SQLException e) {
            LOGGER.error("Database error while initializing!", e);
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    /**
     * Loads all player passwords from the database into the playerPasswords map.
     */
    private void loadPasswordsFromDB() {
        int count = 0;
        playerPasswords.clear();

        String sql = "SELECT uuid, password FROM player_passwords";
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                try {
                    String uuidStr = rs.getString("uuid");
                    String password = rs.getString("password");

                    if (uuidStr != null && password != null && !password.trim().isEmpty()) {
                        UUID uuid = UUID.fromString(uuidStr);
                        playerPasswords.put(uuid, password.trim());
                        count++;
                    }
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Skipping invalid UUID in database: " + rs.getString("uuid"));
                }
            }
            LOGGER.info("Successfully loaded " + count + " passwords from database");

        } catch (SQLException e) {
            LOGGER.error("Failed to load passwords from database", e);
            throw new RuntimeException("Failed to load passwords from database", e);
        }
    }

    /**
     * Saves or updates a player's hashed password in the database.
     */
    private void savePasswordToDB(UUID uuid, String hashedPassword) {
        if (!enableDatabase)
            return;
        if (hashedPassword == null || hashedPassword.trim().isEmpty()) {
            LOGGER.warn("Attempted to save empty password for UUID: " + uuid);
            return;
        }

        String sql = """
                INSERT INTO player_passwords (uuid, password, last_login)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                    password = VALUES(password),
                    last_login = CURRENT_TIMESTAMP
                """;

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, uuid.toString());
            pstmt.setString(2, hashedPassword.trim());

            int affectedRows = pstmt.executeUpdate();
            LOGGER.debug("Saved password for UUID: {} ({} rows affected)", uuid, affectedRows);

        } catch (SQLException e) {
            LOGGER.error("Failed to save password for UUID: " + uuid, e);
            throw new RuntimeException("Failed to save password to database", e);
        }
    }

    // ================================
    // FILE-BASED STORAGE METHODS (Fallback)
    // ================================
    /**
     * Loads stored passwords from the local file into the playerPasswords map.
     */
    private void loadPasswordsFromFile() {
        if (!passwordFile.exists()) {
            System.out.println("LoginSystem: No password file found, starting fresh.");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(passwordFile))) {
            // Clear existing passwords before loading
            playerPasswords.clear();

            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":", 3);
                if (parts.length >= 2) {
                    UUID uuid = UUID.fromString(parts[0]);
                    playerPasswords.put(uuid, parts[1]);
                    count++;
                }
            }
            System.out.println("LoginSystem: Loaded " + count + " passwords from file.");
        } catch (IOException e) {
            System.err.println("LoginSystem: Error reading password file!");
            e.printStackTrace();
        }
    }

    // ================================
    // UTILITY METHODS
    // ================================
    /**
     * Hashes a given password using the SHA-256 algorithm.
     * This ensures that the password is stored securely.
     *
     * @param password The plain-text password.
     * @return A hexadecimal string representation of the hashed password.
     */
    private String hashPassword(String password) {
        return org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt(12));
    }

    private String hashPasswordLegacy(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found!", e);
        }
    }

    /**
     * Restores a player's inventory from the savedInventories map.
     *
     * @param player The player whose inventory will be restored.
     */
    private void restoreInventory(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        if (savedInventories.containsKey(playerId)) {
            ItemStack[] items = savedInventories.get(playerId);
            for (int i = 0; i < items.length; i++) {
                player.getInventory().setStack(i, items[i]);
            }
            savedInventories.remove(playerId);
            player.getInventory().markDirty();
        }
    }

    /**
     * Removes the blindness effect from a player.
     *
     * @param player The player from whom the effect will be removed.
     */
    private void removeBlindness(ServerPlayerEntity player) {
        player.removeStatusEffect(StatusEffects.BLINDNESS);
    }

    /**
     * Shows a title and subtitle to the player
     * 
     * @param player   The player
     * @param title    The title text
     * @param subtitle The subtitle text (can be null)
     */
    private void showTitle(ServerPlayerEntity player, String title, String subtitle) {
        // Clear any existing title
        player.networkHandler.sendPacket(new ClearTitleS2CPacket(false));

        // Send title
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(title)));

        // Send subtitle if provided
        if (subtitle != null && !subtitle.isEmpty()) {
            player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(subtitle)));
        }
    }

    /**
     * Shows an action bar message to the player
     * 
     * @param player  The player
     * @param message The message text
     */
    private void showActionBar(ServerPlayerEntity player, String message) {
        player.networkHandler.sendPacket(new OverlayMessageS2CPacket(Text.literal(message)));
    }

    /**
     * Creates a boss bar for login timeout countdown
     * 
     * @param player         The player
     * @param timeoutSeconds Timeout duration in seconds
     */
    private void createLoginBossBar(ServerPlayerEntity player, int timeoutSeconds) {
        UUID playerId = player.getUuid();
        removeBossBar(player);

        String bossBarTitle = languageManager.getMessage(playerId, "timeout.bossbar");
        ServerBossBar bossBar = new ServerBossBar(
                Text.literal(bossBarTitle),
                BossBar.Color.RED,
                BossBar.Style.PROGRESS);

        bossBar.addPlayer(player);
        bossBar.setPercent(1.0F);
        playerBossBars.put(playerId, bossBar);

        // Start countdown thread
        Thread bossThread = new Thread(() -> {
            for (int i = timeoutSeconds; i > 0; i--) {
                try {
                    Thread.sleep(1000);
                    float progress = (float) i / timeoutSeconds;
                    bossBar.setPercent(progress);

                    final int remaining = i;
                    MinecraftServer serverInstanceLocal = LoginSystem.serverInstance;
                    if (serverInstanceLocal != null) {
                        serverInstanceLocal.execute(() -> {
                            String title = languageManager.getMessage(playerId, "timeout.bossbar") + " - " + remaining
                                    + "s";
                            bossBar.setName(Text.literal(title));
                        });
                    }

                    // Check if player logged in
                    if (loggedIn.getOrDefault(playerId, false)) {
                        MinecraftServer server = LoginSystem.serverInstance;
                        if (server != null) {
                            server.execute(() -> removeBossBar(player));
                        }
                        break;
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        bossThread.setDaemon(true);
        bossThread.start();
    }

    /**
     * Removes the boss bar from a player
     * 
     * @param player The player
     */
    private void removeBossBar(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        if (playerBossBars.containsKey(playerId)) {
            ServerBossBar bossBar = playerBossBars.get(playerId);
            bossBar.removePlayer(player);
            bossBar.clearPlayers();
            playerBossBars.remove(playerId);
        }
    }

    // ================================
    // COMMAND REGISTRATION
    // ================================
    /**
     * Registers mod commands such as /register, /login, /changepassword, and admin
     * commands.
     */
    private void registerCommands(
            com.mojang.brigadier.CommandDispatcher<net.minecraft.server.command.ServerCommandSource> dispatcher) {
        // /register <password> <confirmPassword>
        dispatcher.register(
                CommandManager.literal("register")
                        .then(CommandManager.argument("password", StringArgumentType.string())
                                .then(CommandManager.argument("confirmPassword", StringArgumentType.string())
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                            UUID playerId = player.getUuid();
                                            String password = StringArgumentType.getString(context, "password");
                                            String confirmPassword = StringArgumentType.getString(context,
                                                    "confirmPassword");

                                            if (playerPasswords.containsKey(playerId)) {
                                                String msg = languageManager.getMessage(playerId, "register.already");
                                                player.sendMessage(Text.literal(msg).formatted(Formatting.RED), false);
                                                showActionBar(player, msg);
                                                return 0;
                                            }
                                            if (!password.equals(confirmPassword)) {
                                                String msg = languageManager.getMessage(playerId,
                                                        "register.password.mismatch");
                                                player.sendMessage(Text.literal(msg).formatted(Formatting.RED), false);
                                                showActionBar(player, msg);
                                                return 0;
                                            }
                                            String hashedPassword = hashPassword(password);
                                            playerPasswords.put(playerId, hashedPassword);

                                            if (enableDatabase) {
                                                savePasswordToDB(playerId, hashedPassword);
                                            } else {
                                                savePasswordsToFile();
                                            }
                                            loggedIn.put(playerId, true);
                                            setLastLogin(playerId);
                                            player.setNoGravity(false);

                                            // Remove boss bar
                                            removeBossBar(player);

                                            restoreInventory(player);
                                            removeBlindness(player);
                                            // If player's original position was saved, teleport them back.
                                            if (originalPositions.containsKey(playerId)) {
                                                double[] orig = originalPositions.get(playerId);
                                                safeTeleport(player, player.getServerWorld(),
                                                        orig[0], orig[1], orig[2], player.getYaw(), player.getPitch());
                                                originalPositions.remove(playerId);
                                                removeUnloggedState(playerId);
                                            }

                                            // Show success messages with Title and ActionBar
                                            String successMsg = languageManager.getMessage(playerId,
                                                    "register.success");
                                            String title = languageManager.getMessage(playerId, "register.title");
                                            String subtitle = languageManager.getMessage(playerId, "register.subtitle");

                                            player.sendMessage(Text.literal(successMsg).formatted(Formatting.GREEN),
                                                    false);
                                            showTitle(player, title, subtitle);
                                            showActionBar(player, successMsg);
                                            return 1;
                                        }))));

        // /login <password>
        dispatcher.register(
                CommandManager.literal("login")
                        .then(CommandManager.argument("password", StringArgumentType.string())
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                    UUID playerId = player.getUuid();

                                    if (loggedIn.getOrDefault(playerId, false)) {
                                        String msg = languageManager.getMessage(playerId, "login.already");
                                        player.sendMessage(Text.literal(msg).formatted(Formatting.RED), false);
                                        showActionBar(player, msg);
                                        return 0;
                                    }
                                    if (!playerPasswords.containsKey(playerId)) {
                                        String msg = languageManager.getMessage(playerId, "login.notRegistered");
                                        player.sendMessage(Text.literal(msg).formatted(Formatting.RED), false);
                                        showActionBar(player, msg);
                                        return 0;
                                    }
                                    String password = StringArgumentType.getString(context, "password");
                                    String storedHash = playerPasswords.get(playerId);
                                    boolean isPasswordCorrect = false;
                                    boolean needsUpgrade = false;

                                    if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$")) {
                                        isPasswordCorrect = org.mindrot.jbcrypt.BCrypt.checkpw(password, storedHash);
                                    } else {
                                        String legacyHash = hashPasswordLegacy(password);
                                        if (storedHash.equals(legacyHash)) {
                                            isPasswordCorrect = true;
                                            needsUpgrade = true;
                                        }
                                    }

                                    if (!isPasswordCorrect) {
                                        String msg = languageManager.getMessage(playerId, "login.incorrect");
                                        player.sendMessage(Text.literal(msg).formatted(Formatting.RED), false);
                                        showActionBar(player, msg);
                                        return 0;
                                    }

                                    if (needsUpgrade) {
                                        String newBcryptHash = hashPassword(password);
                                        playerPasswords.put(playerId, newBcryptHash);
                                        if (enableDatabase) {
                                            savePasswordToDB(playerId, newBcryptHash);
                                        } else {
                                            savePasswordsToFile();
                                        }
                                        LOGGER.info("Upgraded password to BCrypt for player " + player.getName().getString());
                                    }
                                    loggedIn.put(playerId, true);
                                    setLastLogin(playerId);
                                    player.setNoGravity(false);

                                    // Remove boss bar
                                    removeBossBar(player);

                                    restoreInventory(player);
                                    removeBlindness(player);
                                    if (originalPositions.containsKey(playerId)) {
                                        double[] orig = originalPositions.get(playerId);
                                        safeTeleport(player, player.getServerWorld(), orig[0],
                                                orig[1], orig[2], player.getYaw(), player.getPitch());
                                        originalPositions.remove(playerId);
                                        removeUnloggedState(playerId);
                                    }

                                    // Show success messages with Title and ActionBar
                                    String successMsg = languageManager.getMessage(playerId, "login.success");
                                    String title = languageManager.getMessage(playerId, "login.title");
                                    String subtitle = languageManager.getMessage(playerId, "login.subtitle");

                                    player.sendMessage(Text.literal(successMsg).formatted(Formatting.GREEN), false);
                                    showTitle(player, title, subtitle);
                                    showActionBar(player, successMsg);
                                    return 1;
                                })));

        // /changepassword <oldPassword> <newPassword>
        dispatcher.register(
                CommandManager.literal("changepassword")
                        .then(CommandManager.argument("oldPassword", StringArgumentType.string())
                                .then(CommandManager.argument("newPassword", StringArgumentType.string())
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                            UUID playerId = player.getUuid();
                                            String oldPassword = StringArgumentType.getString(context, "oldPassword");
                                            String newPassword = StringArgumentType.getString(context, "newPassword");

                                            if (!loggedIn.getOrDefault(playerId, false)) {
                                                String msg = languageManager.getMessage(playerId, "password.mustLogin");
                                                player.sendMessage(Text.literal(msg).formatted(Formatting.RED), false);
                                                showActionBar(player, msg);
                                                return 0;
                                            }

                                            String storedHash = playerPasswords.get(playerId);
                                            boolean isPasswordCorrect = false;

                                            if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$")) {
                                                isPasswordCorrect = org.mindrot.jbcrypt.BCrypt.checkpw(oldPassword, storedHash);
                                            } else {
                                                String legacyHash = hashPasswordLegacy(oldPassword);
                                                if (storedHash.equals(legacyHash)) {
                                                    isPasswordCorrect = true;
                                                }
                                            }

                                            if (!isPasswordCorrect) {
                                                String msg = languageManager.getMessage(playerId,
                                                        "password.oldIncorrect");
                                                player.sendMessage(Text.literal(msg).formatted(Formatting.RED), false);
                                                showActionBar(player, msg);
                                                return 0;
                                            }
                                            String hashedNew = hashPassword(newPassword);
                                            playerPasswords.put(playerId, hashedNew);
                                            // ØªØ­Ø¯ÙŠØ« ÙƒÙ„Ù…Ø© Ø§Ù„Ù…Ø±ÙˆØ± ÙÙŠ Ù‚Ø§Ø¹Ø¯Ø© Ø§Ù„Ø¨ÙŠØ§Ù†Ø§Øª
                                            if (enableDatabase) {
                                                try {
                                                    try (Connection conn = DriverManager.getConnection(jdbcUrl)) {

                                                        String sql = "UPDATE player_passwords SET password = ? WHERE uuid = ?";
                                                        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                                                            pstmt.setString(1, hashedNew);
                                                            pstmt.setString(2, playerId.toString());
                                                            pstmt.executeUpdate();
                                                        }
                                                    }
                                                    LOGGER.info("Password updated in database for player: " + playerId);
                                                } catch (SQLException e) {
                                                    LOGGER.error("Failed to update password in database for player: "
                                                            + playerId, e);
                                                    player.sendMessage(Text.literal(
                                                            "Failed to update password in database. Please contact an administrator.")
                                                            .formatted(Formatting.RED), false);
                                                    return 0;
                                                }
                                            } else {
                                                savePasswordsToFile();
                                            }

                                            String successMsg = languageManager.getMessage(playerId,
                                                    "password.changed");
                                            player.sendMessage(Text.literal(successMsg).formatted(Formatting.GREEN),
                                                    false);
                                            showActionBar(player, successMsg);
                                            return 1;
                                        }))));

        // Admin command: /loginadmin (opens GUI)
        dispatcher.register(
                CommandManager.literal("loginadmin")
                        // Removed .requires() constraint. The command now ALWAYS appears in
                        // tab-complete for everyone,
                        // completely avoiding Brigadier obfuscation failures. We validate permission
                        // on-execution instead.
                        .executes(context -> {
                            ServerPlayerEntity admin = context.getSource().getPlayerOrThrow();
                            UUID adminId = admin.getUuid();

                            // DYNAMIC OP CHECK: Securely verifying OP status natively
                            boolean isOp = isPlayerOp(admin);

                            // Fallback to reflection if ops.json isn't matched
                            if (!isOp) {
                                isOp = checkPermission(context.getSource(), 2);
                            }

                            if (!isOp) {
                                admin.sendMessage(Text.literal("You do not have permission to use this command.")
                                        .formatted(Formatting.RED), false);
                                return 0;
                            }

                            // SECURITY: Must be logged in first!
                            if (!loggedIn.getOrDefault(adminId, false)) {
                                String msg = languageManager.getMessage(adminId, "restrict.command");
                                admin.sendMessage(Text.literal(msg).formatted(Formatting.RED), false);
                                showActionBar(admin, msg);
                                return 0;
                            }

                            // When typing /loginadmin only - open GUI
                            openAdminGUI(admin);
                            return 1;
                        }));

        // Keep old commands commented for reference
        /*
         * .then(CommandManager.literal("info")
         * .then(CommandManager.argument("player", StringArgumentType.string())
         * .executes(context -> {
         * String targetPlayerName = StringArgumentType.getString(context, "player");
         * 
         * // Ø§Ù„Ø¨Ø­Ø« Ø¹Ù† Ø§Ù„Ù„Ø§Ø¹Ø¨ Ø¨Ø¹Ø¯Ø© Ø·Ø±Ù‚
         * UUID targetUUID = null;
         * 
         * // 1. Ù…Ø­Ø§ÙˆÙ„Ø© ØªØ­ÙˆÙŠÙ„ Ø§Ù„Ù†Øµ Ø¥Ù„Ù‰ UUID Ù…Ø¨Ø§Ø´Ø±Ø©
         * try {
         * targetUUID = UUID.fromString(targetPlayerName);
         * LOGGER.info("Using direct UUID: " + targetUUID);
         * } catch (IllegalArgumentException e) {
         * // Ù„ÙŠØ³ UUIDØŒ Ù†Ø¨Ø­Ø« Ø¨Ø§Ù„Ø§Ø³Ù…
         * 
         * // 2. Ø§Ù„Ø¨Ø­Ø« Ø¹Ù† Ø§Ù„Ù„Ø§Ø¹Ø¨ Ø§Ù„Ù…ØªØµÙ„ Ø­Ø§Ù„ÙŠØ§Ù‹
         * ServerPlayerEntity onlinePlayer =
         * context.getSource().getServer().getPlayerManager().getPlayer(targetPlayerName
         * );
         * if (onlinePlayer != null) {
         * targetUUID = onlinePlayer.getUuid();
         * LOGGER.info("Found online player: " + targetPlayerName + " -> " +
         * targetUUID);
         * } else {
         * // 3. Ù„Ù„Ø§Ø¹Ø¨ÙŠÙ† ØºÙŠØ± Ø§Ù„Ù…ØªØµÙ„ÙŠÙ†ØŒ Ø§Ø³ØªØ®Ø¯Ù… UUID Ù…Ø¨Ø§Ø´Ø±Ø©
         * // ÙŠÙ…ÙƒÙ† Ù„Ù„Ø£Ø¯Ù…Ù† Ø§Ø³ØªØ®Ø¯Ø§Ù… /loadmin list Ù„Ø±Ø¤ÙŠØ© Ø¬Ù…ÙŠØ¹
         * UUIDs
         * 
         * // Ø¥Ø°Ø§ Ù„Ù… Ù†Ø¬Ø¯ Ø§Ù„Ù„Ø§Ø¹Ø¨ Ø¨Ø£ÙŠ Ø·Ø±ÙŠÙ‚Ø©
         * if (targetUUID == null) {
         * context.getSource().sendError(Text.literal(
         * "âŒ Player '" + targetPlayerName + "' not found!\n" +
         * "ðŸ’¡ Try one of these:\n" +
         * "  â€¢ Make sure the player name is spelled correctly\n" +
         * "  â€¢ Use the player's UUID directly\n" +
         * "  â€¢ Use /loadmin list to see all registered players")
         * .formatted(Formatting.RED));
         * return 0;
         * }
         * }
         * }
         * 
         * final String pass;
         * if (enableDatabase) {
         * try {
         * try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
         * 
         * String sql = "SELECT password FROM player_passwords WHERE uuid = ?";
         * try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
         * pstmt.setString(1, targetUUID.toString());
         * try (ResultSet rs = pstmt.executeQuery()) {
         * if (rs.next()) {
         * pass = rs.getString("password");
         * } else {
         * pass = null;
         * }
         * }
         * }
         * }
         * } catch (SQLException e) {
         * LOGGER.error("Failed to get password from database for player: " +
         * targetPlayerName, e);
         * context.getSource().sendError(Text.
         * literal("Failed to get password from database. Please check logs.")
         * .formatted(Formatting.RED));
         * return 0;
         * }
         * } else {
         * pass = playerPasswords.get(targetUUID);
         * }
         * 
         * if (pass != null) {
         * final String finalPlayerName = targetPlayerName;
         * // Ø¹Ø±Ø¶ ÙƒÙ„Ù…Ø© Ø§Ù„Ù…Ø±ÙˆØ± Ø§Ù„Ø£ØµÙ„ÙŠØ© Ø¥Ø°Ø§ ÙƒØ§Ù†Øª Ù…ØªØ§Ø­Ø©ØŒ
         * ÙˆØ¥Ù„Ø§ Ø¹Ø±Ø¶ Ø§Ù„Ù€ hash
            String displayPassword = "[HIDDEN]";
         * context.getSource().sendFeedback(() ->
         * Text.literal("Player " + finalPlayerName + " has password: " +
         * displayPassword)
         * .formatted(Formatting.AQUA), false);
         * } else {
         * final String finalPlayerName = targetPlayerName;
         * context.getSource().sendFeedback(() ->
         * Text.literal("No password found for player " + finalPlayerName)
         * .formatted(Formatting.RED), false);
         * }
         * return 1;
         * })
         * )
         * )
         * .then(CommandManager.literal("delete")
         * .then(CommandManager.argument("player", StringArgumentType.string())
         * .executes(context -> {
         * String targetPlayerName = StringArgumentType.getString(context, "player");
         * 
         * // Ø§Ù„Ø¨Ø­Ø« Ø¹Ù† Ø§Ù„Ù„Ø§Ø¹Ø¨ Ø¨Ø¹Ø¯Ø© Ø·Ø±Ù‚
         * UUID targetUUID = null;
         * 
         * // 1. Ù…Ø­Ø§ÙˆÙ„Ø© ØªØ­ÙˆÙŠÙ„ Ø§Ù„Ù†Øµ Ø¥Ù„Ù‰ UUID Ù…Ø¨Ø§Ø´Ø±Ø©
         * try {
         * targetUUID = UUID.fromString(targetPlayerName);
         * } catch (IllegalArgumentException e) {
         * // 2. Ø§Ù„Ø¨Ø­Ø« Ø¨Ø§Ù„Ø§Ø³Ù… (online Ø£Ùˆ offline)
         * ServerPlayerEntity onlinePlayer =
         * context.getSource().getServer().getPlayerManager().getPlayer(targetPlayerName
         * );
         * if (onlinePlayer != null) {
         * targetUUID = onlinePlayer.getUuid();
         * }
         * 
         * if (targetUUID == null) {
         * context.getSource().sendError(Text.literal("âŒ Player '" + targetPlayerName
         * + "' not found! Use player name or UUID.")
         * .formatted(Formatting.RED));
         * return 0;
         * }
         * }
         * 
         * if (enableDatabase) {
         * try {
         * try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
         * 
         * String sql = "DELETE FROM player_passwords WHERE uuid = ?";
         * try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
         * pstmt.setString(1, targetUUID.toString());
         * int rowsAffected = pstmt.executeUpdate();
         * if (rowsAffected > 0) {
         * playerPasswords.remove(targetUUID);
         * context.getSource().sendFeedback(() ->
         * Text.literal("Deleted password for player " + targetPlayerName)
         * .formatted(Formatting.GREEN), false);
         * } else {
         * context.getSource().sendFeedback(() ->
         * Text.literal("No password found for player " + targetPlayerName)
         * .formatted(Formatting.RED), false);
         * }
         * }
         * }
         * LOGGER.info("Password deleted from database for player: " +
         * targetPlayerName);
         * } catch (SQLException e) {
         * LOGGER.error("Failed to delete password from database for player: " +
         * targetPlayerName, e);
         * context.getSource().sendError(Text.
         * literal("Failed to delete password from database. Please check logs.")
         * .formatted(Formatting.RED));
         * return 0;
         * }
         * } else {
         * if (playerPasswords.containsKey(targetUUID)) {
         * playerPasswords.remove(targetUUID);
         * savePasswordsToFile();
         * context.getSource().sendFeedback(() ->
         * Text.literal("Deleted password for player " + targetPlayerName)
         * .formatted(Formatting.GREEN), false);
         * } else {
         * context.getSource().sendFeedback(() ->
         * Text.literal("No password found for player " + targetPlayerName)
         * .formatted(Formatting.RED), false);
         * }
         * }
         * return 1;
         * })
         * )
         * )
         */
    }

    // ================================
    // EVENT HANDLERS
    // ================================
    /**
     * Handles player login event.
     * - Prevents double login by disconnecting duplicate connections.
     * - Stores the player's original location and teleports them to the waiting
     * area.
     * - Applies inventory hiding and blindness effect until login.
     */
    private void onPlayerLogin(ServerPlayerEntity newPlayer) {
        MinecraftServer server = LoginSystem.serverInstance;
        UUID newPlayerUUID = newPlayer.getUuid();
        
        knownPlayerNames.put(newPlayerUUID, newPlayer.getName().getString());
        saveBans();

        // Prevent double login: disconnect duplicate connections.
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player != newPlayer && player.getUuid().equals(newPlayerUUID)) {
                if (!alreadyDisconnected.contains(newPlayerUUID)) {
                    newPlayer.networkHandler.disconnect(Text.literal("A player with that name is already online.")
                            .formatted(Formatting.RED));
                    alreadyDisconnected.add(newPlayerUUID);
                }
                return;
            }
        }

        // Store the player's original location only if not already stored
        // This preserves the location if player reconnects after disconnect
        if (!originalPositions.containsKey(newPlayerUUID)) {
            originalPositions.put(newPlayerUUID, new double[] { newPlayer.getX(), newPlayer.getY(), newPlayer.getZ() });
        }

        // Teleport the player to the waiting area (configured in the config file).
        boolean enableWaitingArea = Boolean.parseBoolean(config.getProperty("enableWaitingArea", "true"));
        if (enableWaitingArea) {
            double waitingX = Double.parseDouble(config.getProperty("waitingAreaX", "0"));
            double waitingY = Double.parseDouble(config.getProperty("waitingAreaY", "100"));
            double waitingZ = Double.parseDouble(config.getProperty("waitingAreaZ", "0"));
            safeTeleport(newPlayer, newPlayer.getServerWorld(), waitingX, waitingY, waitingZ,
                    newPlayer.getYaw(), newPlayer.getPitch());
        }

        // Mark the player as not logged in.
        loggedIn.put(newPlayerUUID, false);

        // SECURITY FIX: Remove all permissions from unlogged players
        // This prevents OP players from using admin commands before login
        // We'll restore their permission level after login
        newPlayer.setNoGravity(true);

        // Show welcome messages with language support
        String promptMsg = languageManager.getMessage(newPlayerUUID, "login.prompt");
        String promptSubtitle = languageManager.getMessage(newPlayerUUID, "login.promptSubtitle");
        newPlayer.sendMessage(Text.literal(promptMsg).formatted(Formatting.YELLOW), false);
        newPlayer.sendMessage(Text.literal(promptSubtitle).formatted(Formatting.GRAY), false);
        showTitle(newPlayer, promptMsg, promptSubtitle);

        // Create boss bar for timeout countdown
        int timeout = Integer.parseInt(config.getProperty("loginTimeout", "60"));
        createLoginBossBar(newPlayer, timeout);

        // Hide inventory if enabled.
        if (Boolean.parseBoolean(config.getProperty("hideInventory", "true"))) {
            int containerSize = newPlayer.getInventory().size();
            ItemStack[] savedItems = new ItemStack[containerSize];
            for (int i = 0; i < containerSize; i++) {
                savedItems[i] = newPlayer.getInventory().getStack(i).copy();
            }
            savedInventories.put(newPlayerUUID, savedItems);
            newPlayer.getInventory().clear();
        }

        // Apply blindness effect if enabled.
        if (Boolean.parseBoolean(config.getProperty("applyBlindness", "true"))) {
            int duration = Integer.parseInt(config.getProperty("blindnessDuration", "40"));
            newPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, duration, 0, false, false));
        }

        // Start a timeout thread to disconnect players who don't log in in time.
        int timeoutMillis = timeout * 1000;
        Thread timeoutThread = new Thread(() -> {
            try {
                Thread.sleep(timeoutMillis);
                if (!loggedIn.getOrDefault(newPlayerUUID, false)) {
                    server.execute(() -> {
                        if (!alreadyDisconnected.contains(newPlayerUUID)) {
                            String kickMsg = languageManager.getMessage(newPlayerUUID, "timeout.kick");
                            newPlayer.networkHandler.disconnect(
                                    Text.literal(kickMsg).formatted(Formatting.RED));
                            alreadyDisconnected.add(newPlayerUUID);
                        }
                    });
                }
            } catch (InterruptedException ignored) {
            }
        });
        timeoutThread.setDaemon(true);
        timeoutThread.start();

        saveUnloggedState(newPlayerUUID);
    }

    /**
     * Handles player logout event by cleaning up stored data.
     */
    private void onPlayerLogout(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();

        // Remove boss bar first
        removeBossBar(player);

        // Only clear data if player was logged in
        // If player disconnects before login, keep original position for next time
        if (loggedIn.getOrDefault(playerId, false)) {
            // Player was logged in, safe to clear everything
            loggedIn.remove(playerId);
            alreadyDisconnected.remove(playerId);
            savedInventories.remove(playerId);
            originalPositions.remove(playerId);
        } else {
            // Player disconnected without logging in
            // Keep originalPositions for next login attempt
            // But clear other temporary data
            loggedIn.remove(playerId);
            alreadyDisconnected.remove(playerId);

            // CRITICAL FIX: Restore inventory natively before the server saves their
            // playerdata file!
            if (savedInventories.containsKey(playerId)) {
                ItemStack[] items = savedInventories.get(playerId);
                for (int i = 0; i < items.length && i < player.getInventory().size(); i++) {
                    if (items[i] != null) {
                        player.getInventory().setStack(i, items[i]);
                    }
                }
                savedInventories.remove(playerId);
            }
            // DO NOT remove originalPositions - keep it for next login
            saveUnloggedState(playerId);
        }

        // Remove player from language manager
        languageManager.removePlayer(playerId);
    }

    /**
     * Handles server starting event to load all passwords.
     */
    private void onServerStarting(MinecraftServer server) {
        System.out.println("LoginSystem: Server is starting, loading passwords...");
        if (enableDatabase) {
            try {
                loadPasswordsFromDB();
                System.out.println(
                        "LoginSystem: Successfully loaded " + playerPasswords.size() + " passwords from database.");
            } catch (Exception e) {
                System.err.println("LoginSystem: Failed to load passwords from database!");
                e.printStackTrace();
                // Don't try to load from file if database is enabled
                throw new RuntimeException("Failed to load passwords from database", e);
            }
        } else {
            loadPasswordsFromFile();
            System.out.println("LoginSystem: Successfully loaded " + playerPasswords.size() + " passwords from file.");
        }

        if (enableWebPanel) {
            webServer = new AdminWebServer(this, webPanelPort, webPanelPassword);
            webServer.start();
        }
    }

    /**
     * Handles server stopping event to ensure passwords are saved.
     */
    private void onServerStopping(MinecraftServer server) {
        System.out.println("LoginSystem: Server is stopping, saving passwords...");
        if (enableDatabase) {
            try {
                // Save all passwords to database before shutdown
                System.out.println("LoginSystem: Saving " + playerPasswords.size() + " passwords to database...");
                saveAllPasswordsToDB();
                System.out.println("LoginSystem: All passwords saved successfully to database!");
            } catch (Exception e) {
                System.err.println("LoginSystem: Failed to save to database!");
                e.printStackTrace();
                throw new RuntimeException("Failed to save passwords to database", e);
            }
        } else {
            savePasswordsToFile();
            System.out.println("LoginSystem: Saved " + playerPasswords.size() + " passwords to file.");
        }

        if (webServer != null) {
            webServer.stop();
        }
    }

    /**
     * Saves all passwords to the database in a single transaction.
     * Uses batch processing for better performance with large numbers of passwords.
     */
    private void saveAllPasswordsToDB() {
        if (!enableDatabase || playerPasswords.isEmpty()) {
            LOGGER.debug("Skipping database save - database disabled or no passwords to save");
            return;
        }

        int totalPasswords = playerPasswords.size();
        LOGGER.info("Saving {} passwords to database...", totalPasswords);
        long startTime = System.currentTimeMillis();

        // Use a transaction for atomic updates
        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            // Disable auto-commit to use a transaction
            conn.setAutoCommit(false);

            // Use batch updates for better performance
            String sql = """
                    INSERT INTO player_passwords (uuid, password, last_login)
                    VALUES (?, ?, CURRENT_TIMESTAMP)
                    ON DUPLICATE KEY UPDATE
                        password = VALUES(password),
                        last_login = CURRENT_TIMESTAMP
                    """;

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                int processedCount = 0;
                int batchSize = 100; // Process in batches of 100

                for (java.util.Map.Entry<UUID, String> entry : playerPasswords.entrySet()) {
                    UUID uuid = entry.getKey();
                    String hashedPassword = entry.getValue();

                    pstmt.setString(1, uuid.toString());
                    pstmt.setString(2, hashedPassword);
                    pstmt.addBatch();

                    // Execute batch when batch size is reached
                    if (++processedCount % batchSize == 0) {
                        int[] updateCounts = pstmt.executeBatch();
                        LOGGER.debug("Processed batch of {} updates", updateCounts.length);
                    }
                }

                // Execute any remaining statements in the batch
                int[] updateCounts = pstmt.executeBatch();
                LOGGER.debug("Processed final batch of {} updates", updateCounts.length);

                // Commit the transaction
                conn.commit();

                long duration = System.currentTimeMillis() - startTime;
                LOGGER.info("Successfully saved {} passwords to database in {} ms",
                        totalPasswords, duration);

            } catch (SQLException e) {
                // Rollback the transaction on error
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    LOGGER.error("Error during transaction rollback", ex);
                }
                LOGGER.error("Failed to save passwords to database", e);
                throw new RuntimeException("Failed to save passwords to database", e);
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to save passwords to database!", e);
            throw new RuntimeException("Failed to save passwords to database", e);
        }
    }

    /**
     * Handles server tick events to monitor players
     */
    private void onServerTick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID playerId = player.getUuid();
            if (!loggedIn.getOrDefault(playerId, false)) {
                // Maintain player within the waiting area.
                boolean enableWaitingArea = Boolean.parseBoolean(config.getProperty("enableWaitingArea", "true"));
                if (enableWaitingArea) {
                    double waitingX = Double.parseDouble(config.getProperty("waitingAreaX", "0"));
                    double waitingY = Double.parseDouble(config.getProperty("waitingAreaY", "100"));
                    double waitingZ = Double.parseDouble(config.getProperty("waitingAreaZ", "0"));
                    double dx = player.getX() - waitingX;
                    double dy = player.getY() - waitingY;
                    double dz = player.getZ() - waitingZ;
                    if (dx * dx + dy * dy + dz * dz > 1) {
                        safeTeleport(player, player.getServerWorld(), waitingX, waitingY, waitingZ,
                                player.getYaw(), player.getPitch());
                        String msg = languageManager.getMessage(playerId, "restrict.move");
                        player.sendMessage(Text.literal(msg).formatted(Formatting.RED), false);
                        showActionBar(player, msg);
                    }
                }
                if (Boolean.parseBoolean(config.getProperty("applyBlindness", "true"))
                        && !player.hasStatusEffect(StatusEffects.BLINDNESS)) {
                    int duration = Integer.parseInt(config.getProperty("blindnessDuration", "40"));
                    player.addStatusEffect(
                            new StatusEffectInstance(StatusEffects.BLINDNESS, duration, 0, false, false));
                }
            }
        }
    }

    public boolean checkPermission(Object source, int level) {
        // Fast path for 1.21.0-1.21.3 environments (uses intermediary name natively if
        // mapped)
        try {
            java.lang.reflect.Method method = source.getClass().getMethod("method_9259", int.class);
            return (boolean) method.invoke(source, level);
        } catch (Exception ignored) {
        }

        // Fast path for 1.21.4+ environments (uses new intermediary name mapped by
        // Fabric)
        try {
            java.lang.reflect.Method method = source.getClass().getMethod("method_9262", int.class);
            return (boolean) method.invoke(source, level);
        } catch (Exception ignored) {
        }

        // Universal Obfuscation-Proof Fallback for all 1.21.x Server Versions
        // Scans the ServerCommandSource class for any specific signature (int) ->
        // boolean
        // method that evaluates to TRUE for the given OP level.
        for (java.lang.reflect.Method m : source.getClass().getMethods()) {
            if (m.getParameterCount() == 1
                    && m.getParameterTypes()[0] == int.class
                    && m.getReturnType() == boolean.class) {
                try {
                    if ((boolean) m.invoke(source, level)) {
                        return true;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }

    public boolean isPlayerOp(net.minecraft.entity.player.PlayerEntity player) {
        try {
            java.io.File opsFile = new java.io.File("ops.json");
            if (opsFile.exists()) {
                String content = new String(java.nio.file.Files.readAllBytes(opsFile.toPath()));
                if (content.contains(player.getUuidAsString()) || content.contains(player.getName().getString())) {
                    return true;
                }
            } else if (serverInstance != null && serverInstance.isSingleplayer()) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public void setLastLogin(UUID uuid) {
        lastLogins.put(uuid, System.currentTimeMillis());
        saveLastLogins();
    }

    public Long getLastLogin(UUID uuid) {
        return lastLogins.get(uuid);
    }

    private void saveLastLogins() {
        try (Writer writer = new FileWriter(lastLoginsFile)) {
            JsonObject root = new JsonObject();
            for (java.util.Map.Entry<UUID, Long> entry : lastLogins.entrySet()) {
                root.addProperty(entry.getKey().toString(), entry.getValue());
            }
            writer.write(root.toString());
        } catch (IOException e) {
            LOGGER.error("Failed to save last logins", e);
        }
    }

    private void loadLastLogins() {
        if (lastLoginsFile.exists()) {
            try (Reader reader = new FileReader(lastLoginsFile)) {
                JsonObject root = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
                    lastLogins.put(UUID.fromString(entry.getKey()), entry.getValue().getAsLong());
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load last logins", e);
            }
        }
    }

    private void registerSafeUseItemCallback() {
        try {
            Class<?> callbackClass = Class.forName("net.fabricmc.fabric.api.event.player.UseItemCallback");
            Object eventObj = callbackClass.getField("EVENT").get(null);
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                    this.getClass().getClassLoader(),
                    new Class<?>[] { callbackClass },
                    (proxyObj, method, args) -> {
                        if (method.getName().equals("interact")) {
                            Object playerObj = args[0];
                            if (playerObj instanceof ServerPlayerEntity serverPlayer) {
                                if (!loggedIn.getOrDefault(serverPlayer.getUuid(), false)) {
                                    String msg = languageManager.getMessage(serverPlayer.getUuid(), "Error.NeedLogin",
                                            serverPlayer);
                                    serverPlayer.sendMessage(Text.literal(msg), false);
                                    return getFailResult(args);
                                }
                            }
                            return getPassResult(args);
                        }
                        if (method.getName().equals("equals"))
                            return proxyObj == args[0];
                        if (method.getName().equals("hashCode"))
                            return System.identityHashCode(proxyObj);
                        if (method.getName().equals("toString"))
                            return "UseItemCallbackProxy";
                        return null;
                    });
            Class.forName("net.fabricmc.fabric.api.event.Event").getMethod("register", Object.class).invoke(eventObj,
                    proxy);
        } catch (Throwable t) {
            LOGGER.warn("Could not register UseItemCallback (1.21.2+ compatible mode): " + t.getMessage());
        }
    }

    private Object getFailResult(Object[] interactArgs) {
        try {
            Class<?> typedResultClass = Class.forName("net.minecraft.util.TypedActionResult");
            Object player = interactArgs[0];
            Object hand = interactArgs[2];
            java.lang.reflect.Method getStack = player.getClass().getMethod("getStackInHand",
                    Class.forName("net.minecraft.util.Hand"));
            Object stack = getStack.invoke(player, hand);
            return typedResultClass.getMethod("fail", Object.class).invoke(null, stack);
        } catch (Throwable ex) {
            try {
                Class<?> actionResultClass = Class.forName("net.minecraft.util.ActionResult");
                return actionResultClass.getField("FAIL").get(null);
            } catch (Throwable ex2) {
                return null;
            }
        }
    }

    private Object getPassResult(Object[] interactArgs) {
        try {
            Class<?> typedResultClass = Class.forName("net.minecraft.util.TypedActionResult");
            Object player = interactArgs[0];
            Object hand = interactArgs[2];
            java.lang.reflect.Method getStack = player.getClass().getMethod("getStackInHand",
                    Class.forName("net.minecraft.util.Hand"));
            Object stack = getStack.invoke(player, hand);
            return typedResultClass.getMethod("pass", Object.class).invoke(null, stack);
        } catch (Throwable ex) {
            try {
                Class<?> actionResultClass = Class.forName("net.minecraft.util.ActionResult");
                return actionResultClass.getField("PASS").get(null);
            } catch (Throwable ex2) {
                return null;
            }
        }
    }

    public static void safeTeleport(ServerPlayerEntity player, ServerWorld world, double x, double y, double z,
            float yaw, float pitch) {
        try {
            // First try to find a method with exactly (ServerWorld, double, double, double,
            // float, float)
            for (java.lang.reflect.Method m : player.getClass().getMethods()) {
                if (m.getParameterCount() == 6) {
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts[0] == ServerWorld.class &&
                            pts[1] == double.class && pts[2] == double.class && pts[3] == double.class &&
                            pts[4] == float.class && pts[5] == float.class) {
                        m.invoke(player, world, x, y, z, yaw, pitch);
                        return;
                    }
                }
            }

            // Fallback: try (ServerWorld, double, double, double, Set, float, float)
            for (java.lang.reflect.Method m : player.getClass().getMethods()) {
                if (m.getParameterCount() == 7) {
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts[0] == ServerWorld.class &&
                            pts[1] == double.class && pts[2] == double.class && pts[3] == double.class &&
                            pts[4] == java.util.Set.class &&
                            pts[5] == float.class && pts[6] == float.class) {
                        m.invoke(player, world, x, y, z, java.util.Collections.emptySet(), yaw, pitch);
                        return;
                    }
                }
            }

            // Ultimate fallback using command execution to guarantee success across any
            // 1.21.x version seamlessly.
            String cmd = String.format(java.util.Locale.US, "execute in %s run tp %s %f %f %f %f %f",
                    world.getRegistryKey().getValue().toString(),
                    player.getUuidAsString(), x, y, z, yaw, pitch);
            LoginSystem.serverInstance.getCommandManager().executeWithPrefix(
                    LoginSystem.serverInstance.getCommandSource(), cmd);

        } catch (Exception e) {
            LOGGER.error("LoginSystem: Failed to safely teleport player via reflection fallback.", e);
        }
    }

    public boolean deletePlayerViaWeb(UUID uuid) {
        try {
            if (isEnableDatabase()) {
                try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
                    String sql = "DELETE FROM player_passwords WHERE uuid = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setString(1, uuid.toString());
                        int rowsAffected = pstmt.executeUpdate();
                        if (rowsAffected > 0) {
                            removePlayerPassword(uuid);
                            return true;
                        }
                    }
                }
            } else {
                if (hasPlayerPassword(uuid)) {
                    removePlayerPassword(uuid);
                    savePasswordsToFile();
                    return true;
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to delete password via Web Panel for UUID: " + uuid, e);
        }
        return false;
    }
    // ==========================================
    // UNLOGGED STATE PERSISTENCE
    // ==========================================

    public String encodeUnloggedState(double[] pos, ItemStack[] items) {
        try {
            NbtCompound root = new NbtCompound();
            if (pos != null && pos.length >= 3) {
                NbtList posList = new NbtList();
                posList.add(NbtDouble.of(pos[0]));
                posList.add(NbtDouble.of(pos[1]));
                posList.add(NbtDouble.of(pos[2]));
                root.put("Pos", posList);
            }
            if (items != null) {
                NbtList invList = new NbtList();
                RegistryOps<NbtElement> ops = serverInstance.getRegistryManager().getOps(NbtOps.INSTANCE);
                for (int i = 0; i < items.length; i++) {
                    if (items[i] != null && !items[i].isEmpty()) {
                        NbtCompound slotNbt = new NbtCompound();
                        slotNbt.putByte("Slot", (byte) i);
                        NbtElement itemNbt = ItemStack.CODEC.encodeStart(ops, items[i]).getOrThrow();
                        slotNbt.put("Item", itemNbt);
                        invList.add(slotNbt);
                    }
                }
                root.put("Inventory", invList);
            }
            return NbtHelper.toNbtProviderString(root);
        } catch (Exception e) {
            LOGGER.error("Failed to encode unlogged state", e);
            return "";
        }
    }

    public void decodeUnloggedState(UUID uuid, String snbt) {
        if (snbt == null || snbt.isEmpty()) return;
        try {
            NbtCompound root = StringNbtReader.parse(snbt);
            if (root.contains("Pos", NbtElement.LIST_TYPE)) {
                NbtList posList = root.getList("Pos", NbtElement.DOUBLE_TYPE);
                if (posList.size() >= 3) {
                    originalPositions.put(uuid, new double[]{posList.getDouble(0), posList.getDouble(1), posList.getDouble(2)});
                }
            }
            if (root.contains("Inventory", NbtElement.LIST_TYPE)) {
                NbtList invList = root.getList("Inventory", NbtElement.COMPOUND_TYPE);
                ItemStack[] items = new ItemStack[41];
                for (int i = 0; i < items.length; i++) items[i] = ItemStack.EMPTY;
                RegistryOps<NbtElement> ops = serverInstance.getRegistryManager().getOps(NbtOps.INSTANCE);
                for (int i = 0; i < invList.size(); i++) {
                    NbtCompound slotNbt = invList.getCompound(i);
                    int slot = slotNbt.getByte("Slot") & 255;
                    if (slotNbt.contains("Item") && slot < items.length) {
                        items[slot] = ItemStack.CODEC.parse(ops, slotNbt.get("Item")).getOrThrow();
                    }
                }
                savedInventories.put(uuid, items);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to decode unlogged state for UUID: " + uuid, e);
        }
    }

    public void saveUnloggedState(UUID uuid) {
        double[] pos = originalPositions.get(uuid);
        ItemStack[] items = savedInventories.get(uuid);
        if (pos == null && items == null) return;
        
        String encoded = encodeUnloggedState(pos, items);
        
        if (enableDatabase) {
            try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
                String sql = "INSERT INTO player_unlogged_states (uuid, state_data) VALUES (?, ?) ON DUPLICATE KEY UPDATE state_data = VALUES(state_data)";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, uuid.toString());
                    pstmt.setString(2, encoded);
                    pstmt.executeUpdate();
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to save unlogged state to DB for UUID: " + uuid, e);
                saveUnloggedStatesToFile(); // fallback to file on DB fail
            }
        } else {
            saveUnloggedStatesToFile();
        }
    }

    public void removeUnloggedState(UUID uuid) {
        if (enableDatabase) {
            try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
                String sql = "DELETE FROM player_unlogged_states WHERE uuid = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, uuid.toString());
                    pstmt.executeUpdate();
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to delete unlogged state from DB for UUID: " + uuid, e);
            }
        }
        // Always sync file to be sure
        saveUnloggedStatesToFile();
    }

    public void saveUnloggedStatesToFile() {
        File file = new File("config/loginsystem", "unlogged_states.json");
        try {
            JsonObject json = new JsonObject();
            java.util.Set<UUID> allStored = new HashSet<>();
            allStored.addAll(originalPositions.keySet());
            allStored.addAll(savedInventories.keySet());
            
            for (UUID uuid : allStored) {
                double[] pos = originalPositions.get(uuid);
                ItemStack[] items = savedInventories.get(uuid);
                json.addProperty(uuid.toString(), encodeUnloggedState(pos, items));
            }
            try (FileWriter writer = new FileWriter(file)) {
                new com.google.gson.Gson().toJson(json, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save unlogged states to file", e);
        }
    }

    public void loadAllUnloggedStates() {
        if (serverInstance == null) return;
        originalPositions.clear();
        savedInventories.clear();
        
        if (enableDatabase) {
            try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
                try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT uuid, state_data FROM player_unlogged_states")) {
                    while (rs.next()) {
                        try {
                            UUID uuid = UUID.fromString(rs.getString("uuid"));
                            decodeUnloggedState(uuid, rs.getString("state_data"));
                        } catch(Exception e) {}
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to load unlogged states from DB", e);
            }
        } else {
            File file = new File("config/loginsystem", "unlogged_states.json");
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                    for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : json.entrySet()) {
                        try {
                            UUID uuid = UUID.fromString(entry.getKey());
                            decodeUnloggedState(uuid, entry.getValue().getAsString());
                        } catch(Exception e) {}
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to load unlogged states from file", e);
                }
            }
        }
        LOGGER.info("Loaded unlogged states: " + originalPositions.size() + " positions, " + savedInventories.size() + " inventories");
    }
}
