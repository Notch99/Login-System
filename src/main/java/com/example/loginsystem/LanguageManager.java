package com.example.loginsystem;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Properties;
import java.util.UUID;

import org.apache.logging.log4j.Logger;

/**
 * LanguageManager - Manages multi-language support for the Login System mod
 * Supports: English, Arabic, French, German, Chinese
 */
public class LanguageManager {
    private final Logger logger;
    private final File langDir;
    private final HashMap<String, Properties> languages = new HashMap<>();
    private final HashMap<UUID, String> playerLanguages = new HashMap<>();
    private String defaultLanguage;
    
    // Available languages
    public static final String[] SUPPORTED_LANGUAGES = {"en", "ar", "fr", "de", "zh"};
    
    public LanguageManager(Logger logger, String defaultLanguage) {
        this.logger = logger;
        this.defaultLanguage = defaultLanguage != null && !defaultLanguage.isEmpty() ? defaultLanguage : "en";
        this.langDir = new File("config/loginsystem/languages");
        
        // Create language directory
        if (!langDir.exists()) {
            langDir.mkdirs();
        }
        
        // Initialize all language files
        initializeLanguageFiles();
        
        // Load all languages
        loadAllLanguages();
    }
    
    /**
     * Creates default language files if they don't exist
     */
    private void initializeLanguageFiles() {
        createEnglishFile();
        createArabicFile();
        createFrenchFile();
        createGermanFile();
        createChineseFile();
    }
    
