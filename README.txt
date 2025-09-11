# 🔐 Login System Mod v1.3.0

Advanced authentication system for Minecraft servers with database support and secure encryption.

## ✨ Features

### 🔒 Security Features
- **SHA-256 Encryption**: All passwords are securely hashed
- **Database Support**: MySQL and MariaDB with embedded JDBC drivers
- **File Fallback**: Automatic fallback to file storage if database fails
- **Admin Password Management**: View and manage player passwords

### 🎮 Player Experience
- **Waiting Area System**: Players are teleported to a safe area until login
- **Inventory Protection**: Player inventories are hidden until authentication
- **Blindness Effect**: Visual restriction for unlogged players
- **Configurable Timeout**: Automatic kick for inactive players

### ⚙️ Administration
- **Admin Commands**: Full password management system
- **UUID Support**: Works with both online and offline players
- **Real-time Logging**: Comprehensive logging system
- **Configurable Settings**: Extensive configuration options

## 📋 System Requirements

- **Minecraft**: 1.20 - 1.20.4
- **Forge**: 47.0.0 - 48.0.0
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
- `/loadmin info <player>` - View player's password information
- `/loadmin delete <player>` - Delete a player's account
- `/loadmin resetpass <player> <newPassword>` - Reset player's password
- `/loadmin list` - List all registered players

## 🗄️ Database Configuration

The mod supports MySQL and MariaDB databases with automatic fallback to file storage.

### Configuration File: `config/loginsystem.properties`

```properties
# Database Settings
enableDatabase=true
database.host=127.0.0.1
database.port=3306
database.name=loginsystem
database.username=root
database.password=your_password

# Security Settings
database.useSSL=false
database.allowPublicKeyRetrieval=true

# Gameplay Settings
loginTimeout=60
applyBlindness=true
hideInventory=true

# Waiting Area Coordinates
waitingAreaX=0
waitingAreaY=100
waitingAreaZ=0
```

## 🔧 Technical Details

- **JAR Size**: ~3MB (includes embedded JDBC drivers)
- **Memory Usage**: Minimal impact on server performance
- **Thread Safe**: Designed for high-traffic servers
- **Async Operations**: Non-blocking database operations

## 📝 Changelog

### v1.3.0
- ✅ Added embedded MySQL and MariaDB JDBC drivers
- ✅ Improved admin command system with UUID support
- ✅ Added password reset functionality
- ✅ Enhanced error handling and logging
- ✅ Optimized JAR size (under 5MB)
- ✅ Better support for offline players

### v1.2.0
- Added database support
- Improved security with SHA-256 encryption
- Added admin commands

### v1.1.0
- Initial release
- Basic file-based authentication

## 🐛 Troubleshooting

### Common Issues

1. **Database Connection Failed**
   - Check your database credentials in config file
   - Ensure database server is running
   - Verify network connectivity

2. **Player Can't Login**
   - Check if player is registered
   - Verify password spelling
   - Use admin commands to reset if needed

3. **Commands Not Working**
   - Ensure you have proper permissions (OP level 2+)
   - Check command syntax
   - Verify player is online for name-based commands

## 📞 Support

- **Issues**: Report bugs on GitHub
- **Discord**: Join our community server
- **Documentation**: Full wiki available online

## 📄 License

MIT License © 2025 LoginSystem Team

This project is open source and available under the MIT License. You are free to use, modify, and distribute this software according to the terms of the MIT License. See the LICENSE.txt file for full details.

## 🙏 Credits

- **Minecraft Forge Team**: For the excellent modding framework
- **Database Drivers**: MySQL and MariaDB JDBC drivers
- **Community**: Thanks to all testers and contributors

---

**Server-side only mod - No client installation required!**
