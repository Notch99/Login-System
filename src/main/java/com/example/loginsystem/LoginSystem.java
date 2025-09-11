package com.example.loginsystem;

import net.minecraftforge.fml.common.Mod;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
 * Login System Mod v1.1
 *
 * This mod enforces that players register or log in before they can interact
 * with the game. It supports multiple storage methods (database via JDBC or a local file),
 * and includes a waiting area that teleports unlogged players away from their original location.
 *
 * Key features:
 * - Database support (MySQL, SQLite, PostgreSQL) via JDBC.
 * - Waiting area: Players are teleported to a configurable waiting area until they log in.
 * - Inventory hiding: Unlogged players' inventories are hidden.
 * - Blindness effect: Unlogged players have a blindness effect applied.
 * - Double login prevention.
 * - Admin commands to view or delete stored passwords.
 */
@Mod("loginsystem")
public class LoginSystem {
    private static final Logger LOGGER = LogManager.getLogger();
    // ================================
    // DATA STRUCTURES & GLOBAL VARIABLES
    // ================================
    // Stores hashed passwords for each player (key: player's UUID, value: hashed password).
    private final HashMap<UUID, String> playerPasswords = new HashMap<>();
    // Tracks whether a player has successfully logged in.
    private final HashMap<UUID, Boolean> loggedIn = new HashMap<>();
    // Stores the player's original location (to be restored after login).
    private final HashMap<UUID, double[]> originalPositions = new HashMap<>();
    // Prevents disconnecting the same player multiple times.
    private final HashSet<UUID> alreadyDisconnected = new HashSet<>();
    // Stores players' inventories to be restored after login.
    private final HashMap<UUID, ItemStack[]> savedInventories = new HashMap<>();

    // ================================
    // CONFIGURATION VARIABLES
    // ================================
    // Configuration properties loaded from file.
    private final Properties config = new Properties();
    // The configuration file is located at "config/loginsystem.properties".
    private final File configFile = new File("config/loginsystem.properties");
    // If database storage is not enabled, passwords are saved to this file.
    private final File passwordFile = new File("config/passwords.txt");

    // Database settings (retrieved from the config file).
    private boolean enableDatabase;
    private String jdbcUrl;
    private String dbHost;
    private String dbPort;
    private String dbName;
    private String dbUsername;
    private String dbPassword;
    
    // تخزين كلمات المرور الأصلية للأدمن
    private final HashMap<UUID, String> plainTextPasswords = new HashMap<>();

