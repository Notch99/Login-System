# 🔐 Login System Mod v2.1.0

Advanced authentication system for Minecraft servers with database support, secure encryption, admin alerts, and intuitive GUI management.

## ✨ Features

### 🔒 Security Features
- **BCrypt Encryption**: All passwords are securely hashed using industry-standard BCrypt (Cost 12)
- **🚫 Anti-Spoofing Protection**: Intercepts and blocks malicious double-logins completely
- **🚨 Admin Alert System**: Live notifications for suspicious logins and excessive failed attempts. 
- **Database Support**: MySQL and MariaDB with embedded JDBC drivers (with Fast 3-sec Crash Guard)
- **File Fallback**: Automatic fallback to file storage if database fails
- **Admin Password Management**: Reset player passwords via GUI
- **Complete Player Lockdown**: 13+ protection systems for unlogged players

### 🌐 Admin Web Dashboard (NEW!)
- **Browser-Based Management**: Monitor your server via a live Web Interface on `http://localhost:<port>`
- **Live Inventories**: View live inventories of players through the web browser.
- **Remote Moderation**: Kick, Ban, Mute, and Unban entirely remotely.

### 🎮 Player Experience
- **Waiting Area System**: Players are teleported to a safe area until login
- **💾 Offline Persistence**: Inventories and precise locations are backed up on join, preventing void-drop crashes.
- **Inventory Protection**: Player inventories are hidden until authentication
- **Blindness Effect**: Visual restriction for unlogged players
- **Configurable Timeout**: Automatic kick for inactive players
- **Complete Restriction**: Unlogged players cannot interact with anything

### ⚙️ Administration
- **🎨 Admin GUI System**: Modern, intuitive in-game graphical interface (`/loadmin`)
- **Real Player Skins**: Player heads display actual Minecraft skins
- **Secure Password Reset**: Instantly generate random 6-digit passwords for users
- **One-Click Management**: Easy player account deletion
- **Real-time Logging**: Comprehensive logging system and security events

## 📋 System Requirements
- **Minecraft**: 1.21.x
- **NeoForge**: Compatible with 1.21.x NeoForge distributions
- **Java**: 17 or higher
- **Server Side Only**: No client installation required

## 🚀 Installation

1. Download the latest JAR file from releases
2. Place it in your server's `mods` folder
3. Start the server to generate configuration files
4. Configure database settings in `config/loginsystem.properties`
5. Restart the server

## ⚡ Commands

### Player Commands
- `/register <password> <confirmPassword>` - Register a new account
- `/login <password>` - Login to your account
- `/changepassword <oldPassword> <newPassword>` - Change your password

### Admin Commands (Permission Level 2+)
- `/loadmin` - Open the Admin GUI Panel

## 🎨 Admin GUI System

The Admin GUI provides a visual interface for managing player accounts:

### Main Menu
Type `/loadmin` to see:
- **📖 Info** (Book Icon) - View all players and hover to reveal passwords. 
- **🚫 Delete Player** (Barrier Icon) - One-click account removal system.

## 🗄️ Configuration (config/loginsystem.properties)

```properties
# Database Settings
enableDatabase=true
database.host=127.0.0.1
database.port=3306
database.name=loginsystem
database.username=root
database.password=your_password

# Admin Alerts
enableAdminAlerts=true
maxFailedAttempts=3

# Gameplay Settings
loginTimeout=60
applyBlindness=true
hideInventory=true
```

## 🐛 Troubleshooting & Support
- **Issue**: Database Connection Failed? Check your credentials in the config.
- **Support**: Open an issue on GitHub for assistance!

## 📄 License & Credits
MIT License © 2026 LoginSystem Team

- **Forge API**: Thanks to the modding framework!
- **Server-side only**: No client downloads needed!
