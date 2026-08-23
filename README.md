<div align="center">

# 🔒 Login-System v3.1
### *The Ultimate Enterprise Authentication & Web Administration Suite for Minecraft*

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.x%20%7C%2026.x-brightgreen?style=for-the-badge&logo=minecraft)](https://github.com/Notch99/Login-System)
[![Java](https://img.shields.io/badge/Java-21%2B-orange?style=for-the-badge&logo=openjdk)](https://www.oracle.com/java/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-blue?style=for-the-badge&logo=fabric)](https://github.com/Notch99/Login-System/tree/fabric)
[![NeoForge](https://img.shields.io/badge/Loader-NeoForge-orange?style=for-the-badge)](https://github.com/Notch99/Login-System/tree/neoforge)
[![Forge](https://img.shields.io/badge/Loader-Forge-gold?style=for-the-badge)](https://github.com/Notch99/Login-System/tree/forge)
[![License](https://img.shields.io/badge/License-MIT-purple?style=for-the-badge)](LICENSE.txt)

<p align="center">
  <b>A modern, high-performance authentication mod built for servers. Features a rich real-time Web Admin Panel with 2D interactive maps, dual SQLite/MySQL support, in-game chest GUI, and multi-language support (English & Arabic RTL).</b>
</p>

[🌐 Web Dashboard Preview](#-web-administration-dashboard) • [📦 Platforms & Branches](#-platform-directory) • [⚡ Commands](#-commands--permissions) • [⚙️ Configuration](#-configuration)

---

</div>

## 📦 Platform Directory

> **Note:** The source code and builds for each mod loader are organized in dedicated branches. Select your mod loader below:

| Platform | Supported Versions | Branch | Status |
| :--- | :--- | :---: | :---: |
| 🧵 **Fabric** | `1.21.x` / `26.x` | [📁 **`fabric` Branch**](https://github.com/Notch99/Login-System/tree/fabric) | [![Build Status](https://img.shields.io/badge/v3.1-Stable-brightgreen?style=flat-square)](https://github.com/Notch99/Login-System/tree/fabric) |
| ⚡ **NeoForge** | `1.21.x` / `26.x` | [📁 **`neoforge` Branch**](https://github.com/Notch99/Login-System/tree/neoforge) | [![Build Status](https://img.shields.io/badge/v3.1-Stable-brightgreen?style=flat-square)](https://github.com/Notch99/Login-System/tree/neoforge) |
| 🔨 **MinecraftForge** | `1.21.x` / `26.1` / `26.2` | [📁 **`forge` Branch**](https://github.com/Notch99/Login-System/tree/forge) | [![Build Status](https://img.shields.io/badge/v3.1-Stable-brightgreen?style=flat-square)](https://github.com/Notch99/Login-System/tree/forge) |

---

## ✨ Key Features

### 🌐 Web Administration Dashboard
* **Real-Time Control Center**: Manage players, inspect full live inventory/armor slots, reset passwords, kick, ban, or mute remotely from any web browser.
* **Unified 2D World Map**: Seamlessly integrates with [Claim-System](https://github.com/Notch99/Claim-System) to display interactive land claims, territory coordinates, and owners on a single unified web port.
* **Smart Console Banner**: On server startup, automatically detects the server's public IP and prints a clean framed ASCII banner with direct dashboard links.
* **Port Conflict Protection**: Intelligent bind protection and sharing mechanism prevent port conflicts with other server services.

```text
========================================================================
          🌐  SERVER ADMIN WEB DASHBOARD IS ONLINE!  🌐
========================================================================
  👉 Open Dashboard : http://103.178.166.225:20037
  🔑 Web Password   : admin
  ⚙️ Server Port     : 20037
  🛡️ Mod Features   : Login Security + Claims & 2D World Map (Unified)
========================================================================
```

### 🔒 Enterprise-Grade Security
* **BCrypt Hashing**: Passwords are securely hashed with industry-standard salt algorithms.
* **Action Freezing**: Unauthenticated players cannot move, jump, mine, drop items, place blocks, or execute unpermitted commands.
* **Blindness & Immobility**: Prevents unauthorized players from exploring or looking at the world before logging in.
* **Session Memory**: Auto-logs in returning players from the same IP if within the configurable session timeout.
* **Brute-Force & Flood Rate Limiting**: Automatically temporarily bans IPs attempting rapid password guessing.

### 💾 Dual Database Storage
* **SQLite (Default)**: Zero-configuration local database (`loginsystem.db`). Plug-and-play with instant setup.
* **MySQL / MariaDB**: Enterprise database support with connection pooling, perfect for BungeeCord / Velocity multi-server networks.

### 🖥️ In-Game Interactive Chest GUI
* Open visual administration menus directly inside Minecraft using `/loginadmin`.
* Search and inspect registered players, view inventory items, check last login timestamps, and manage bans with a single click.

### 🌍 Multi-Language & RTL Support
* Built-in English (`en`) and Arabic (`ar`) localization.
* Full Right-to-Left (RTL) formatting support in chat and console messages.

---

## ⚡ Commands & Permissions

### 👤 Player Commands
| Command | Description | Permission |
| :--- | :--- | :---: |
| `/register <password> <confirm>` | Register your server account | Everyone |
| `/login <password>` | Log in to your account | Everyone |
| `/changepassword <old> <new>` | Change your current password | Logged In |
| `/login help` | Display available player commands | Everyone |

### 👑 Admin Commands (OP Level 4 / Web Admin)
| Command | Description |
| :--- | :--- |
| `/loginadmin` | Opens the visual interactive Chest GUI |
| `/loginadmin reset <player>` | Unregisters a player and clears their stored password |
| `/loginadmin setpassword <player> <new>` | Forces a new password for a player |
| `/loginadmin ban <player> [time] [reason]` | Bans a player temporarily or permanently |
| `/loginadmin unban <player>` | Removes a ban from a player |
| `/loginadmin mute <player>` | Mutes a player in chat |
| `/loginadmin unmute <player>` | Unmutes a player |
| `/loginadmin reload` | Reloads `loginsystem.properties` configuration |

---

## ⚙️ Configuration

The mod automatically generates `config/loginsystem.properties` on first launch:

```properties
# ⚙️ Login-System Configuration File (v3.1)
# ===================================================

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

# 🔒 Security
maxLoginAttempts=5
tempBanDuration=300
```

---

## 🛡️ Ecosystem Integration: Claim-System

Login-System seamlessly combines with **[Claim-System](https://github.com/Notch99/Claim-System)** to deliver an all-in-one server protection suite:

* 🗺️ **Unified Web Dashboard**: Both mods share the same web server port without port conflicts.
* 📍 **2D Interactive World Map**: Real-time visualization of land claims and player territories.
* 🟡 **Live Laser Particles**: Boundary rendering with 5-block corner pillars.

---

## 🔨 Building from Source

To compile any platform version from source:

```bash
# Clone the repository
git clone https://github.com/Notch99/Login-System.git
cd Login-System

# Checkout your desired platform branch
git checkout fabric     # or 'neoforge' or 'forge'

# Build the JAR
./gradlew build
```
Built JARs will be generated in `build/libs/`.

---

<div align="center">

Made with ❤️ by **[Notch99](https://github.com/Notch99)**

*For bug reports and feature requests, open an issue on the [Issues Tab](https://github.com/Notch99/Login-System/issues).*

</div>
