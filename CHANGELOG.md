# ðŸ” Login System Mod - Changelog

## v2.0 - Item Drop & Stability Hotfix

### 🛡️ Bug Fixes & Improvements
- **Item Drop Desync Fixed:** Re-engineered the item drop prevention (ItemTossEvent) with a 1-tick delay to prevent the client inventory from becoming visually desynced when an item drop is blocked.
- **Mixins Cleanup:** Removed a lingering, redundant mixin that caused IllegalAccessError crashes when trying to drop items.
- **Reflection Cleanup:** Silenced expected console spam (NoSuchMethodException) in the NBTHelper fallback logic when loading legacy player data.

## v2.1.0 â€” The Ultimate Security & Stability Update ðŸš€
**Release Date:** 2026-05-24

This massive update focuses on bulletproofing your server against data-loss, exploits, and crashes, while vastly improving password security.

### ðŸ›¡ï¸ Major Security Features
- **Double-Login / Name Spoofing Prevention (Offline Mode fixes):**
  - **Critical Exploit Patched:** Malicious users can no longer log in using an identical username to kick out a legitimate player before authentication!
  - **Native Protections:** Implemented surgical packet interception via Spongepowered Mixins removing the vanilla behavior that drops the original connection before verifying identity.
- **BCrypt Password Overhaul:**
  - Migrated from generic internal algorithms to **BCrypt** (Cost Factor 12), the gold standard for password hashing.
  - **Zero Downtime Migration:** When old players log in, the mod automatically upgrades their passwords to BCrypt in the database silently and instantly.
  - Smashed plain-text password vulnerabilities system-wide.
- **Admin UI Hardening:**
  - **Removed "View Passwords":** Admins can no longer view plaintext passwords for users. 
  - **Password Reset Panel:** You can now forcefully factory-reset a user's password to a secure 6-digit random string which is sent securely to the admin initiating the change.

### ðŸŒ The All-New Admin Web Dashboard
- A complete browser-based administrative panel has been introduced! By visiting `http://localhost:<port>`, server owners can now:
  - Monitor all registered accounts and instantly see who is currently online with live coordinates.
  - Remotely Kick, Ban, Mute, or Unban players with the click of a button.
  - View real-time player inventories dynamically mapped onto a visual Web UI grid without even being in the game!
  - Force-reset account passwords remotely.
  - Broadcast server-wide chat messages directly from the Web Interface.

### ðŸ’¾ Persistence & Data Safety
- **Unlogged State Protection (Inventory/Location):**
  - Ever had players lose their inventory because the server crashed before they typed `/login`? Fixed.
  - Player inventory and exact location coordinates are now securely saved to JSON/Database automatically at connection handshake, guaranteeing 100% item retention and void-falling prevention during unintended restarts or disconnects.

### âš™ï¸ Performance & Server Engine Optimizations
- **Fixed "Server Watchdog" 60-Second Crash Loops:**
  - Placed an aggressive 3-second `Fast Timeout` constraint globally on JDBC SQL. Servers will now instantaneously fallback to Local JSON Mode if the configured MySQL Database is offline, bypassing the dreaded 60-second connection timeout that forcibly kills Minecraft servers on launch.
- **Fixed Server Shutdown Deadlocks:**
  - Mapped all background features (BossBar Timers, AFK Kick Threads, Admin Web Panel Executors) directly to Java JVM Daemons. Running the `/stop` command seamlessly powers down the server without ever hanging at `Clearing Modloader`.

---

## v1.6.0 (Latest) - January 2026
### ðŸ›¡ï¸ Security Enhancement Update

### ðŸš¨ Admin Alert System (NEW!)
- **Suspicious Login Detection**: Alerts admins when a player fails login 3+ times
- **Real-time Notifications**: Admins receive instant alerts with Player IP, Failed attempts, and Warnings.
- **Console Logging**: Security events logged for review
- **Configurable**: Enable/disable and customize max attempts in config

---

## v1.4.0 - October 2025
### ðŸŽ‰ Major Updates - GUI Revolution
- ðŸŽ¨ **NEW: Complete Admin GUI System**: Graphical interface for player management
- ðŸ–±ï¸ **Interactive Player Management**: Click-based account management
- ðŸ‘¤ **Real Minecraft Skins**: Display actual player skins
- ðŸ”’ **Ultimate Security**: 13+ comprehensive protection systems for unlogged players
- ðŸ’¬ **Instant Password Access**: Hover tooltips and click-to-view passwords

### ðŸ—¡ï¸ Protection Layers (13 Systems)
1. Movement restriction (waiting area)
2. Block breaking prevention
3. Block placing prevention
4. Item interaction blocking
5. Entity attack prevention
6. Damage dealing prevention
7. Item dropping restriction
8. Item pickup blocking
9. Chat message prevention
10. Container/chest access
11. All interactions disabled
12. Damage immunity
13. Inventory hiding system

---

## v1.3.0 - August 2025
### ðŸŽ‰ Major Updates
- âœ… **Embedded JDBC Drivers**: Added MySQL and MariaDB drivers directly in JAR
- âœ… **Advanced Admin Commands**: Overhaul of admin command system
- âœ… **UUID Support**: Full support for both online and offline players

---

## v1.2.0 - April 2025
- âœ… **Database Support**: Full MySQL/MariaDB integration
- âœ… **SHA-256 Encryption**: Secure password hashing

## v1.1.0 - March 2025
- âœ… **Basic Authentication**: Register and login system with file storage


