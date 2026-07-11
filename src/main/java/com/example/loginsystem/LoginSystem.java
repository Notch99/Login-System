package com.example.loginsystem;

import net.minecraftforge.fml.common.Mod;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.NbtIo;
import com.mojang.authlib.GameProfile;
import net.minecraft.world.BossEvent;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import net.minecraft.core.HolderLookup;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.Base64;
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
 * - Action Bar: Provides visual feedback via action bar messages.
 * - Blindness effect: Unlogged players have a blindness effect applied.
 * - Double login prevention.
 * - Admin commands to view or delete stored passwords.
 */
@Mod("loginsystem")
@SuppressWarnings("null")
public class LoginSystem {
    public static final Logger LOGGER = LogManager.getLogger();
    // ================================
    // DATA STRUCTURES & GLOBAL VARIABLES
    // ================================
    // Stores hashed passwords for each player (key: player's UUID, value: hashed
    // password).
    private final HashMap<UUID, String> playerPasswords = new HashMap<>();
    // Tracks whether a player has successfully logged in.
    public static final HashMap<UUID, Boolean> loggedIn = new HashMap<>();
    // Stores the player's original location (to be restored after login).
    private final HashMap<UUID, double[]> originalPositions = new HashMap<>();
    // Prevents disconnecting the same player multiple times.
    private final HashSet<UUID> alreadyDisconnected = new HashSet<>();
    // Stores players' inventories to be restored after login.
    private final HashMap<UUID, ItemStack[]> savedInventories = new HashMap<>();
    // Boss bars for tracking login timeout for each player
    private final HashMap<UUID, ServerBossEvent> playerBossBars = new HashMap<>();
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
    // Directory for storing player data (inventory, position) when database is
    // disabled
    private final File playerDataDir = new File("config/loginsystem/playerdata");

    // Database settings (retrieved from the config file).
    private boolean enableDatabase;
    private String jdbcUrl;
    private String dbHost;
    private String dbPort;
    private String dbName;
    private String dbUsername;
    private String dbPassword;
    // Custom Ban System
    public static java.util.Map<java.util.UUID, Long> tempBans = new java.util.concurrent.ConcurrentHashMap<>();
    public static java.util.Map<java.util.UUID, String> knownPlayerNames = new java.util.concurrent.ConcurrentHashMap<>();

    public static java.util.Set<java.util.UUID> mutedPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static java.util.Map<java.util.UUID, Long> lastLogins = new java.util.concurrent.ConcurrentHashMap<>();

    // Maps UUID to login timer (in ticks). 60 seconds = 1200 ticks.
    public static final java.util.Map<java.util.UUID, Integer> loginTimers = new java.util.concurrent.ConcurrentHashMap<>();

    // Store original admin passwords - removed

    // Web Panel settings
    private boolean enableWebPanel;
    private int webPanelPort;
    private String webPanelPassword;
    private AdminWebServer webServer;

    // ================================
    // FAILED LOGIN TRACKING (ADMIN ALERTS)
    // ================================
    // Tracks failed login attempts per player (key: player UUID, value: attempt
    // count)
    private final HashMap<UUID, Integer> failedLoginAttempts = new HashMap<>();