    private void createEnglishFile() {
        File file = new File(langDir, "en.properties");
        if (!file.exists()) {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8))) {
                writer.write("# English Language File for Login System\n\n");
                writer.write("# Registration Messages\n");
                writer.write("register.success=✅ Registration successful!\n");
                writer.write("register.already=⚠️ You are already registered!\n");
                writer.write("register.password.mismatch=❌ Passwords do not match!\n");
                writer.write("register.title=Registration Complete\n");
                writer.write("register.subtitle=Welcome to the server!\n\n");
                
                writer.write("# Login Messages\n");
                writer.write("login.success=✅ Login successful!\n");
                writer.write("login.incorrect=❌ Incorrect password!\n");
                writer.write("login.notRegistered=⚠️ You are not registered! Use /register first.\n");
                writer.write("login.already=⚠️ You are already logged in!\n");
                writer.write("login.title=Login Successful\n");
                writer.write("login.subtitle=Welcome back!\n");
                writer.write("login.prompt=Please login or register\n");
                writer.write("login.promptSubtitle=/register <password> <confirm> or /login <password>\n\n");
                
                writer.write("# Timeout Messages\n");
                writer.write("timeout.kick=⏰ You were kicked for not logging in!\n");
                writer.write("timeout.warning=⏰ Time remaining: %s seconds\n");
                writer.write("timeout.bossbar=Login Timeout\n\n");
                
                writer.write("# Password Change\n");
                writer.write("password.changed=✅ Password changed successfully!\n");
                writer.write("password.oldIncorrect=❌ Old password is incorrect.\n");
                writer.write("password.mustLogin=⚠️ You must be logged in to change your password.\n\n");
                
                writer.write("# Restriction Messages\n");
                writer.write("restrict.chat=❌ You must be logged in to chat! Use /register or /login\n");
                writer.write("restrict.command=❌ You must be logged in to use commands!\n");
                writer.write("restrict.move=❌ You must be logged in to move!\n");
                writer.write("restrict.break=❌ You must be logged in to break blocks!\n");
                writer.write("restrict.place=❌ You must be logged in to place blocks!\n");
                writer.write("restrict.attack=❌ You must be logged in to attack!\n");
                writer.write("restrict.drop=⚠️ You must be logged in to drop items. Your item has been returned.\n\n");
                
                writer.write("# Language Command\n");
                writer.write("language.changed=✅ Language changed to: %s\n");
                writer.write("language.available=Available languages: en, ar, fr, de, zh\n");
                writer.write("language.usage=Usage: /language <en|ar|fr|de|zh>\n");
            } catch (IOException e) {
                logger.error("Failed to create English language file", e);
            }
        }
    }
    
    private void createArabicFile() {
        File file = new File(langDir, "ar.properties");
        if (!file.exists()) {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8))) {
                writer.write("# ملف اللغة العربية لنظام تسجيل الدخول\n\n");
                writer.write("# رسائل التسجيل\n");
                writer.write("register.success=✅ تم التسجيل بنجاح!\n");
                writer.write("register.already=⚠️ أنت مسجل بالفعل!\n");
                writer.write("register.password.mismatch=❌ كلمات المرور غير متطابقة!\n");
                writer.write("register.title=اكتمل التسجيل\n");
                writer.write("register.subtitle=مرحباً بك في السيرفر!\n\n");
                
                writer.write("# رسائل تسجيل الدخول\n");
                writer.write("login.success=✅ تم تسجيل الدخول بنجاح!\n");
                writer.write("login.incorrect=❌ كلمة المرور غير صحيحة!\n");
                writer.write("login.notRegistered=⚠️ أنت غير مسجل! استخدم /register أولاً.\n");
                writer.write("login.already=⚠️ أنت مسجل دخولك بالفعل!\n");
                writer.write("login.title=تم تسجيل الدخول\n");
                writer.write("login.subtitle=مرحباً بعودتك!\n");
                writer.write("login.prompt=الرجاء تسجيل الدخول أو التسجيل\n");
                writer.write("login.promptSubtitle=/register <كلمة_المرور> <تأكيد> أو /login <كلمة_المرور>\n\n");
                
                writer.write("# رسائل انتهاء الوقت\n");
                writer.write("timeout.kick=⏰ تم طردك لعدم تسجيل الدخول!\n");
                writer.write("timeout.warning=⏰ الوقت المتبقي: %s ثانية\n");
                writer.write("timeout.bossbar=وقت تسجيل الدخول\n\n");
                
                writer.write("# تغيير كلمة المرور\n");
                writer.write("password.changed=✅ تم تغيير كلمة المرور بنجاح!\n");
                writer.write("password.oldIncorrect=❌ كلمة المرور القديمة غير صحيحة.\n");
                writer.write("password.mustLogin=⚠️ يجب تسجيل الدخول لتغيير كلمة المرور.\n\n");
                
                writer.write("# رسائل القيود\n");
                writer.write("restrict.chat=❌ يجب تسجيل الدخول للدردشة! استخدم /register أو /login\n");
                writer.write("restrict.command=❌ يجب تسجيل الدخول لاستخدام الأوامر!\n");
                writer.write("restrict.move=❌ يجب تسجيل الدخول للتحرك!\n");
                writer.write("restrict.break=❌ يجب تسجيل الدخول لكسر الكتل!\n");
                writer.write("restrict.place=❌ يجب تسجيل الدخول لوضع الكتل!\n");
                writer.write("restrict.attack=❌ يجب تسجيل الدخول للهجوم!\n");
                writer.write("restrict.drop=⚠️ يجب تسجيل الدخول لإسقاط العناصر. تم إرجاع العنصر.\n\n");
                
                writer.write("# أمر اللغة\n");
                writer.write("language.changed=✅ تم تغيير اللغة إلى: %s\n");
                writer.write("language.available=اللغات المتاحة: en, ar, fr, de, zh\n");
                writer.write("language.usage=الاستخدام: /language <en|ar|fr|de|zh>\n");
            } catch (IOException e) {
                logger.error("Failed to create Arabic language file", e);
            }
        }
    }
    
    private void createFrenchFile() {
        File file = new File(langDir, "fr.properties");
        if (!file.exists()) {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8))) {
                writer.write("# Fichier de langue française pour le système de connexion\n\n");
                writer.write("# Messages d'inscription\n");
                writer.write("register.success=✅ Inscription réussie!\n");
                writer.write("register.already=⚠️ Vous êtes déjà inscrit!\n");
                writer.write("register.password.mismatch=❌ Les mots de passe ne correspondent pas!\n");
                writer.write("register.title=Inscription terminée\n");
                writer.write("register.subtitle=Bienvenue sur le serveur!\n\n");
                
                writer.write("# Messages de connexion\n");
                writer.write("login.success=✅ Connexion réussie!\n");
                writer.write("login.incorrect=❌ Mot de passe incorrect!\n");
                writer.write("login.notRegistered=⚠️ Vous n'êtes pas inscrit! Utilisez /register d'abord.\n");
                writer.write("login.already=⚠️ Vous êtes déjà connecté!\n");
                writer.write("login.title=Connexion réussie\n");
                writer.write("login.subtitle=Bon retour!\n");
                writer.write("login.prompt=Veuillez vous connecter ou vous inscrire\n");
                writer.write("login.promptSubtitle=/register <mot_de_passe> <confirmer> ou /login <mot_de_passe>\n\n");
                
                writer.write("# Messages de délai d'attente\n");
                writer.write("timeout.kick=⏰ Vous avez été expulsé pour ne pas vous être connecté!\n");
                writer.write("timeout.warning=⏰ Temps restant: %s secondes\n");
                writer.write("timeout.bossbar=Délai de connexion\n\n");
                
                writer.write("# Changement de mot de passe\n");
                writer.write("password.changed=✅ Mot de passe changé avec succès!\n");
                writer.write("password.oldIncorrect=❌ L'ancien mot de passe est incorrect.\n");
                writer.write("password.mustLogin=⚠️ Vous devez être connecté pour changer votre mot de passe.\n\n");
                
                writer.write("# Messages de restriction\n");
                writer.write("restrict.chat=❌ Vous devez être connecté pour discuter! Utilisez /register ou /login\n");
                writer.write("restrict.command=❌ Vous devez être connecté pour utiliser les commandes!\n");
                writer.write("restrict.move=❌ Vous devez être connecté pour vous déplacer!\n");
                writer.write("restrict.break=❌ Vous devez être connecté pour casser des blocs!\n");
                writer.write("restrict.place=❌ Vous devez être connecté pour placer des blocs!\n");
                writer.write("restrict.attack=❌ Vous devez être connecté pour attaquer!\n");
                writer.write("restrict.drop=⚠️ Vous devez être connecté pour jeter des objets. Votre objet a été retourné.\n\n");
                
                writer.write("# Commande de langue\n");
                writer.write("language.changed=✅ Langue changée en: %s\n");
                writer.write("language.available=Langues disponibles: en, ar, fr, de, zh\n");
                writer.write("language.usage=Usage: /language <en|ar|fr|de|zh>\n");
            } catch (IOException e) {
                logger.error("Failed to create French language file", e);
            }
        }
    }
    
    private void createGermanFile() {
        File file = new File(langDir, "de.properties");
        if (!file.exists()) {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8))) {
                writer.write("# Deutsche Sprachdatei für das Anmeldesystem\n\n");
                writer.write("# Registrierungsnachrichten\n");
                writer.write("register.success=✅ Registrierung erfolgreich!\n");
                writer.write("register.already=⚠️ Sie sind bereits registriert!\n");
                writer.write("register.password.mismatch=❌ Passwörter stimmen nicht überein!\n");
                writer.write("register.title=Registrierung abgeschlossen\n");
                writer.write("register.subtitle=Willkommen auf dem Server!\n\n");
                
                writer.write("# Anmeldenachrichten\n");
                writer.write("login.success=✅ Anmeldung erfolgreich!\n");
                writer.write("login.incorrect=❌ Falsches Passwort!\n");
                writer.write("login.notRegistered=⚠️ Sie sind nicht registriert! Verwenden Sie zuerst /register.\n");
                writer.write("login.already=⚠️ Sie sind bereits angemeldet!\n");
                writer.write("login.title=Anmeldung erfolgreich\n");
                writer.write("login.subtitle=Willkommen zurück!\n");
                writer.write("login.prompt=Bitte anmelden oder registrieren\n");
                writer.write("login.promptSubtitle=/register <passwort> <bestätigen> oder /login <passwort>\n\n");
                
                writer.write("# Timeout-Nachrichten\n");
                writer.write("timeout.kick=⏰ Sie wurden gekickt, weil Sie sich nicht angemeldet haben!\n");
                writer.write("timeout.warning=⏰ Verbleibende Zeit: %s Sekunden\n");
                writer.write("timeout.bossbar=Anmelde-Timeout\n\n");
                
                writer.write("# Passwort ändern\n");
                writer.write("password.changed=✅ Passwort erfolgreich geändert!\n");
                writer.write("password.oldIncorrect=❌ Das alte Passwort ist falsch.\n");
                writer.write("password.mustLogin=⚠️ Sie müssen angemeldet sein, um Ihr Passwort zu ändern.\n\n");
                
                writer.write("# Einschränkungsnachrichten\n");
                writer.write("restrict.chat=❌ Sie müssen angemeldet sein, um zu chatten! Verwenden Sie /register oder /login\n");
                writer.write("restrict.command=❌ Sie müssen angemeldet sein, um Befehle zu verwenden!\n");
                writer.write("restrict.move=❌ Sie müssen angemeldet sein, um sich zu bewegen!\n");
                writer.write("restrict.break=❌ Sie müssen angemeldet sein, um Blöcke abzubauen!\n");
                writer.write("restrict.place=❌ Sie müssen angemeldet sein, um Blöcke zu platzieren!\n");
                writer.write("restrict.attack=❌ Sie müssen angemeldet sein, um anzugreifen!\n");
                writer.write("restrict.drop=⚠️ Sie müssen angemeldet sein, um Gegenstände fallen zu lassen. Ihr Gegenstand wurde zurückgegeben.\n\n");
                
                writer.write("# Sprachbefehl\n");
                writer.write("language.changed=✅ Sprache geändert zu: %s\n");
                writer.write("language.available=Verfügbare Sprachen: en, ar, fr, de, zh\n");
                writer.write("language.usage=Verwendung: /language <en|ar|fr|de|zh>\n");
            } catch (IOException e) {
                logger.error("Failed to create German language file", e);
            }
        }
    }
    
    private void createChineseFile() {
        File file = new File(langDir, "zh.properties");
        if (!file.exists()) {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8))) {
                writer.write("# 登录系统中文语言文件\n\n");
                writer.write("# 注册消息\n");
                writer.write("register.success=✅ 注册成功！\n");
                writer.write("register.already=⚠️ 您已经注册过了！\n");
                writer.write("register.password.mismatch=❌ 密码不匹配！\n");
                writer.write("register.title=注册完成\n");
                writer.write("register.subtitle=欢迎来到服务器！\n\n");
                
                writer.write("# 登录消息\n");
                writer.write("login.success=✅ 登录成功！\n");
                writer.write("login.incorrect=❌ 密码错误！\n");
                writer.write("login.notRegistered=⚠️ 您尚未注册！请先使用 /register。\n");
                writer.write("login.already=⚠️ 您已经登录了！\n");
                writer.write("login.title=登录成功\n");
                writer.write("login.subtitle=欢迎回来！\n");
                writer.write("login.prompt=请登录或注册\n");
                writer.write("login.promptSubtitle=/register <密码> <确认> 或 /login <密码>\n\n");
                
                writer.write("# 超时消息\n");
                writer.write("timeout.kick=⏰ 您因未登录而被踢出！\n");
                writer.write("timeout.warning=⏰ 剩余时间：%s 秒\n");
                writer.write("timeout.bossbar=登录超时\n\n");
                
                writer.write("# 修改密码\n");
                writer.write("password.changed=✅ 密码修改成功！\n");
                writer.write("password.oldIncorrect=❌ 旧密码不正确。\n");
                writer.write("password.mustLogin=⚠️ 您必须登录才能修改密码。\n\n");
                
                writer.write("# 限制消息\n");
                writer.write("restrict.chat=❌ 您必须登录才能聊天！请使用 /register 或 /login\n");
                writer.write("restrict.command=❌ 您必须登录才能使用命令！\n");
                writer.write("restrict.move=❌ 您必须登录才能移动！\n");
                writer.write("restrict.break=❌ 您必须登录才能破坏方块！\n");
                writer.write("restrict.place=❌ 您必须登录才能放置方块！\n");
                writer.write("restrict.attack=❌ 您必须登录才能攻击！\n");
                writer.write("restrict.drop=⚠️ 您必须登录才能丢弃物品。您的物品已归还。\n\n");
                
                writer.write("# 语言命令\n");
                writer.write("language.changed=✅ 语言已更改为：%s\n");
                writer.write("language.available=可用语言：en, ar, fr, de, zh\n");
                writer.write("language.usage=用法：/language <en|ar|fr|de|zh>\n");
            } catch (IOException e) {
                logger.error("Failed to create Chinese language file", e);
            }
        }
    }
    
    /**
     * Loads all language files
     */
    private void loadAllLanguages() {
        for (String lang : SUPPORTED_LANGUAGES) {
            Properties props = new Properties();
            boolean loaded = false;
            
            // First, try to load from config directory (user customized)
            File langFile = new File(langDir, lang + ".properties");
            if (langFile.exists()) {
                try (InputStreamReader reader = new InputStreamReader(
                        new FileInputStream(langFile), StandardCharsets.UTF_8)) {
                    props.load(reader);
                    loaded = true;
                    logger.info("Loaded language from config: " + lang);
                } catch (IOException e) {
                    logger.warn("Failed to load language file from config: " + lang, e);
                }
            }
            
            // If not loaded, try to load from resources (bundled with mod)
            if (!loaded) {
                try (InputStream stream = getClass().getResourceAsStream("/languages/" + lang + ".properties")) {
                    if (stream != null) {
                        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                            props.load(reader);
                            loaded = true;
                            logger.info("Loaded language from resources: " + lang);
                        }
                    }
                } catch (IOException e) {
                    logger.warn("Failed to load language from resources: " + lang, e);
                }
            }
            
            if (loaded && !props.isEmpty()) {
                languages.put(lang, props);
            } else {
                logger.error("Failed to load language: " + lang + " - No valid file found!");
            }
        }
    }
    
    /**
     * Gets a translated message for a player
     */
    public String getMessage(UUID playerId, String key, Object... args) {
        String lang = playerLanguages.getOrDefault(playerId, defaultLanguage);
        Properties props = languages.get(lang);
        
        if (props == null) {
            props = languages.get(defaultLanguage);
        }
        
        String message = props.getProperty(key, key);
        
        // Format with arguments if provided
        if (args.length > 0) {
            message = String.format(message, args);
        }
        
        return message;
    }
    
    /**
     * Sets a player's preferred language
     */
    public void setPlayerLanguage(UUID playerId, String language) {
        if (languages.containsKey(language)) {
            playerLanguages.put(playerId, language);
        }
    }
    
    /**
     * Gets a player's current language
     */
    public String getPlayerLanguage(UUID playerId) {
        return playerLanguages.getOrDefault(playerId, defaultLanguage);
    }
    
    /**
     * Checks if a language is supported
     */
    public boolean isLanguageSupported(String language) {
        return languages.containsKey(language);
    }
    
    /**
     * Removes a player's language preference (on logout)
     */
    public void removePlayer(UUID playerId) {
        playerLanguages.remove(playerId);
    }
}

