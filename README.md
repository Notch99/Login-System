# ðŸ” Login System Mod v2.2 - Fabric Edition

A comprehensive authentication system for Minecraft Fabric servers that requires players to register and login before they can interact with the game world.

## âœ¨ Features

### ðŸ”’ Security Features
- **BCrypt Encryption**: All passwords are securely hashed using industry-standard BCrypt
- **ðŸš« Anti-Spoofing Protection**: Intercepts blocks malicious double-logins
- **Database Support**: MySQL and MariaDB with embedded JDBC drivers and timeout watchdogs
- **File Fallback**: Automatic fallback to file storage if database is disabled
- **Complete Player Lockdown**: 13+ protection systems for unlogged players (movement, block breaking, damage, etc.)

### ðŸŒ Admin Web Dashboard (NEW!)
- **Browser-Based Management**: Monitor your server via a live Web Interface
- **Live Inventories**: View live inventories of players through the web browser.
- **Remote Moderation**: Kick, Ban, Mute, and Unban entirely remotely.

### ðŸŽ® Player Experience
- **Waiting Area System**: Players are teleported to a safe area until login
- **ðŸ’¾ Offline Persistence**: Inventories and precise locations are safely backed up on join, preventing falling into the void.
- **Inventory Protection**: Player inventories are hidden until authentication
- **Blindness Effect**: Visual restriction for unlogged players
- **Configurable Timeout**: Automatic kick for inactive players

### âš™ï¸ Administration Commands
- **Player Commands**: 
  - `/register <password> <confirmPassword>`
  - `/login <password>`
  - `/changepassword <oldPassword> <newPassword>`
- **Admin Commands (OP Level 2+)**:
  - `/loadmin` - Opens the Admin Management GUI
  - *Note: Passwords are no longer visible to admins for security, but can be securely resettled.*

## ðŸ“‹ System Requirements
- **Minecraft**: 1.21.x
- **Fabric Loader**: Compatible with latest 1.21.x loaders
- **Fabric API**: Required
- **Java**: 17 or higher
- **Server Side Only**: No client installation required

## ðŸš€ Installation & Setup

1. Install **Fabric Loader** on your server.
2. Download and install the **Fabric API** mod.
3. Place the mod's `.jar` file in your server's `mods` folder.
4. Start the server (configuration files will be auto-generated).
5. Configure settings in `config/loginsystem.properties`.
6. Restart the server.

## ðŸ—„ï¸ Database Configuration (Optional)

The mod supports MySQL and MariaDB databases with automatic fallback to file storage.
To configure the database, open `config/loginsystem.properties`:

```properties
enableDatabase=true
database.host=127.0.0.1
database.port=3306
database.name=loginsystem
database.username=root
database.password=your_password
```

## ðŸ› ï¸ Development Setup

1. Open project in your preferred IDE (IntelliJ/Eclipse/VSCode).
2. Wait for Gradle to import the project.
3. Run: `./gradlew genSources` to generate Minecraft sources.
4. Use `./gradlew build` to build the mod, or `./gradlew runServer` to test it.

## ðŸ“„ License & Support
This mod is licensed under the MIT License. See `LICENSE.txt` for more details.
For support or configuration help, please check the generated config file.