    // ================================
    // CONSTRUCTOR: MOD INITIALIZATION
    // ================================
    public LoginSystem() {
        // Register this mod to listen for events.
        EventRegistrationHelper.registerAllEvents(this);
        // Load (or create) the configuration file first
        loadConfig();
        // Initialize language manager with default language from config
        String defaultLang = config.getProperty("defaultLanguage", "en");
        languageManager = new LanguageManager(LOGGER, defaultLang);
        LOGGER.info("✅ Language system initialized with support for 5 languages (default: " + defaultLang + ")");
        // Retrieve database settings from the config.
        enableDatabase = Boolean.parseBoolean(config.getProperty("enableDatabase", "false"));

        if (!enableDatabase) {
            LOGGER.info("💾 Database is DISABLED in configuration - using file storage");
        } else {
            LOGGER.info("🗃️ Database is ENABLED - embedded JDBC drivers available");
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
                    LOGGER.info("✅ MySQL JDBC driver loaded successfully (embedded)");
                    jdbcUrl = originalJdbcUrl; // Keep original MySQL format
                    driverLoaded = true;
                } catch (ClassNotFoundException e) {
                    LOGGER.warn("⚠️ MySQL JDBC driver not found, trying MariaDB driver...");

                    // Try to load MariaDB JDBC driver as fallback
                    try {
                        Class.forName("org.mariadb.jdbc.Driver");
                        LOGGER.info("✅ MariaDB JDBC driver loaded successfully (embedded)");
                        // Update JDBC URL to use MariaDB format
                        jdbcUrl = jdbcUrl.replace("jdbc:mysql://", "jdbc:mariadb://");
                        driverLoaded = true;
                    } catch (ClassNotFoundException ex) {
                        LOGGER.error("❌ Neither MySQL nor MariaDB JDBC driver found in the classpath!");
                        throw new RuntimeException("No compatible JDBC driver found. Please check your dependencies.",
                                ex);
                    }
                }

                if (driverLoaded) {
                    // Set global login timeout to 3 seconds to prevent Watchdog Server crashes if
                    // DB is offline!
                    DriverManager.setLoginTimeout(3);
                    // Test the connection
                    try (Connection testConn = DriverManager.getConnection(jdbcUrl)) {
                        if (testConn.isValid(3)) { // 3 second timeout
                            // Initialize the database table if it doesn't exist.
                            initDatabase();
                            // Load existing player passwords from the database.
                            loadPasswordsFromDB();
                            LOGGER.info("✅ Successfully connected to database: " + dbName);
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("❌ Failed to connect to MySQL database: " + e.getMessage());
                LOGGER.error("Please check your database configuration in config/loginsystem.properties");
                LOGGER.error("Falling back to file storage");
                enableDatabase = false;
                loadPasswordsFromFile();
            } catch (Exception e) {
                LOGGER.error("❌ Failed to initialize database: " + e.getMessage());
                LOGGER.error("Falling back to file storage");
                enableDatabase = false;
                loadPasswordsFromFile();
            }
        } else {
            // Load stored passwords from a local file.
            LOGGER.info("Using file-based storage (database disabled in config)");
            loadPasswordsFromFile();
        }
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
     * - Visual Effects settings (blindness effect)
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
            String configContent = "# 🚀 Login System Mod Configuration File 🚀\n"
                    + "# This file contains configuration settings for the Login System mod.\n"
                    + "# Adjust the values below according to your server's needs.\n\n"
                    + "# ----------------------------\n"
                    + "# General Settings\n"
                    + "# ----------------------------\n"
                    + "# Maximum time (in seconds) a player can remain in the waiting area before being disconnected.\n"
                    + "loginTimeout=60\n"
                    + "# Default language for new players (en, ar, fr, de, zh)\n"
                    + "defaultLanguage=en\n\n"
                    + "# ----------------------------\n"
                    + "# Messages\n"
                    + "# ----------------------------\n"
                    + "# Message when registration is successful.\n"
                    + "message.registerSuccess=Registration successful! 🎉\n"
                    + "# Message when login is successful.\n"
                    + "message.loginSuccess=Login successful! ✅\n"
                    + "# Message for incorrect password.\n"
                    + "message.incorrectPassword=Incorrect password! ❌\n"
                    + "# Message when a player tries to login without registering.\n"
                    + "message.notRegistered=You are not registered! Use /register first. ⚠️\n"
                    + "# Message when a player attempts to register again.\n"
                    + "message.alreadyRegistered=You are already registered! ⚠️\n"
                    + "# Message when a player is kicked for timeout.\n"
                    + "message.kickTimeout=You were kicked for not logging in! ⏰\n\n"
                    + "# ----------------------------\n"
                    + "# Visual Effects\n"
                    + "# ----------------------------\n"
                    + "# If true, applies a blindness effect to unlogged players.\n"
                    + "applyBlindness=true\n"
                    + "# Duration (in ticks) for the blindness effect (20 ticks = 1 second).\n"
                    + "blindnessDuration=40\n\n"
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
                    + "# Waiting Area Settings\n"
                    + "# ----------------------------\n"
                    + "# Coordinates for the waiting area where unlogged players will be teleported.\n"
                    + "waitingAreaX=0\n"
                    + "waitingAreaY=100\n"
                    + "waitingAreaZ=0\n\n"
                    + "# ----------------------------\n"
                    + "# Web Panel Settings\n"
                    + "# ----------------------------\n"
                    + "enableWebPanel=true\n"
                    + "webPanelPort=8080\n"
                    + "webPanelPassword=admin123\n\n"
                    + "# ----------------------------\n"
                    + "# Admin Alert Settings\n"
                    + "# ----------------------------\n"
                    + "# Enable admin alerts for suspicious login attempts\n"
                    + "enableAdminAlerts=true\n"
                    + "# Number of failed login attempts before alerting admins\n"
                    + "maxFailedAttempts=3\n";
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
            LOGGER.info("🔍 Configuration loaded from: " + configFile.getAbsolutePath());
            LOGGER.info("🔍 enableDatabase = " + enableDatabase);
            LOGGER.info("🔍 database.host = " + dbHost);
            LOGGER.info("🔍 database.username = " + dbUsername);
            LOGGER.info("🔍 database.name = " + dbName);

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
                // Create the passwords table if it doesn't exist
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS player_passwords (
                            uuid VARCHAR(36) PRIMARY KEY,
                            password TEXT NOT NULL,
                            last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");

                LOGGER.info("Table created or already exists: player_passwords");

                // Create the player_data table for storing inventory and position
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS player_data (
                            uuid VARCHAR(36) PRIMARY KEY,
                            inventory LONGTEXT,
                            position_x DOUBLE,
                            position_y DOUBLE,
                            position_z DOUBLE,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");

                LOGGER.info("Table created or already exists: player_data");

                // Check if we need to add the last_login column (for backward compatibility)
                try {
                    stmt.execute(
                            "ALTER TABLE player_passwords ADD COLUMN IF NOT EXISTS last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
                } catch (SQLException e) {
                    // Column might already exist, ignore
                    LOGGER.debug("Column last_login already exists or error adding it: " + e.getMessage());
                }

                // Create the player_last_location table for storing last known position
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS player_last_location (
                            uuid VARCHAR(36) PRIMARY KEY,
                            last_x DOUBLE NOT NULL,
                            last_y DOUBLE NOT NULL,
                            last_z DOUBLE NOT NULL,
                            saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");

                LOGGER.info("Table created or already exists: player_last_location");
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
    private void savePasswordToDB(UUID uuid, String password) {
        if (!enableDatabase)
            return;
        if (password == null || password.trim().isEmpty()) {
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
            pstmt.setString(2, password.trim());

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
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    playerPasswords.put(UUID.fromString(parts[0]), parts[1]);
                    count++;
                }
            }
            System.out.println("LoginSystem: Loaded " + count + " passwords from file.");
        } catch (IOException e) {
            System.err.println("LoginSystem: Error reading password file!");
            e.printStackTrace();
        }
    }

    /**
     * Helper methods for AdminGUIMenu
     */
    public String getPasswordForPlayer(UUID uuid) {
        return "[HIDDEN]";
    }

    public boolean hasPlayerPassword(UUID uuid) {
        return playerPasswords.containsKey(uuid);
    }

    public void removePlayerPassword(UUID uuid) {
        playerPasswords.remove(uuid);
    }

    public boolean isEnableDatabase() {
        return enableDatabase;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    /**
     * Saves the current playerPasswords map to the local file.
     */
    public void savePasswordsToFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(passwordFile))) {
            int count = 0;
            for (UUID uuid : playerPasswords.keySet()) {
                writer.write(uuid.toString() + ":" + playerPasswords.get(uuid));
                writer.newLine();
                count++;
            }
            System.out.println("LoginSystem: Saved " + count + " passwords to file.");
        } catch (IOException e) {
            System.err.println("LoginSystem: Error writing password file!");
            e.printStackTrace();
        }
    }

    // ================================
    // ADMIN ALERT METHODS
    // ================================
    /**
     * Gets the player's IP address.
     */
    private String getPlayerIP(ServerPlayer player) {
        try {
            String address = player.connection.getRemoteAddress().toString();
            // Remove leading slash and port
            if (address.startsWith("/")) {
                address = address.substring(1);
            }
            int colonIndex = address.lastIndexOf(':');
            if (colonIndex > 0) {
                address = address.substring(0, colonIndex);
            }
            return address;
        } catch (Exception e) {
            LOGGER.error("Failed to get player IP", e);
            return "unknown";
        }
    }

    /**
     * Records a failed login attempt and alerts admins if threshold is reached.
     */
    private void recordFailedLoginAttempt(ServerPlayer player) {
        UUID playerId = player.getUUID();
        int attempts = failedLoginAttempts.getOrDefault(playerId, 0) + 1;
        failedLoginAttempts.put(playerId, attempts);

        int maxAttempts = Integer.parseInt(config.getProperty("maxFailedAttempts", "3"));

        if (attempts >= maxAttempts && Boolean.parseBoolean(config.getProperty("enableAdminAlerts", "true"))) {
            // Alert all online admins
            alertAdmins(player, attempts);
        }
    }

    /**
     * Resets failed login attempts for a player after successful login.
     */
    private void resetFailedLoginAttempts(UUID playerId) {
        failedLoginAttempts.remove(playerId);
    }

    /**
     * Sends an alert to all online admins about suspicious login attempts.
     */
    private void alertAdmins(ServerPlayer suspiciousPlayer, int attempts) {
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null)
            return;

        String playerName = suspiciousPlayer.getName().getString();
        String playerIP = getPlayerIP(suspiciousPlayer);

        Component alertMessage = Component.literal("")
                .append(Component.literal("⚠ SECURITY ALERT ⚠").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal("\n"))
                .append(Component.literal("Player: ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(playerName).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("\n"))
                .append(Component.literal("IP: ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(playerIP).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("\n"))
                .append(Component.literal("Failed attempts: ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(String.valueOf(attempts)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal("\n"))
                .append(Component.literal("Action: Possible brute-force attack!").withStyle(ChatFormatting.RED));

        // Send to all players with permission level 2 or higher (operators)
        for (ServerPlayer admin : server.getPlayerList().getPlayers()) {
            if (PermissionHelper.hasPermissions(admin, server, 2)) {
                admin.sendSystemMessage(alertMessage);
                // Also show as action bar
                showActionBar(admin, "§c⚠ " + playerName + " has " + attempts + " failed login attempts!");
            }
        }

        // Log to console
        LOGGER.warn("SECURITY ALERT: Player {} (IP: {}) has {} failed login attempts!", playerName, playerIP, attempts);
    }

    // ================================
    // PERSISTENT PLAYER DATA STORAGE
    // ================================
    /**
     * Serializes an inventory (ItemStack array) to a Base64-encoded NBT string.
     */
    public static net.minecraft.nbt.Tag safeSerializeItemStack(net.minecraft.world.item.ItemStack stack,
            net.minecraft.core.HolderLookup.Provider registries) {
        try {
            for (java.lang.reflect.Method m : net.minecraft.world.item.ItemStack.class.getMethods()) {
                if (m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isAssignableFrom(net.minecraft.core.HolderLookup.Provider.class)) {
                    if (m.getName().contains("save") || m.getName().startsWith("m_")) {
                        Object res = m.invoke(stack, registries);
                        if (res instanceof net.minecraft.nbt.Tag)
                            return (net.minecraft.nbt.Tag) res;
                    }
                }
            }
        } catch (Throwable t) {
        }
        return new net.minecraft.nbt.CompoundTag();
    }

    public static net.minecraft.world.item.ItemStack safeDeserializeItemStack(
            net.minecraft.core.HolderLookup.Provider registries, net.minecraft.nbt.Tag tag) {
        try {
            for (java.lang.reflect.Method m : net.minecraft.world.item.ItemStack.class.getMethods()) {
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 2) {
                    if (m.getParameterTypes()[0].isAssignableFrom(net.minecraft.core.HolderLookup.Provider.class) &&
                            net.minecraft.nbt.Tag.class.isAssignableFrom(m.getParameterTypes()[1])) {

                        if (m.getName().contains("parse") || m.getName().startsWith("m_")) {
                            Object res = m.invoke(null, registries, tag);
                            if (res instanceof net.minecraft.world.item.ItemStack)
                                return (net.minecraft.world.item.ItemStack) res;
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
        } catch (Throwable t) {
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    private String serializeInventory(ItemStack[] inventory) {
        try {
            HolderLookup.Provider registries = ServerLifecycleHooks.getCurrentServer().registryAccess();
            CompoundTag root = new CompoundTag();
            ListTag itemList = new ListTag();

            for (int i = 0; i < inventory.length; i++) {
                ItemStack stack = inventory[i];
                if (stack != null && !stack.isEmpty()) {
                    net.minecraft.nbt.Tag itemTag = safeSerializeItemStack(stack, registries);
                    if (itemTag instanceof CompoundTag)
                        NBTHelper.putInt((CompoundTag) itemTag, "Slot", i);
                    itemList.add(itemTag);
                }
            }
            root.put("Items", itemList);
            NBTHelper.putInt(root, "Size", inventory.length);

            // Convert NBT to bytes then to Base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            NbtIo.write(root, dos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            LOGGER.error("Failed to serialize inventory", e);
            return null;
        }
    }

    /**
     * Deserializes a Base64-encoded NBT string back to an ItemStack array.
     */
    private ItemStack[] deserializeInventory(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            HolderLookup.Provider registries = ServerLifecycleHooks.getCurrentServer().registryAccess();
            byte[] bytes = Base64.getDecoder().decode(data);
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            DataInputStream dis = new DataInputStream(bais);
            CompoundTag root = NbtIo.read(dis);

            int size = NBTHelper.getInt(root, "Size");
            ItemStack[] inventory = new ItemStack[size];

            // Initialize all slots to empty
            for (int i = 0; i < size; i++) {
                inventory[i] = ItemStack.EMPTY;
            }

            ListTag itemList = NBTHelper.getList(root, "Items", 10); // 10 = CompoundTag type
            for (int i = 0; i < NBTHelper.size(itemList); i++) {
                CompoundTag itemTag = NBTHelper.getCompound(itemList, i);
                int slot = NBTHelper.getInt(itemTag, "Slot");
                if (slot >= 0 && slot < size) {
                    inventory[slot] = (ItemStack) CompatibilityHelper.parseItemStack(registries, itemTag);
                }
            }

            return inventory;
        } catch (Exception e) {
            LOGGER.error("Failed to deserialize inventory", e);
            return null;
        }
    }

    /**
     * Saves player data (inventory and position) to persistent storage.
     * Uses database if enabled, otherwise saves to file.
     */
    private void savePlayerDataPersistent(UUID playerId, ItemStack[] inventory, double[] position) {
        String inventoryData = serializeInventory(inventory);

        if (enableDatabase) {
            savePlayerDataToDB(playerId, inventoryData, position);
        } else {
            savePlayerDataToFile(playerId, inventoryData, position);
        }
    }

    /**
     * Saves player data to database.
     */
    private void savePlayerDataToDB(UUID playerId, String inventoryData, double[] position) {
        String sql = """
                INSERT INTO player_data (uuid, inventory, position_x, position_y, position_z)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    inventory = VALUES(inventory),
                    position_x = VALUES(position_x),
                    position_y = VALUES(position_y),
                    position_z = VALUES(position_z)
                """;

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, playerId.toString());
            pstmt.setString(2, inventoryData);
            pstmt.setDouble(3, position[0]);
            pstmt.setDouble(4, position[1]);
            pstmt.setDouble(5, position[2]);

            pstmt.executeUpdate();
            LOGGER.info("Saved player data to database for: " + playerId);
        } catch (SQLException e) {
            LOGGER.error("Failed to save player data to database for: " + playerId, e);
        }
    }

    /**
     * Saves player data to file.
     */
    private void savePlayerDataToFile(UUID playerId, String inventoryData, double[] position) {
        // Ensure directory exists
        if (!playerDataDir.exists()) {
            playerDataDir.mkdirs();
        }

        File playerFile = new File(playerDataDir, playerId.toString() + ".dat");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(playerFile))) {
            writer.write("inventory=" + (inventoryData != null ? inventoryData : ""));
            writer.newLine();
            writer.write("posX=" + position[0]);
            writer.newLine();
            writer.write("posY=" + position[1]);
            writer.newLine();
            writer.write("posZ=" + position[2]);
            writer.newLine();
            LOGGER.info("Saved player data to file for: " + playerId);
        } catch (IOException e) {
            LOGGER.error("Failed to save player data to file for: " + playerId, e);
        }
    }

    /**
     * Loads player data from persistent storage.
     * Returns null if no data exists.
     */
    private Object[] loadPlayerDataPersistent(UUID playerId) {
        if (enableDatabase) {
            return loadPlayerDataFromDB(playerId);
        } else {
            return loadPlayerDataFromFile(playerId);
        }
    }

    /**
     * Loads player data from database.
     * Returns Object[] {ItemStack[] inventory, double[] position} or null.
     */
    private Object[] loadPlayerDataFromDB(UUID playerId) {
        String sql = "SELECT inventory, position_x, position_y, position_z FROM player_data WHERE uuid = ?";

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, playerId.toString());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String inventoryData = rs.getString("inventory");
                double posX = rs.getDouble("position_x");
                double posY = rs.getDouble("position_y");
                double posZ = rs.getDouble("position_z");

                ItemStack[] inventory = deserializeInventory(inventoryData);
                double[] position = new double[] { posX, posY, posZ };

                LOGGER.info("Loaded player data from database for: " + playerId);
                return new Object[] { inventory, position };
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load player data from database for: " + playerId, e);
        }
        return null;
    }

    /**
     * Loads player data from file.
     * Returns Object[] {ItemStack[] inventory, double[] position} or null.
     */
    private Object[] loadPlayerDataFromFile(UUID playerId) {
        File playerFile = new File(playerDataDir, playerId.toString() + ".dat");
        if (!playerFile.exists()) {
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(playerFile))) {
            String inventoryData = null;
            double posX = 0, posY = 0, posZ = 0;

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("inventory=")) {
                    inventoryData = line.substring("inventory=".length());
                } else if (line.startsWith("posX=")) {
                    posX = Double.parseDouble(line.substring("posX=".length()));
                } else if (line.startsWith("posY=")) {
                    posY = Double.parseDouble(line.substring("posY=".length()));
                } else if (line.startsWith("posZ=")) {
                    posZ = Double.parseDouble(line.substring("posZ=".length()));
                }
            }

            ItemStack[] inventory = deserializeInventory(inventoryData);
            double[] position = new double[] { posX, posY, posZ };

            LOGGER.info("Loaded player data from file for: " + playerId);
            return new Object[] { inventory, position };
        } catch (Exception e) {
            LOGGER.error("Failed to load player data from file for: " + playerId, e);
        }
        return null;
    }

    /**
     * Deletes player data from persistent storage after successful login.
     */
    private void deletePlayerDataPersistent(UUID playerId) {
        if (enableDatabase) {
            deletePlayerDataFromDB(playerId);
        } else {
            deletePlayerDataFromFile(playerId);
        }
    }

    /**
     * Deletes player data from database.
     */
    private void deletePlayerDataFromDB(UUID playerId) {
        String sql = "DELETE FROM player_data WHERE uuid = ?";

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, playerId.toString());
            pstmt.executeUpdate();
            LOGGER.debug("Deleted player data from database for: " + playerId);
        } catch (SQLException e) {
            LOGGER.error("Failed to delete player data from database for: " + playerId, e);
        }
    }

