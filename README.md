<div align="center">

# 🔒 Login-System v3.1 - Fabric Edition
### *Enterprise Authentication & Web Administration Suite for Minecraft Fabric*

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.x%20%7C%2026.x-brightgreen?style=for-the-badge&logo=minecraft)](https://github.com/Notch99/Login-System)
[![Loader](https://img.shields.io/badge/Loader-Fabric-blue?style=for-the-badge)](https://github.com/Notch99/Login-System/tree/fabric)
[![Version](https://img.shields.io/badge/Version-v3.1-blue?style=for-the-badge)](https://github.com/Notch99/Login-System/tree/fabric)
[![License](https://img.shields.io/badge/License-MIT-purple?style=for-the-badge)](LICENSE.txt)

<p align="center">
  <b>High-performance, secure authentication mod for Fabric servers. Features real-time Web Dashboard, unified 2D world maps with Claim-System, SQLite/MySQL dual database, in-game chest GUI, and Arabic RTL support.</b>
</p>

---

</div>

## ✨ Key Features in v3.1

* 🌐 **Admin Web Dashboard**: Remote browser panel with live player management, inventory inspector, remote console, and unified Claim-System 2D world map on a single port!
* 🔒 **Enterprise-Grade Security**: BCrypt password hashing, session auto-resume, IP rate-limiting, freeze timer, and anti-flood protection.
* 💾 **Dual Database Storage**: Built-in SQLite (zero configuration) and MySQL / MariaDB connection pooling.
* 🖥️ **Interactive In-Game Chest GUI**: Open visual player management menus with `/loginadmin`.
* 🌍 **Multi-Language & RTL**: Full English (`en`) and Arabic (`ar`) support with native Right-to-Left formatting.
* 🛡️ **Claim-System Integration**: Zero-conflict unified server dashboard.

---

## ⚡ Commands & Permissions

### 👤 Player Commands
| Command | Description | Permission |
| :--- | :--- | :---: |
| `/register <password> <confirm>` | Register your account | Everyone |
| `/login <password>` | Log in to the server | Everyone |
| `/changepassword <old> <new>` | Change your current password | Logged In |
| `/login help` | Show help commands | Everyone |

### 👑 Admin Commands (OP Level 4)
| Command | Description |
| :--- | :--- |
| `/loginadmin` | Open interactive Chest GUI menu |
| `/loginadmin reset <player>` | Reset and clear player password |
| `/loginadmin setpassword <player> <new>` | Force change player password |
| `/loginadmin ban <player> [time] [reason]` | Ban player temporarily or permanently |
| `/loginadmin unban <player>` | Remove ban from player |
| `/loginadmin mute <player>` | Mute player in chat |
| `/loginadmin unmute <player>` | Unmute player |
| `/loginadmin reload` | Reload configuration file |

---

## ⚙️ Configuration (`config/loginsystem.properties`)

```properties
# 🌐 Web Administration Dashboard
enableWebPanel=true
webPanelPort=20037
webPanelPassword=admin

# ⏱️ Authentication Timers (Seconds)
loginTimeout=60
sessionTimeout=1200

# 💾 Database Storage
enableDatabase=false
dbType=sqlite
dbHost=localhost
dbPort=3306
dbName=minecraft_login
dbUser=root
dbPassword=secret

# 🌍 Language (en / ar)
language=en
```

---

## 🔨 Building

```bash
./gradlew build
```
The compiled JAR will be located at `build/libs/`.
