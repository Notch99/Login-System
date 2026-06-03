Login System Mod - Fabric Edition
==================================

A comprehensive login system for Minecraft Fabric servers that requires players 
to register and login before they can interact with the game world.

Features:
---------
🔐 Secure password system with SHA-256 hashing
🗄️ Database support (MySQL/MariaDB) with embedded JDBC drivers
📁 File-based storage fallback when database is disabled
⏰ Configurable login timeout with automatic kick
🎮 Player commands: /register, /login, /changepassword
👑 Admin commands: /loadmin info, /loadmin delete
🚫 Complete protection for unlogged players (movement, block breaking, damage, etc.)
🌍 Waiting area system that teleports unlogged players
👁️ Blindness effect and inventory hiding for unlogged players

Setup Instructions for Fabric Development:
==========================================

Prerequisites:
--------------
- Java 17 or higher
- Fabric development environment

Step 1: Clone/Download this project
-----------------------------------
Extract or clone this project to your desired location.

Step 2: Import into your IDE
-----------------------------

For IntelliJ IDEA:
1. Open IntelliJ IDEA
2. File > Open > Select the project folder
3. Wait for Gradle to import the project
4. Run: `./gradlew genSources` to generate Minecraft sources
5. Refresh Gradle project if needed

For Eclipse:
1. Install Buildship Gradle plugin if not already installed
2. File > Import > Gradle > Existing Gradle Project
3. Select the project folder
4. Run: `./gradlew genEclipseRuns`
5. Refresh the project

For Visual Studio Code:
1. Install Extension Pack for Java
2. Open the project folder
3. VS Code should automatically detect the Gradle project
4. Run: `./gradlew genSources`

Step 3: Development Commands
----------------------------
- `./gradlew build` - Build the mod
- `./gradlew runServer` - Run test server
- `./gradlew runClient` - Run test client
- `./gradlew clean` - Clean build files
- `./gradlew genSources` - Generate Minecraft sources

Installation for Server Owners:
===============================

Requirements:
- Minecraft Server 1.20 - 1.20.6
- Fabric Loader 0.14.22+
- Fabric API mod

Steps:
1. Install Fabric Loader on your server
2. Download and install Fabric API
3. Place loginsystem-1.0.jar in your server's mods folder
4. Start the server (config files will be auto-generated)
5. Configure settings in config/loginsystem.properties
6. Restart the server

Configuration:
==============
The mod automatically creates a configuration file at:
config/loginsystem.properties

Key settings:
- enableDatabase: Enable MySQL/MariaDB database storage
- waitingAreaX/Y/Z: Coordinates for the waiting area
- loginTimeout: Time before unlogged players are kicked
- applyBlindness: Apply blindness effect to unlogged players
- hideInventory: Hide inventory until login

Database Setup (Optional):
==========================
If you want to use database storage instead of files:

1. Set enableDatabase=true in config/loginsystem.properties
2. Configure your database connection settings:
   - database.host=your_host
   - database.port=3306
   - database.name=loginsystem
   - database.username=your_username
   - database.password=your_password

The mod includes embedded MySQL and MariaDB JDBC drivers.

Commands:
=========
Player Commands:
- /register <password> <confirmPassword> - Register a new account
- /login <password> - Login to your account
- /changepassword <oldPassword> <newPassword> - Change your password

Admin Commands (OP level 2+):
- /loadmin info <player> - View player's password info
- /loadmin delete <player> - Delete a player's account

Support:
========
For issues, suggestions, or support, please check the configuration file
first as it contains detailed explanations for all settings.

License:
========
This mod is licensed under the MIT License.
See LICENSE.txt for more details.

Fabric Documentation: https://fabricmc.net/wiki/
Fabric API: https://github.com/FabricMC/fabric