    /**
     * Deletes player data file.
     */
    private void deletePlayerDataFromFile(UUID playerId) {
        File playerFile = new File(playerDataDir, playerId.toString() + ".dat");
        if (playerFile.exists()) {
            if (playerFile.delete()) {
                LOGGER.debug("Deleted player data file for: " + playerId);
            } else {
                LOGGER.warn("Failed to delete player data file for: " + playerId);
            }
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
     * Also deletes persistent data after successful restoration.
     *
     * @param player The player whose inventory will be restored.
     */
    private void restoreInventory(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (savedInventories.containsKey(playerId)) {
            ItemStack[] items = savedInventories.get(playerId);
            for (int i = 0; i < items.length; i++) {
                // Add null check to prevent crashes
                if (items[i] != null) {
                    player.getInventory().setItem(i, items[i]);
                } else {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                }
            }
            savedInventories.remove(playerId);
            player.getInventory().setChanged();

            // Delete persistent data after successful login/register
            deletePlayerDataPersistent(playerId);
            LOGGER.info("Player inventory restored and persistent data cleaned up for: " + playerId);
        }
    }

    /**
     * Removes the blindness effect from a player.
     *
     * @param player The player from whom the effect will be removed.
     */
    private void removeBlindness(ServerPlayer player) {
        player.removeEffect(MobEffects.BLINDNESS);
    }

    /**
     * Shows a title and subtitle to a player
     */
    private void showTitle(ServerPlayer player, String title, String subtitle) {
        // Set animation timings (fade in, stay, fade out) in ticks
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));

        // Send title
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));

        // Send subtitle
        if (subtitle != null && !subtitle.isEmpty()) {
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
        }
    }

    /**
     * Shows an action bar message to a player
     */
    private void showActionBar(ServerPlayer player, String message) {
        player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(message)));
    }

    /**
     * Creates and shows a boss bar for login timeout
     */
    private void createLoginBossBar(ServerPlayer player, int timeoutSeconds) {
        UUID playerId = player.getUUID();

        // Remove existing boss bar if any
        removeBossBar(player);

        // Create new boss bar
        String bossBarTitle = languageManager.getMessage(playerId, "timeout.bossbar");
        ServerBossEvent bossBar = new ServerBossEvent(
                Component.literal(bossBarTitle),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS);

        bossBar.addPlayer(player);
        bossBar.setProgress(1.0F);
        playerBossBars.put(playerId, bossBar);

        // Start a thread to update the boss bar
        Thread bossThread = new Thread(() -> {
            for (int i = timeoutSeconds; i > 0; i--) {
                try {
                    Thread.sleep(1000);
                    float progress = (float) i / timeoutSeconds;
                    bossBar.setProgress(progress);

                    // Update title with remaining time
                    final int remaining = i;
                    MinecraftServer serverInstance = ServerLifecycleHooks.getCurrentServer();
                    if (serverInstance != null) {
                        serverInstance.execute(() -> {
                            String title = languageManager.getMessage(playerId, "timeout.bossbar") + " - " + remaining
                                    + "s";
                            bossBar.setName(Component.literal(title));
                        });
                    }

                    // Check if player logged in
                    if (loggedIn.getOrDefault(playerId, false)) {
                        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
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
     * Removes the boss bar for a player
     */
    private void removeBossBar(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (playerBossBars.containsKey(playerId)) {
            ServerBossEvent bossBar = playerBossBars.get(playerId);
            bossBar.removePlayer(player);
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
    public void onRegisterCommands(RegisterCommandsEvent event) {
        // /register <password> <confirmPassword>
        event.getDispatcher().register(
                Commands.literal("register")
                        .then(Commands.argument("password", StringArgumentType.string())
                                .then(Commands.argument("confirmPassword", StringArgumentType.string())
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            UUID playerId = player.getUUID();
                                            String password = StringArgumentType.getString(context, "password");
                                            String confirmPassword = StringArgumentType.getString(context,
                                                    "confirmPassword");

                                            if (playerPasswords.containsKey(playerId)) {
                                                String msg = languageManager.getMessage(playerId, "register.already");
                                                player.sendSystemMessage(
                                                        Component.literal(msg).withStyle(ChatFormatting.RED));
                                                showActionBar(player, msg);
                                                return 0;
                                            }
                                            if (!password.equals(confirmPassword)) {
                                                String msg = languageManager.getMessage(playerId,
                                                        "register.password.mismatch");
                                                player.sendSystemMessage(
                                                        Component.literal(msg).withStyle(ChatFormatting.RED));
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
                                            loginTimers.remove(playerId);
                                            player.setNoGravity(false);
                                            restoreInventory(player);
                                            removeBlindness(player);
                                            removeBossBar(player);

                                            // Teleport player to their last location or original position
                                            double[] targetPos = null;

                                            // First try to load last saved location
                                            double[] lastLoc = loadLastLocation(playerId);
                                            if (lastLoc != null) {
                                                targetPos = lastLoc;
                                                LOGGER.info("Teleported player {} to last location: X={}, Y={}, Z={}",
                                                        playerId, lastLoc[0], lastLoc[1], lastLoc[2]);
                                                deleteLastLocation(playerId);
                                            }
                                            // Fallback to original position if available
                                            else if (originalPositions.containsKey(playerId)) {
                                                targetPos = originalPositions.get(playerId);
                                                originalPositions.remove(playerId);
                                            }

                                            if (targetPos != null) {
                                                TeleportHelper.teleport(player, (ServerLevel) ((net.minecraft.world.entity.Entity)player).level(),
                                                        targetPos[0], targetPos[1], targetPos[2], player.getYRot(),
                                                        player.getXRot());
                                            }

                                            // Show success message with title and action bar
                                            String successMsg = languageManager.getMessage(playerId,
                                                    "register.success");
                                            String title = languageManager.getMessage(playerId, "register.title");
                                            String subtitle = languageManager.getMessage(playerId, "register.subtitle");

                                            player.sendSystemMessage(
                                                    Component.literal(successMsg).withStyle(ChatFormatting.GREEN));
                                            showTitle(player, title, subtitle);
                                            showActionBar(player, successMsg);
                                            return 1;
                                        }))));

        // /login <password>
        event.getDispatcher().register(
                Commands.literal("login")
                        .then(Commands.argument("password", StringArgumentType.string())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    UUID playerId = player.getUUID();

                                    if (loggedIn.getOrDefault(playerId, false)) {
                                        String msg = languageManager.getMessage(playerId, "login.already");
                                        player.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED));
                                        showActionBar(player, msg);
                                        return 0;
                                    }
                                    if (!playerPasswords.containsKey(playerId)) {
                                        String msg = languageManager.getMessage(playerId, "login.notRegistered");
                                        player.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED));
                                        showActionBar(player, msg);
                                        return 0;
                                    }
                                    String password = StringArgumentType.getString(context, "password");
                                    String storedHash = playerPasswords.get(playerId);
                                    boolean isPasswordCorrect = false;
                                    boolean needsUpgrade = false;

                                    if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$")
                                            || storedHash.startsWith("$2y$")) {
                                        isPasswordCorrect = org.mindrot.jbcrypt.BCrypt.checkpw(password, storedHash);
                                    } else {
                                        String legacyHash = hashPasswordLegacy(password);
                                        if (storedHash.equals(legacyHash)) {
                                            isPasswordCorrect = true;
                                            needsUpgrade = true;
                                        }
                                    }

                                    if (!isPasswordCorrect) {
                                        // Record failed attempt and alert admins if threshold reached
                                        recordFailedLoginAttempt(player);

                                        int attempts = failedLoginAttempts.getOrDefault(playerId, 0);
                                        String msg = languageManager.getMessage(playerId, "login.incorrect")
                                                + " §7(" + attempts + "/3)";
                                        player.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED));
                                        showActionBar(player, msg);
                                        return 0;
                                    }

                                    // Successfully logged in
                                    if (needsUpgrade) {
                                        String newBcryptHash = hashPassword(password);
                                        playerPasswords.put(playerId, newBcryptHash);
                                        if (enableDatabase) {
                                            savePasswordToDB(playerId, newBcryptHash);
                                        } else {
                                            savePasswordsToFile();
                                        }
                                        LOGGER.info("Upgraded password to BCrypt for player "
                                                + player.getName().getString());
                                    }

                                    // Reset failed login attempts on successful login
                                    resetFailedLoginAttempts(playerId);

                                    loggedIn.put(playerId, true);
                                    loginTimers.remove(playerId);
                                    player.setNoGravity(false);
                                    restoreInventory(player);
                                    removeBlindness(player);
                                    removeBossBar(player);

                                    // Teleport player to their last location or original position
                                    double[] targetPos = null;

                                    // First try to load last saved location
                                    double[] lastLoc = loadLastLocation(playerId);
                                    if (lastLoc != null) {
                                        targetPos = lastLoc;
                                        LOGGER.info("Teleported player {} to last location: X={}, Y={}, Z={}",
                                                playerId, lastLoc[0], lastLoc[1], lastLoc[2]);
                                        deleteLastLocation(playerId);
                                    }
                                    // Fallback to original position if available
                                    else if (originalPositions.containsKey(playerId)) {
                                        targetPos = originalPositions.get(playerId);
                                        originalPositions.remove(playerId);
                                    }

                                    if (targetPos != null) {
                                        TeleportHelper.teleport(player, (ServerLevel) ((net.minecraft.world.entity.Entity)player).level(),
                                                targetPos[0], targetPos[1], targetPos[2], player.getYRot(),
                                                player.getXRot());
                                    }

                                    // Show success message with title and action bar
                                    String successMsg = languageManager.getMessage(playerId, "login.success");
                                    String title = languageManager.getMessage(playerId, "login.title");
                                    String subtitle = languageManager.getMessage(playerId, "login.subtitle");

                                    player.sendSystemMessage(
                                            Component.literal(successMsg).withStyle(ChatFormatting.GREEN));
                                    showTitle(player, title, subtitle);
                                    showActionBar(player, successMsg);
                                    return 1;
                                })));

        // /changepassword <oldPassword> <newPassword>
        event.getDispatcher().register(
                Commands.literal("changepassword")
                        .then(Commands.argument("oldPassword", StringArgumentType.string())
                                .then(Commands.argument("newPassword", StringArgumentType.string())
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            UUID playerId = player.getUUID();
                                            String oldPassword = StringArgumentType.getString(context, "oldPassword");
                                            String newPassword = StringArgumentType.getString(context, "newPassword");

                                            if (!loggedIn.getOrDefault(playerId, false)) {
                                                String msg = languageManager.getMessage(playerId, "password.mustLogin");
                                                player.sendSystemMessage(
                                                        Component.literal(msg).withStyle(ChatFormatting.RED));
                                                showActionBar(player, msg);
                                                return 0;
                                            }
                                            String storedHash = playerPasswords.get(playerId);
                                            boolean isPasswordCorrect = false;

                                            if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$")
                                                    || storedHash.startsWith("$2y$")) {
                                                isPasswordCorrect = org.mindrot.jbcrypt.BCrypt.checkpw(oldPassword,
                                                        storedHash);
                                            } else {
                                                String legacyHash = hashPasswordLegacy(oldPassword);
                                                if (storedHash.equals(legacyHash)) {
                                                    isPasswordCorrect = true;
                                                }
                                            }

                                            if (!isPasswordCorrect) {
                                                String msg = languageManager.getMessage(playerId,
                                                        "password.oldIncorrect");
                                                player.sendSystemMessage(
                                                        Component.literal(msg).withStyle(ChatFormatting.RED));
                                                showActionBar(player, msg);
                                                return 0;
                                            }
                                            String hashedNew = hashPassword(newPassword);
                                            playerPasswords.put(playerId, hashedNew);

                                            // Update password in database
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
                                                    player.sendSystemMessage(Component.literal(
                                                            "Failed to update password in database. Please contact an administrator.")
                                                            .withStyle(ChatFormatting.RED));
                                                    return 0;
                                                }
                                            } else {
                                                savePasswordsToFile();
                                            }

                                            String msg = languageManager.getMessage(playerId, "password.changed");
                                            player.sendSystemMessage(
                                                    Component.literal(msg).withStyle(ChatFormatting.GREEN));
                                            showActionBar(player, msg);
                                            return 1;
                                        }))));

        // Admin command: /loginadmin
        event.getDispatcher().register(
                Commands.literal("loginadmin")
                        .executes(context -> {
                            // When typing /loginadmin only - open GUI
                            ServerPlayer admin = context.getSource().getPlayerOrException();
                            MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks
                                    .getCurrentServer();

                            // Use PermissionHelper with Throwable catching to avoid NoSuchMethodError on
                            // hybrid/obfuscated servers
                            boolean isOp = server != null && PermissionHelper.hasPermissions(admin, server, 2);

                            if (!isOp) {
                                admin.sendSystemMessage(
                                        Component.literal("§cYou don't have permission to use this command."));
                                return 0;
                            }
                            openAdminGUI(admin);
                            return 1;
                        }));
    }

    // ================================
    // ADMIN GUI
    // ================================
    /**
     * Open Admin GUI showing all registered players
     */
    public void openAdminGUI(ServerPlayer admin) {
        // Create a container of size 27 (small chest, 3 rows)
        SimpleContainer container = new SimpleContainer(27);

        // Add a book at slot 13 (center) to view players
        ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
        book.set(DataComponents.CUSTOM_NAME, Component.literal("§6§lInfo").withStyle(style -> style.withBold(true)));
        book.set(DataComponents.LORE, new ItemLore(java.util.List.of(
                Component.literal("§7Click to view all players"),
                Component.literal("§7and their passwords"))));
        CompoundTag bookTag = new CompoundTag();
        NBTHelper.putString(bookTag, "GUIAction", "ViewPlayers");
        book.set(DataComponents.CUSTOM_DATA, CustomData.of(bookTag));
        container.setItem(13, book);

        // Add a barrier at slot 11 (left-center) for deletion
        ItemStack barrier = new ItemStack(Items.BARRIER);
        barrier.set(DataComponents.CUSTOM_NAME,
                Component.literal("§c§lDelete Player").withStyle(style -> style.withBold(true)));
        barrier.set(DataComponents.LORE, new ItemLore(java.util.List.of(
                Component.literal("§7Click to view players"),
                Component.literal("§7and delete accounts"))));
        CompoundTag barrierTag = new CompoundTag();
        NBTHelper.putString(barrierTag, "GUIAction", "DeletePlayers");
        barrier.set(DataComponents.CUSTOM_DATA, CustomData.of(barrierTag));
        container.setItem(11, barrier);

        // Open the GUI for the admin
        admin.openMenu(new SimpleMenuProvider(
                (id, playerInventory, player) -> new AdminGUIMenu(id, playerInventory, container, this),
                Component.literal("§6§lAdmin Panel - Login System")));
    }

    /**
     * Open player list for viewing only
     */
    public void openPlayersListGUI(ServerPlayer admin) {
        SimpleContainer container = new SimpleContainer(54);

        int slot = 0;
        for (UUID uuid : playerPasswords.keySet()) {
            if (slot >= 54)
                break;

            String playerName = getPlayerName(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer(),
                    uuid);
            String password = "[HIDDEN]";

            ItemStack playerHead = new ItemStack(Items.PLAYER_HEAD);
            playerHead.set(DataComponents.CUSTOM_NAME,
                    Component.literal("§e" + playerName).withStyle(style -> style.withBold(true)));
            playerHead.set(DataComponents.LORE, new ItemLore(java.util.List.of(
                    Component.literal("§7UUID: §f" + uuid.toString()),
                    Component.empty(),
                    Component.literal("§6Password: §a" + password))));

            CompoundTag customDataTag = new CompoundTag();
            NBTHelper.putString(customDataTag, "PlayerUUID", uuid.toString());
            playerHead.set(DataComponents.CUSTOM_DATA, CustomData.of(customDataTag));
            container.setItem(slot, playerHead);
            slot++;
        }

        admin.openMenu(new SimpleMenuProvider(
                (id, playerInventory, player) -> new AdminGUIMenu(id, playerInventory, container, this),
                Component.literal("§6§lPlayers List - View Only")));
    }

    /**
     * Open player list for deletion
     */
    public void openDeletePlayersGUI(ServerPlayer admin) {
        SimpleContainer container = new SimpleContainer(54);

        int slot = 0;
        for (UUID uuid : playerPasswords.keySet()) {
            if (slot >= 54)
                break;

            String playerName = getPlayerName(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer(),
                    uuid);
            String password = "[HIDDEN]";

            ItemStack playerHead = new ItemStack(Items.PLAYER_HEAD);
            playerHead.set(DataComponents.CUSTOM_NAME,
                    Component.literal("§c" + playerName).withStyle(style -> style.withBold(true)));
            playerHead.set(DataComponents.LORE, new ItemLore(java.util.List.of(
                    Component.literal("§7UUID: §f" + uuid.toString()),
                    Component.empty(),
                    Component.literal("§6Password: §a" + password),
                    Component.empty(),
                    Component.literal("§c§lClick to DELETE this player"))));

            CompoundTag customDataTag = new CompoundTag();
            NBTHelper.putString(customDataTag, "PlayerUUID", uuid.toString());
            NBTHelper.putString(customDataTag, "GUIAction", "DeleteThisPlayer");
            playerHead.set(DataComponents.CUSTOM_DATA, CustomData.of(customDataTag));
            container.setItem(slot, playerHead);
            slot++;
        }

        admin.openMenu(new SimpleMenuProvider(
                (id, playerInventory, player) -> new AdminGUIMenu(id, playerInventory, container, this),
                Component.literal("§c§lDelete Players - Click to Remove")));
    }

    /**
     * Get player name from UUID
     */

    public void kickPlayer(java.util.UUID uuid, String reason) {
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                server.execute(
                        () -> player.connection.disconnect(net.minecraft.network.chat.Component.literal(reason)));
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
        } catch (Exception e) {
        }

        try {
            java.nio.file.Path namesFile = java.nio.file.Paths.get("config/loginsystem_names.json");
            if (java.nio.file.Files.exists(namesFile)) {
                String json = java.nio.file.Files.readString(namesFile);
                com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : obj.entrySet()) {
                    knownPlayerNames.put(java.util.UUID.fromString(entry.getKey()), entry.getValue().getAsString());
                }
            }
        } catch (Exception e) {
        }
    }

    private void saveBans() {
        try {
            com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
            for (java.util.Map.Entry<java.util.UUID, Long> entry : tempBans.entrySet()) {
                obj.addProperty(entry.getKey().toString(), entry.getValue());
            }
            java.nio.file.Files.writeString(java.nio.file.Paths.get("config/loginsystem_bans.json"), obj.toString());
        } catch (Exception e) {
        }

        try {
            com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
            for (java.util.Map.Entry<java.util.UUID, String> entry : knownPlayerNames.entrySet()) {
                obj.addProperty(entry.getKey().toString(), entry.getValue());
            }
            java.nio.file.Files.writeString(java.nio.file.Paths.get("config/loginsystem_names.json"), obj.toString());
        } catch (Exception e) {
        }
    }

    public void banPlayer(java.util.UUID uuid, String reason, int durationDays) {
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> {
                String playerName = getPlayerName(server, uuid);
                long expires = durationDays > 0 ? System.currentTimeMillis() + (durationDays * 86400000L)
                        : Long.MAX_VALUE;
                tempBans.put(uuid, expires);
                saveBans();

                if (playerName != null && !playerName.equals("Unknown")) {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                            "ban " + playerName + " " + reason);
                }

                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    String msg = "You are banned: " + reason;
                    if (durationDays > 0)
                        msg += " for " + durationDays + " days.";
                    player.connection.disconnect(net.minecraft.network.chat.Component.literal(msg));
                }
            });
        }
    }

    public void unbanPlayer(java.util.UUID uuid) {
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> {
                String playerName = getPlayerName(server, uuid);
                if (playerName != null && !playerName.equals("Unknown")) {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                            "pardon " + playerName);
                } else {
                    try {
                        com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(uuid, null);
                        try { server.getPlayerList().getBans().getClass().getMethod("remove", Object.class).invoke(server.getPlayerList().getBans(), wrapGameProfileForBanList(profile)); } catch (Throwable t2) {}
                    } catch (Throwable t) {
                    }
                }

                tempBans.remove(uuid);
                saveBans();
            });
        }
    }

    public boolean isBanned(java.util.UUID uuid) {
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (tempBans.containsKey(uuid)) {
            if (tempBans.get(uuid) > System.currentTimeMillis()) {
                return true;
            } else {
                tempBans.remove(uuid);
                saveBans();
                if (server != null) {
                    String name = getPlayerName(server, uuid);
                    if (name != null && !name.equals("Unknown")) {
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                                "pardon " + name);
                    }
                }
            }
        }

        if (server != null) {
            try {
                String playerName = getPlayerName(server, uuid);
                com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(uuid,
                        playerName != null && !playerName.equals("Unknown") ? playerName : null);
                try { return (boolean) server.getPlayerList().getBans().getClass().getMethod("isBanned", Object.class).invoke(server.getPlayerList().getBans(), wrapGameProfileForBanList(profile)); } catch (Throwable t2) { return false; }
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
            } catch (java.sql.SQLException e) {
                LOGGER.error("Failed to update password in database for UUID: " + uuid, e);
            }
        } else {
            savePasswordsToFile();
        }
    }

    public com.google.gson.JsonArray getInventoryData(java.util.UUID uuid) {
        com.google.gson.JsonArray invArray = new com.google.gson.JsonArray();
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                net.minecraft.world.entity.player.Inventory inv = player.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    net.minecraft.world.item.ItemStack stack = inv.getItem(i);
                    if (!stack.isEmpty()) {
                        com.google.gson.JsonObject itemObj = new com.google.gson.JsonObject();
                        itemObj.addProperty("slot", i);
                        String itemId = stack.getItem().getDescriptionId();
                        if (itemId.startsWith("block."))
                            itemId = itemId.substring(6);
                        else if (itemId.startsWith("item."))
                            itemId = itemId.substring(5);
                        itemId = itemId.replace(".", ":");
                        itemObj.addProperty("id", itemId);
                        itemObj.addProperty("count", stack.getCount());
                        itemObj.addProperty("name", stack.getHoverName().getString());
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
                    String itemId = stack.getItem().getDescriptionId();
                    if (itemId.startsWith("block."))
                        itemId = itemId.substring(6);
                    else if (itemId.startsWith("item."))
                        itemId = itemId.substring(5);
                    itemId = itemId.replace(".", ":");
                    itemObj.addProperty("id", itemId);
                    itemObj.addProperty("count", stack.getCount());
                    itemObj.addProperty("name", stack.getHoverName().getString());
                    invArray.add(itemObj);
                }
            }
        }
        return invArray;
    }

    public void broadcastMessage(String message) {
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal(message), false);
        }
    }

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
        if (knownPlayerNames.containsKey(uuid))
            return knownPlayerNames.get(uuid);
        return "Unknown";
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

    public java.util.Map<UUID, String> getPlayerPasswords() {
        return playerPasswords;
    }

    public Long getLastLogin(java.util.UUID uuid) {
        return lastLogins.get(uuid);
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
    public boolean onServerChat(net.minecraftforge.event.ServerChatEvent event) {
        if (isMuted(event.getPlayer().getUUID())) {
            event.getPlayer().sendSystemMessage(net.minecraft.network.chat.Component
                    .literal("You are muted and cannot speak.").withStyle(net.minecraft.ChatFormatting.RED));
            return true;
        }
        return false;
    }


        public void onServerTick(Object event) {
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (java.util.UUID uuid : loginTimers.keySet()) {
                int timeLeft = loginTimers.get(uuid) - 1;
                if (timeLeft <= 0) {
                    loginTimers.remove(uuid);
                    ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                    if (player != null && !loggedIn.getOrDefault(uuid, false)) {
                        player.connection.disconnect(net.minecraft.network.chat.Component
                                .literal("Login timeout. You took too long to authenticate."));
                    }
                } else {
                    loginTimers.put(uuid, timeLeft);
                    // Send actionbar title warning when time is running out (e.g., < 15 seconds)
                    if (timeLeft % 20 == 0 && timeLeft <= 300) {
                        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                        if (player != null && !loggedIn.getOrDefault(uuid, false)) {
                            player.displayClientMessage(net.minecraft.network.chat.Component
                                    .literal("Time left to login: " + (timeLeft / 20) + "s").withStyle(
                                            net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD),
                                    true);
                        }
                    }
                }
            }
        }
    }

        public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        ServerPlayer newPlayer = (ServerPlayer) event.getEntity();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        UUID newPlayerUUID = newPlayer.getUUID();

        // Prevent double login: disconnect duplicate connections.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != newPlayer && player.getUUID().equals(newPlayerUUID)) {
                if (!alreadyDisconnected.contains(newPlayerUUID)) {
                    newPlayer.connection.disconnect(Component.literal("A player with that name is already online.")
                            .withStyle(ChatFormatting.RED));
                    alreadyDisconnected.add(newPlayerUUID);
                }
                return;
            }
        }

        // Check for persistent data from previous session (in case of server
        // crash/restart)
        Object[] persistentData = loadPlayerDataPersistent(newPlayerUUID);
        boolean hasPersistentData = persistentData != null;

        if (hasPersistentData) {
            // Use data from persistent storage (server crashed before player logged in)
            ItemStack[] savedInv = (ItemStack[]) persistentData[0];
            double[] savedPos = (double[]) persistentData[1];

            if (savedInv != null) {
                savedInventories.put(newPlayerUUID, savedInv);
            }
            if (savedPos != null) {
                originalPositions.put(newPlayerUUID, savedPos);
            }
            LOGGER.info("Restored persistent data for player: " + newPlayerUUID);
        } else {
            // Store the player's original location.
            double[] currentPos = new double[] { newPlayer.getX(), newPlayer.getY(), newPlayer.getZ() };
            originalPositions.put(newPlayerUUID, currentPos);

            // Save position to persistent storage
            savePlayerDataPersistent(newPlayerUUID, new ItemStack[0], currentPos);
        }

        // Teleport the player to the waiting area (configured in the config file).
        double waitingX = Double.parseDouble(config.getProperty("waitingAreaX", "0"));
        double waitingY = Double.parseDouble(config.getProperty("waitingAreaY", "100"));
        double waitingZ = Double.parseDouble(config.getProperty("waitingAreaZ", "0"));
        TeleportHelper.teleport(newPlayer, (ServerLevel) ((net.minecraft.world.entity.Entity)newPlayer).level(), waitingX, waitingY, waitingZ,
                newPlayer.getYRot(), newPlayer.getXRot());

        // Mark the player as not logged in.
        loggedIn.put(newPlayerUUID, false);



        // Show login prompt with title and action bar
        String promptMsg = languageManager.getMessage(newPlayerUUID, "login.prompt");
        String promptSubtitle = languageManager.getMessage(newPlayerUUID, "login.promptSubtitle");
        newPlayer.sendSystemMessage(Component.literal(promptMsg).withStyle(ChatFormatting.YELLOW));
        newPlayer.sendSystemMessage(Component.literal(promptSubtitle).withStyle(ChatFormatting.GRAY));
        showTitle(newPlayer, promptMsg, promptSubtitle);

        // Create boss bar for timeout
        int timeout = Integer.parseInt(config.getProperty("loginTimeout", "60"));
        createLoginBossBar(newPlayer, timeout);

        // Apply blindness effect if enabled.
        if (Boolean.parseBoolean(config.getProperty("applyBlindness", "true"))) {
            int duration = Integer.parseInt(config.getProperty("blindnessDuration", "40"));
            newPlayer.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, 0, false, false));
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
                            newPlayer.connection.disconnect(Component.literal(kickMsg).withStyle(ChatFormatting.RED));
                            alreadyDisconnected.add(newPlayerUUID);
                        }
                    });
                }
            } catch (InterruptedException ignored) {
            }
        });
        timeoutThread.setDaemon(true);
        timeoutThread.start();
    }

    /**
     * Handles player logout event by cleaning up stored data.
     * Saves the player's last location if they were logged in.
     */
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();

        // Remove boss bar if exists
        if (event.getEntity() instanceof ServerPlayer player) {
            removeBossBar(player);
        }

        // If player was logged in, save their last location
        if (loggedIn.getOrDefault(playerId, false)) {
            if (event.getEntity() instanceof ServerPlayer player) {
                // Save the player's current location as their last location
                double[] lastPos = new double[] { player.getX(), player.getY(), player.getZ() };
                saveLastLocation(playerId, lastPos);
                LOGGER.info("Saved last location for player {}: X={}, Y={}, Z={}",
                        playerId, lastPos[0], lastPos[1], lastPos[2]);
            }
            originalPositions.remove(playerId);
        }

        loggedIn.remove(playerId);
        alreadyDisconnected.remove(playerId);
        savedInventories.remove(playerId);
        languageManager.removePlayer(playerId);
    }

    // ================================
    // LAST LOCATION STORAGE
    // ================================
    /**
     * Saves the player's last location to persistent storage.
     * Uses database if enabled, otherwise saves to file.
     */
    private void saveLastLocation(UUID playerId, double[] position) {
        if (enableDatabase) {
            saveLastLocationToDB(playerId, position);
        } else {
            saveLastLocationToFile(playerId, position);
        }
    }

    /**
     * Saves the player's last location to database.
     */
    private void saveLastLocationToDB(UUID playerId, double[] position) {
        String sql = """
                INSERT INTO player_last_location (uuid, last_x, last_y, last_z, saved_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                    last_x = VALUES(last_x),
                    last_y = VALUES(last_y),
                    last_z = VALUES(last_z),
                    saved_at = CURRENT_TIMESTAMP
                """;

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, playerId.toString());
            pstmt.setDouble(2, position[0]);
            pstmt.setDouble(3, position[1]);
            pstmt.setDouble(4, position[2]);

            pstmt.executeUpdate();
            LOGGER.debug("Saved last location to database for: " + playerId);
        } catch (SQLException e) {
            LOGGER.error("Failed to save last location to database for: " + playerId, e);
        }
    }

    /**
     * Saves the player's last location to file.
     */
    private void saveLastLocationToFile(UUID playerId, double[] position) {
        // Ensure directory exists
        if (!playerDataDir.exists()) {
            playerDataDir.mkdirs();
        }

        File locationFile = new File(playerDataDir, playerId.toString() + "_lastloc.dat");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(locationFile))) {
            writer.write("lastX=" + position[0]);
            writer.newLine();
            writer.write("lastY=" + position[1]);
            writer.newLine();
            writer.write("lastZ=" + position[2]);
            writer.newLine();
            writer.write("savedAt=" + System.currentTimeMillis());
            writer.newLine();
            LOGGER.debug("Saved last location to file for: " + playerId);
        } catch (IOException e) {
            LOGGER.error("Failed to save last location to file for: " + playerId, e);
        }
    }

    /**
     * Loads the player's last location from persistent storage.
     * Returns double[] {x, y, z} or null if no location exists.
     */
    private double[] loadLastLocation(UUID playerId) {
        if (enableDatabase) {
            return loadLastLocationFromDB(playerId);
        } else {
            return loadLastLocationFromFile(playerId);
        }
    }

    /**
     * Loads the player's last location from database.
     */
    private double[] loadLastLocationFromDB(UUID playerId) {
        String sql = "SELECT last_x, last_y, last_z FROM player_last_location WHERE uuid = ?";

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, playerId.toString());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                double x = rs.getDouble("last_x");
                double y = rs.getDouble("last_y");
                double z = rs.getDouble("last_z");
                LOGGER.info("Loaded last location from database for: " + playerId);
                return new double[] { x, y, z };
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load last location from database for: " + playerId, e);
        }
        return null;
    }

    /**
     * Loads the player's last location from file.
     */
    private double[] loadLastLocationFromFile(UUID playerId) {
        File locationFile = new File(playerDataDir, playerId.toString() + "_lastloc.dat");
        if (!locationFile.exists()) {
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(locationFile))) {
            double x = 0, y = 0, z = 0;

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("lastX=")) {
                    x = Double.parseDouble(line.substring("lastX=".length()));
                } else if (line.startsWith("lastY=")) {
                    y = Double.parseDouble(line.substring("lastY=".length()));
                } else if (line.startsWith("lastZ=")) {
                    z = Double.parseDouble(line.substring("lastZ=".length()));
                }
            }

            LOGGER.info("Loaded last location from file for: " + playerId);
            return new double[] { x, y, z };
        } catch (Exception e) {
            LOGGER.error("Failed to load last location from file for: " + playerId, e);
        }
        return null;
    }

    /**
     * Deletes the player's last location from persistent storage after successful
     * teleport.
     */
    private void deleteLastLocation(UUID playerId) {
        if (enableDatabase) {
            deleteLastLocationFromDB(playerId);
        } else {
            deleteLastLocationFromFile(playerId);
        }
    }

    /**
     * Deletes the player's last location from database.
     */
    private void deleteLastLocationFromDB(UUID playerId) {
        String sql = "DELETE FROM player_last_location WHERE uuid = ?";

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, playerId.toString());
            pstmt.executeUpdate();
            LOGGER.debug("Deleted last location from database for: " + playerId);
        } catch (SQLException e) {
            LOGGER.error("Failed to delete last location from database for: " + playerId, e);
        }
    }

    /**
     * Deletes the player's last location file.
     */
    private void deleteLastLocationFromFile(UUID playerId) {
        File locationFile = new File(playerDataDir, playerId.toString() + "_lastloc.dat");
        if (locationFile.exists()) {
            if (locationFile.delete()) {
                LOGGER.debug("Deleted last location file for: " + playerId);
            } else {
                LOGGER.warn("Failed to delete last location file for: " + playerId);
            }
        }
    }

    /**
     * Handles server starting event to load all passwords.
     */
    public void onServerStarting(ServerStartingEvent event) {
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
    public void onServerStopping(ServerStoppingEvent event) {
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
                    pstmt.setString(1, entry.getKey().toString());
                    pstmt.setString(2, entry.getValue());
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
     * Prevents unlogged players from dropping items.
     * Only runs on server side to avoid ClassCastException with LocalPlayer.
     */
    public boolean onPlayerDropItem(ItemTossEvent event) {
        if (event.getPlayer() == null || !(event.getPlayer() instanceof ServerPlayer)) {
            return false;
        }
        ServerPlayer player = (ServerPlayer) event.getPlayer();
        UUID playerId = player.getUUID();
        if (!loggedIn.getOrDefault(playerId, false)) {
            // Cancel event
            
            // Add null checks to prevent crashes when accessing dropped item
            if (event.getEntity() != null && event.getEntity().getItem() != null) {
                ItemStack droppedItem = event.getEntity().getItem().copy();

                if (!droppedItem.isEmpty()) {
                    net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
                    if (server != null) {
                        server.execute(() -> {
                            boolean added = player.getInventory().add(droppedItem);
                            player.getInventory().setChanged();

                            if (!added) {
                                player.sendSystemMessage(
                                        Component.literal("Your inventory is full, so the item couldn't be returned.")
                                                .withStyle(ChatFormatting.RED));
                            } else {
                                String msg = languageManager.getMessage(playerId, "restrict.drop");
                                player.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.YELLOW));
                                showActionBar(player, msg);
                            }
                        });
                    }
                }
            }
            return true;
        }
        return false;
    }


    /**
     * Prevents unlogged players from taking damage.
     */
    // LivingDamageEvent is not cancellable in NeoForge 1.21
    // Damage prevention for unlogged players is handled by
    // LivingAttackEvent instead

    /**
     * Prevents unlogged players from breaking blocks.
     */
    public boolean onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getPlayer();
            UUID playerId = player.getUUID();
            if (!loggedIn.getOrDefault(playerId, false)) {
                ((ServerLevel) event.getLevel()).setBlock(event.getPos(), event.getState(), 3);
                String msg = languageManager.getMessage(playerId, "restrict.break");
                player.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED));
                showActionBar(player, msg);
                return true;
            }
        }
    return false;
    }


    /**
     * Prevents unlogged players from placing blocks.
     */
    public boolean onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            UUID playerId = player.getUUID();
            if (!loggedIn.getOrDefault(playerId, false)) {
                String msg = languageManager.getMessage(playerId, "restrict.place");
                player.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED));
                showActionBar(player, msg);
                return true;
            }
        }
    return false;
    }


    /**
     * Prevents unlogged players from using items or interacting with
     * blocks/entities.
     */
    public void onPlayerRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        handlePlayerInteract(event);
    }
    public void onPlayerRightClickItem(PlayerInteractEvent.RightClickItem event) {
        handlePlayerInteract(event);
    }
    public void onPlayerLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        handlePlayerInteract(event);
    }
    public void onPlayerEntityInteract(PlayerInteractEvent.EntityInteract event) {
        handlePlayerInteract(event);
    }
    public void onPlayerEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        handlePlayerInteract(event);
    }

    private void handlePlayerInteract(PlayerInteractEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            UUID playerId = player.getUUID();
            if (!loggedIn.getOrDefault(playerId, false)) {
                try { event.getClass().getMethod("setCanceled", boolean.class).invoke(event, true); } catch(Exception e) {}
            }
        }
    }

    /**
     * Prevents unlogged players from attacking entities.
     */
    public boolean onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            UUID playerId = player.getUUID();
            if (!loggedIn.getOrDefault(playerId, false)) {
                String msg = languageManager.getMessage(playerId, "restrict.attack");
                player.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED));
                showActionBar(player, msg);
                return true;
            }
        }
    return false;
    }


    /**
     * Prevents unlogged players from dealing damage to entities.
     */
    public boolean onLivingAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            UUID playerId = player.getUUID();
            if (!loggedIn.getOrDefault(playerId, false)) {
                return true;
            }
        }
    return false;
    }


    /**
     * Prevents unlogged players from picking up items.
     */
    public boolean onItemPickup(EntityItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            UUID playerId = player.getUUID();
            if (!loggedIn.getOrDefault(playerId, false)) {
                // return true;
            }
        }
    return false;
    }


    /**
     * Prevents unlogged players from sending chat messages (except commands).
     */
    public boolean onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        UUID playerId = player.getUUID();
        if (!loggedIn.getOrDefault(playerId, false)) {
            String msg = languageManager.getMessage(playerId, "restrict.chat");
            player.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED));
            showActionBar(player, msg);
            return true;
        }
        return false;
    }


    /**
     * Prevents unlogged players from executing any commands except /register and
     * /login.
     * This is critical for security to prevent unauthorized access to admin
     * commands.
     */
    public boolean onCommand(CommandEvent event) {
        if (event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player) {
            UUID playerId = player.getUUID();
            if (!loggedIn.getOrDefault(playerId, false)) {
                // Get the command name
                String commandInput = event.getParseResults().getReader().getString().toLowerCase();

                // Allow only /register and /login commands
                if (!commandInput.startsWith("register") &&
                        !commandInput.startsWith("login")) {
                    String msg = languageManager.getMessage(playerId, "restrict.command");
                    player.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED));
                    showActionBar(player, msg);
                    return true;
                }
            }
        }
    return false;
    }


    /**
     * Ensures unlogged players remain in the waiting area and reapply blindness
     * effect if needed.
     */
        public void onPlayerTick(Object event) {
        try {
            Object playerObj = null;
            try { playerObj = event.getClass().getMethod("player").invoke(event); } catch (Exception e) {}
            if (playerObj == null) {
                try { playerObj = event.getClass().getField("player").get(event); } catch (Exception e) {}
            }
            if (playerObj == null || !(playerObj instanceof ServerPlayer)) return;
            ServerPlayer player = (ServerPlayer) playerObj;
            if (!((net.minecraft.world.entity.Entity)player).level().isClientSide()) {
            UUID playerId = player.getUUID();
            if (!loggedIn.getOrDefault(playerId, false)) {
                // Maintain player within the waiting area.
                double waitingX = Double.parseDouble(config.getProperty("waitingAreaX", "0"));
                double waitingY = Double.parseDouble(config.getProperty("waitingAreaY", "100"));
                double waitingZ = Double.parseDouble(config.getProperty("waitingAreaZ", "0"));
                double dx = player.getX() - waitingX;
                double dy = player.getY() - waitingY;
                double dz = player.getZ() - waitingZ;
                if (dx * dx + dy * dy + dz * dz > 1) {
                    TeleportHelper.teleport(player, (ServerLevel) ((net.minecraft.world.entity.Entity)player).level(), waitingX, waitingY, waitingZ,
                            player.getYRot(), player.getXRot());
                    String msg = languageManager.getMessage(playerId, "restrict.move");
                    showActionBar(player, msg);
                    return;
                }
                if (Boolean.parseBoolean(config.getProperty("applyBlindness", "true"))
                        && !player.hasEffect(MobEffects.BLINDNESS)) {
                    int duration = Integer.parseInt(config.getProperty("blindnessDuration", "40"));
                    player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, 0, false, false));
                }
            }
            }
        } catch (Exception e) {}
    }

    @SuppressWarnings("unchecked")
    private static Object wrapGameProfileForBanList(com.mojang.authlib.GameProfile profile) {
        try {
            Class<?> nameAndIdClass = Class.forName("net.minecraft.server.players.NameAndId");
            return nameAndIdClass.getConstructor(com.mojang.authlib.GameProfile.class).newInstance(profile);
        } catch (Throwable t) {
            return profile;
        }
    }
}