    // ================================
    // CONSTRUCTOR: MOD INITIALIZATION
    // ================================
    public LoginSystem() {
        // Register this mod to listen for events.
        MinecraftForge.EVENT_BUS.register(this);
        // Load (or create) the configuration file.
        loadConfig();
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
                        throw new RuntimeException("No compatible JDBC driver found. Please check your dependencies.", ex);
                    }
                }
                
                if (driverLoaded) {
                    // Test the connection
                    try (Connection testConn = DriverManager.getConnection(jdbcUrl)) {
                        if (testConn.isValid(5)) { // 5 second timeout
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
     *  - General Settings (e.g., loginTimeout)
     *  - Messages for various events (registration, login, errors, etc.)
     *  - Visual Effects & Inventory Control settings (blindness effect, hideInventory)
     *  - Database settings (enableDatabase, jdbcurl)
     *  - Waiting Area settings (coordinates for waiting area)
     */
    private void loadConfig() {
        // Ensure the config directory exists.
        File configDir = new File("config");
        if (!configDir.exists()) {
            configDir.mkdir();
        }
        // If the config file does not exist, create it with default settings and comments.
        if (!configFile.exists()) {
            String configContent = "# 🚀 Login System Mod Configuration File 🚀\n"
                    + "# This file contains configuration settings for the Login System mod.\n"
                    + "# Adjust the values below according to your server's needs.\n\n"
                    + "# ----------------------------\n"
                    + "# General Settings\n"
                    + "# ----------------------------\n"
                    + "# Maximum time (in seconds) a player can remain in the waiting area before being disconnected.\n"
                    + "loginTimeout=60\n\n"
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
                    + "# Waiting Area Settings\n"
                    + "# ----------------------------\n"
                    + "# Coordinates for the waiting area where unlogged players will be teleported.\n"
                    + "waitingAreaX=0\n"
                    + "waitingAreaY=100\n"
                    + "waitingAreaZ=0\n";
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
            
            // Construct JDBC URL with proper encoding of username and password
            String encodedUsername = java.net.URLEncoder.encode(dbUsername, StandardCharsets.UTF_8.toString());
            String encodedPassword = java.net.URLEncoder.encode(dbPassword, StandardCharsets.UTF_8.toString());
            
            jdbcUrl = String.format("jdbc:mysql://%s:%s/%s?user=%s&password=%s&allowPublicKeyRetrieval=%s&useSSL=%s&autoReconnect=%s&maxReconnects=%s",
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
     * Initializes the database by creating the "player_passwords" table if it does not exist.
     */
    private void initDatabase() {
        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            try (Statement stmt = conn.createStatement()) {
                // Create the table if it doesn't exist
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS player_passwords (
                        uuid VARCHAR(36) PRIMARY KEY,
                        password TEXT NOT NULL,
                        last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");
                
                LOGGER.info("Table created or already exists: player_passwords");
                
                // Check if we need to add the last_login column (for backward compatibility)
                try {
                    stmt.execute("ALTER TABLE player_passwords ADD COLUMN IF NOT EXISTS last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
                } catch (SQLException e) {
                    // Column might already exist, ignore
                    LOGGER.debug("Column last_login already exists or error adding it: " + e.getMessage());
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
    private void savePasswordToDB(UUID uuid, String password) {
        if (!enableDatabase) return;
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
     * Saves the current playerPasswords map to the local file.
     */
    private void savePasswordsToFile() {
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
    private void restoreInventory(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (savedInventories.containsKey(playerId)) {
            ItemStack[] items = savedInventories.get(playerId);
            for (int i = 0; i < items.length; i++) {
                player.getInventory().setItem(i, items[i]);
            }
            savedInventories.remove(playerId);
            player.getInventory().setChanged();
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

    // ================================
    // COMMAND REGISTRATION
    // ================================
    /**
     * Registers mod commands such as /register, /login, /changepassword, and admin commands.
     */
    @SubscribeEvent
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
                            String confirmPassword = StringArgumentType.getString(context, "confirmPassword");

                            if (playerPasswords.containsKey(playerId)) {
                                player.sendSystemMessage(Component.literal(config.getProperty("message.alreadyRegistered"))
                                        .withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            if (!password.equals(confirmPassword)) {
                                player.sendSystemMessage(Component.literal("Passwords do not match!")
                                        .withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            String hashedPassword = hashPassword(password);
                            playerPasswords.put(playerId, hashedPassword);
                            
                            // حفظ كلمة المرور الأصلية للأدمن
                            plainTextPasswords.put(playerId, password);
                            
                            if (enableDatabase) {
                                savePasswordToDB(playerId, hashedPassword);
                            } else {
                                savePasswordsToFile();
                            }
                            loggedIn.put(playerId, true);
                            player.setNoGravity(false);
                            restoreInventory(player);
                            removeBlindness(player);
                            // If player's original position was saved, teleport them back.
                            if (originalPositions.containsKey(playerId)) {
                                double[] orig = originalPositions.get(playerId);
                                player.teleportTo((ServerLevel) player.getCommandSenderWorld(), orig[0], orig[1], orig[2], player.getYRot(), player.getXRot());
                                originalPositions.remove(playerId);
                            }
                            player.sendSystemMessage(Component.literal(config.getProperty("message.registerSuccess"))
                                    .withStyle(ChatFormatting.GREEN));
                            return 1;
                        })
                    )
                )
        );

        // /login <password>
        event.getDispatcher().register(
            Commands.literal("login")
                .then(Commands.argument("password", StringArgumentType.string())
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        UUID playerId = player.getUUID();

                        if (loggedIn.getOrDefault(playerId, false)) {
                            player.sendSystemMessage(Component.literal("You are already logged in!")
                                    .withStyle(ChatFormatting.RED));
                            return 0;
                        }
                        if (!playerPasswords.containsKey(playerId)) {
                            player.sendSystemMessage(Component.literal(config.getProperty("message.notRegistered"))
                                    .withStyle(ChatFormatting.RED));
                            return 0;
                        }
                        String password = StringArgumentType.getString(context, "password");
                        String hashedPassword = hashPassword(password);
                        if (!playerPasswords.get(playerId).equals(hashedPassword)) {
                            player.sendSystemMessage(Component.literal("Incorrect password!")
                                    .withStyle(ChatFormatting.RED));
                            return 0;
                        }
                        loggedIn.put(playerId, true);
                        player.setNoGravity(false);
                        restoreInventory(player);
                        removeBlindness(player);
                        if (originalPositions.containsKey(playerId)) {
                            double[] orig = originalPositions.get(playerId);
                            player.teleportTo((ServerLevel) player.getCommandSenderWorld(), orig[0], orig[1], orig[2], player.getYRot(), player.getXRot());
                            originalPositions.remove(playerId);
                        }
                        player.sendSystemMessage(Component.literal(config.getProperty("message.loginSuccess"))
                                .withStyle(ChatFormatting.GREEN));
                        return 1;
                    })
                )
        );

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
                                player.sendSystemMessage(Component.literal("You must be logged in to change your password.")
                                        .withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            String hashedOld = hashPassword(oldPassword);
                            if (!playerPasswords.get(playerId).equals(hashedOld)) {
                                player.sendSystemMessage(Component.literal("Old password is incorrect.")
                                        .withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            String hashedNew = hashPassword(newPassword);
                            playerPasswords.put(playerId, hashedNew);
                            
                            // تحديث كلمة المرور في قاعدة البيانات
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
                                    LOGGER.error("Failed to update password in database for player: " + playerId, e);
                                    player.sendSystemMessage(Component.literal("Failed to update password in database. Please contact an administrator.")
                                            .withStyle(ChatFormatting.RED));
                                    return 0;
                                }
                            } else {
                                savePasswordsToFile();
                            }
                            
                            player.sendSystemMessage(Component.literal("Password changed successfully!")
                                    .withStyle(ChatFormatting.GREEN));
                            return 1;
                        })
                    )
                )
        );

        // Admin commands: /loadmin info <player> and /loadmin delete <player>
        event.getDispatcher().register(
            Commands.literal("loadmin")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("info")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .executes(context -> {
                            String targetPlayerName = StringArgumentType.getString(context, "player");
                            
                            // البحث عن اللاعب بعدة طرق
                            UUID targetUUID = null;
                            
                            // 1. محاولة تحويل النص إلى UUID مباشرة
                            try {
                                targetUUID = UUID.fromString(targetPlayerName);
                                LOGGER.info("Using direct UUID: " + targetUUID);
                            } catch (IllegalArgumentException e) {
                                // ليس UUID، نبحث بالاسم
                                
                                // 2. البحث عن اللاعب المتصل حالياً
                                ServerPlayer onlinePlayer = context.getSource().getServer().getPlayerList().getPlayerByName(targetPlayerName);
                                if (onlinePlayer != null) {
                                    targetUUID = onlinePlayer.getUUID();
                                    LOGGER.info("Found online player: " + targetPlayerName + " -> " + targetUUID);
                                } else {
                                                                        // 3. للاعبين غير المتصلين، استخدم UUID مباشرة
                                    // يمكن للأدمن استخدام /loadmin list لرؤية جميع UUIDs
                                    
                                    // إذا لم نجد اللاعب بأي طريقة
                                    if (targetUUID == null) {
                                        context.getSource().sendFailure(Component.literal(
                                            "❌ Player '" + targetPlayerName + "' not found!\n" +
                                            "💡 Try one of these:\n" +
                                            "  • Make sure the player name is spelled correctly\n" +
                                            "  • Use the player's UUID directly\n" +
                                            "  • Use /loadmin list to see all registered players")
                                                .withStyle(ChatFormatting.RED));
                                        return 0;
                                    }
                                }
                            }
                            
                            final String pass;
                            if (enableDatabase) {
                                try {
                                    try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
                                        
                                        String sql = "SELECT password FROM player_passwords WHERE uuid = ?";
                                        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                                            pstmt.setString(1, targetUUID.toString());
                                            try (ResultSet rs = pstmt.executeQuery()) {
                                                if (rs.next()) {
                                                    pass = rs.getString("password");
                                                } else {
                                                    pass = null;
                                                }
                                            }
                                        }
                                    }
                                } catch (SQLException e) {
                                    LOGGER.error("Failed to get password from database for player: " + targetPlayerName, e);
                                    context.getSource().sendFailure(Component.literal("Failed to get password from database. Please check logs.")
                                            .withStyle(ChatFormatting.RED));
                                    return 0;
                                }
                            } else {
                                pass = playerPasswords.get(targetUUID);
                            }
                            
                            if (pass != null) {
                                final String finalPlayerName = targetPlayerName;
                                // عرض كلمة المرور الأصلية إذا كانت متاحة، وإلا عرض الـ hash
                                final String displayPassword = plainTextPasswords.containsKey(targetUUID) ? 
                                    plainTextPasswords.get(targetUUID) : pass;
                                context.getSource().sendSuccess(() ->
                                    Component.literal("Player " + finalPlayerName + " has password: " + displayPassword)
                                        .withStyle(ChatFormatting.AQUA), false);
                            } else {
                                final String finalPlayerName = targetPlayerName;
                                context.getSource().sendSuccess(() ->
                                    Component.literal("No password found for player " + finalPlayerName)
                                        .withStyle(ChatFormatting.RED), false);
                            }
                            return 1;
                        })
                    )
                )
                .then(Commands.literal("delete")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .executes(context -> {
                            String targetPlayerName = StringArgumentType.getString(context, "player");
                            
                            // البحث عن اللاعب بعدة طرق
                            UUID targetUUID = null;
                            
                            // 1. محاولة تحويل النص إلى UUID مباشرة
                            try {
                                targetUUID = UUID.fromString(targetPlayerName);
                            } catch (IllegalArgumentException e) {
                                // 2. البحث بالاسم (online أو offline)
                                ServerPlayer onlinePlayer = context.getSource().getServer().getPlayerList().getPlayerByName(targetPlayerName);
                                if (onlinePlayer != null) {
                                    targetUUID = onlinePlayer.getUUID();
                                                                 }
                                
                                if (targetUUID == null) {
                                    context.getSource().sendFailure(Component.literal("❌ Player '" + targetPlayerName + "' not found! Use player name or UUID.")
                                            .withStyle(ChatFormatting.RED));
                                    return 0;
                                }
                            }
                            
                                if (enableDatabase) {
                                try {
                                    try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
                                        
                                        String sql = "DELETE FROM player_passwords WHERE uuid = ?";
                                        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                                            pstmt.setString(1, targetUUID.toString());
                                            int rowsAffected = pstmt.executeUpdate();
                                            if (rowsAffected > 0) {
                                                playerPasswords.remove(targetUUID);
                                                context.getSource().sendSuccess(() ->
                                                    Component.literal("Deleted password for player " + targetPlayerName)
                                                        .withStyle(ChatFormatting.GREEN), false);
                                } else {
                                                context.getSource().sendSuccess(() ->
                                                    Component.literal("No password found for player " + targetPlayerName)
                                                        .withStyle(ChatFormatting.RED), false);
                                            }
                                        }
                                    }
                                    LOGGER.info("Password deleted from database for player: " + targetPlayerName);
                                } catch (SQLException e) {
                                    LOGGER.error("Failed to delete password from database for player: " + targetPlayerName, e);
                                    context.getSource().sendFailure(Component.literal("Failed to delete password from database. Please check logs.")
                                            .withStyle(ChatFormatting.RED));
                                    return 0;
                                }
                            } else {
                                if (playerPasswords.containsKey(targetUUID)) {
                                    playerPasswords.remove(targetUUID);
                                    savePasswordsToFile();
                                context.getSource().sendSuccess(() ->
                                    Component.literal("Deleted password for player " + targetPlayerName)
                                        .withStyle(ChatFormatting.GREEN), false);
                            } else {
                                context.getSource().sendSuccess(() ->
                                    Component.literal("No password found for player " + targetPlayerName)
                                        .withStyle(ChatFormatting.RED), false);
                                }
                            }
                            return 1;
                        })
                    )
                )
        );
    }

    // ================================
    // EVENT HANDLERS
    // ================================
    /**
     * Handles player login event.
     * - Prevents double login by disconnecting duplicate connections.
     * - Stores the player's original location and teleports them to the waiting area.
     * - Applies inventory hiding and blindness effect until login.
     */
    @SubscribeEvent
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

        // Store the player's original location.
        originalPositions.put(newPlayerUUID, new double[]{newPlayer.getX(), newPlayer.getY(), newPlayer.getZ()});

        // Teleport the player to the waiting area (configured in the config file).
        double waitingX = Double.parseDouble(config.getProperty("waitingAreaX", "0"));
        double waitingY = Double.parseDouble(config.getProperty("waitingAreaY", "100"));
        double waitingZ = Double.parseDouble(config.getProperty("waitingAreaZ", "0"));
        newPlayer.teleportTo((ServerLevel) newPlayer.getCommandSenderWorld(), waitingX, waitingY, waitingZ, newPlayer.getYRot(), newPlayer.getXRot());

        // Mark the player as not logged in.
        loggedIn.put(newPlayerUUID, false);
        newPlayer.sendSystemMessage(Component.literal("Please register or login using /register <password> <confirmPassword> or /login <password>")
                .withStyle(ChatFormatting.YELLOW));

        // Hide inventory if enabled.
        if (Boolean.parseBoolean(config.getProperty("hideInventory", "true"))) {
            int containerSize = newPlayer.getInventory().getContainerSize();
            ItemStack[] savedItems = new ItemStack[containerSize];
            for (int i = 0; i < containerSize; i++) {
                savedItems[i] = newPlayer.getInventory().getItem(i).copy();
            }
            savedInventories.put(newPlayerUUID, savedItems);
            newPlayer.getInventory().clearContent();
        }

        // Apply blindness effect if enabled.
        if (Boolean.parseBoolean(config.getProperty("applyBlindness", "true"))) {
            int duration = Integer.parseInt(config.getProperty("blindnessDuration", "40"));
            newPlayer.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, 0, false, false));
        }

        // Start a timeout thread to disconnect players who don't log in in time.
        int timeout = Integer.parseInt(config.getProperty("loginTimeout", "60")) * 1000;
        new Thread(() -> {
            try {
                Thread.sleep(timeout);
                if (!loggedIn.getOrDefault(newPlayerUUID, false)) {
                    server.execute(() -> {
                        if (!alreadyDisconnected.contains(newPlayerUUID)) {
                            newPlayer.connection.disconnect(
                                    Component.literal(config.getProperty("message.kickTimeout", "You were kicked for not logging in!"))
                                            .withStyle(ChatFormatting.RED));
                            alreadyDisconnected.add(newPlayerUUID);
                        }
                    });
                }
            } catch (InterruptedException ignored) {}
        }).start();
    }

    /**
     * Handles player logout event by cleaning up stored data.
     */
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        loggedIn.remove(playerId);
        alreadyDisconnected.remove(playerId);
        savedInventories.remove(playerId);
        originalPositions.remove(playerId);
    }

    /**
     * Handles server starting event to load all passwords.
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        System.out.println("LoginSystem: Server is starting, loading passwords...");
        if (enableDatabase) {
            try {
                loadPasswordsFromDB();
                System.out.println("LoginSystem: Successfully loaded " + playerPasswords.size() + " passwords from database.");
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
    }

    /**
     * Handles server stopping event to ensure passwords are saved.
     */
    @SubscribeEvent
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
     */
    @SubscribeEvent
    public void onPlayerDropItem(ItemTossEvent event) {
        ServerPlayer player = (ServerPlayer) event.getPlayer();
        UUID playerId = player.getUUID();
        if (!loggedIn.getOrDefault(playerId, false)) {
            event.setCanceled(true);
            ItemStack droppedItem = event.getEntity().getItem().copy();
            boolean added = player.getInventory().add(droppedItem);
            player.getInventory().setChanged();
            if (!added) {
                player.sendSystemMessage(Component.literal("Your inventory is full, so the item couldn't be returned.")
                        .withStyle(ChatFormatting.RED));
            } else {
                player.sendSystemMessage(Component.literal("You must be logged in to drop items. Your item has been returned.")
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    /**
     * Prevents unlogged players from taking damage.
     */
    @SubscribeEvent
    public void onPlayerHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            UUID playerId = player.getUUID();
            if (!loggedIn.getOrDefault(playerId, false)) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * Prevents unlogged players from breaking blocks.
     */
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getPlayer();
            UUID playerId = player.getUUID();
            if (!loggedIn.getOrDefault(playerId, false)) {
                event.setCanceled(true);
                ((ServerLevel) event.getLevel()).setBlock(event.getPos(), event.getState(), 3);
                player.sendSystemMessage(Component.literal("You must be logged in to break blocks!")
                        .withStyle(ChatFormatting.RED));
            }
        }
    }

    /**
     * Ensures unlogged players remain in the waiting area and reapply blindness effect if needed.
     */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!event.player.getCommandSenderWorld().isClientSide() && event.phase == TickEvent.Phase.END) {
            ServerPlayer player = (ServerPlayer) event.player;
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
                    player.teleportTo((ServerLevel) player.getCommandSenderWorld(), waitingX, waitingY, waitingZ, player.getYRot(), player.getXRot());
                    player.sendSystemMessage(Component.literal("You must be logged in to move!")
                            .withStyle(ChatFormatting.RED));
                }
                if (Boolean.parseBoolean(config.getProperty("applyBlindness", "true")) && !player.hasEffect(MobEffects.BLINDNESS)) {
                    int duration = Integer.parseInt(config.getProperty("blindnessDuration", "40"));
                    player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, 0, false, false));
                }
            }
        }
    }
}
