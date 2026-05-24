package com.example.loginsystem;

import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * LanguageManager - Multi-language support for Login System
 * Manages language files and provides translated messages to players
 */
public class LanguageManager {
    private final Logger logger;
    private final String defaultLanguage;
    private final Map<UUID, String> playerLanguages = new HashMap<>();
    private final Map<String, Properties> languageFiles = new HashMap<>();
    
    public static final String[] SUPPORTED_LANGUAGES = {"en", "ar", "fr", "de", "zh"};
    
    public LanguageManager(Logger logger, String defaultLanguage, File langDir) {
        this.logger = logger;
        this.defaultLanguage = defaultLanguage;
        loadLanguageFiles(langDir);
    }
    
    /**
     * Load all language files from resources/languages/ and extract them to config dir
     */
    private void loadLanguageFiles(File langDir) {
        if (!langDir.exists()) {
            langDir.mkdirs();
        }
        for (String lang : SUPPORTED_LANGUAGES) {
            Properties props = new Properties();
            String resourceName = "languages/" + lang + ".properties";
            File langFile = new File(langDir, lang + ".properties");
            
            if (!langFile.exists()) {
                try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourceName)) {
                    if (input != null) {
                        java.nio.file.Files.copy(input, langFile.toPath());
                        logger.info("Generated default language file: " + langFile.getPath());
                    }
                } catch (IOException e) {
                    logger.error("Failed to extract language file: " + resourceName, e);
                }
            }
            
            if (langFile.exists()) {
                try (InputStream input = new FileInputStream(langFile)) {
                    InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
                    props.load(reader);
                    languageFiles.put(lang, props);
                    logger.info("✅ Loaded language file: " + lang + ".properties");
                } catch (IOException e) {
                    logger.error("❌ Failed to load language file: " + langFile.getPath(), e);
                }
            } else {
                logger.warn("⚠️ Language file not found: " + langFile.getPath());
            }
        }
        
        logger.info("📚 Language system loaded with " + languageFiles.size() + " languages");
    }
    
    /**
     * Get translated message for a player
     * 
     * @param playerId Player UUID
     * @param key Message key
     * @param args Optional formatting arguments
     * @return Translated message
     */
    public String getMessage(UUID playerId, String key, Object... args) {
        String language = playerLanguages.getOrDefault(playerId, defaultLanguage);
        
        // Get the appropriate language file
        Properties langFile = languageFiles.get(language);
        if (langFile == null) {
            // Fallback to default language
            langFile = languageFiles.get(defaultLanguage);
        }
        
        // Fallback to English if default is also not available
        if (langFile == null) {
            langFile = languageFiles.get("en");
        }
        
        // Get the message
        String message = langFile != null ? langFile.getProperty(key) : null;
        
        // If message not found, return the key itself
        if (message == null) {
            logger.warn("⚠️ Message key not found: " + key + " for language: " + language);
            return key;
        }
        
        // Format message with arguments if provided
        if (args.length > 0) {
            try {
                return String.format(message, args);
            } catch (IllegalFormatException e) {
                logger.error("❌ Failed to format message: " + key, e);
                return message;
            }
        }
        
        return message;
    }
    
    /**
     * Set player's preferred language
     * 
     * @param playerId Player UUID
     * @param language Language code (en, ar, fr, de, zh)
     */
    public void setPlayerLanguage(UUID playerId, String language) {
        if (isLanguageSupported(language)) {
            playerLanguages.put(playerId, language);
            logger.info("🌍 Player " + playerId + " language set to: " + language);
        } else {
            logger.warn("⚠️ Unsupported language: " + language);
        }
    }
    
    /**
     * Get player's current language
     * 
     * @param playerId Player UUID
     * @return Language code
     */
    public String getPlayerLanguage(UUID playerId) {
        return playerLanguages.getOrDefault(playerId, defaultLanguage);
    }
    
    /**
     * Remove player from language tracking (on logout)
     * 
     * @param playerId Player UUID
     */
    public void removePlayer(UUID playerId) {
        playerLanguages.remove(playerId);
    }
    
    /**
     * Check if language is supported
     * 
     * @param language Language code
     * @return true if supported
     */
    public boolean isLanguageSupported(String language) {
        for (String lang : SUPPORTED_LANGUAGES) {
            if (lang.equals(language)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get default language
     * 
     * @return Default language code
     */
    public String getDefaultLanguage() {
        return defaultLanguage;
    }
}

