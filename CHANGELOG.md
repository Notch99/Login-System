# ðŸ” Login System Mod - Fabric Edition Changelog

## v2.2 - Item Drop & Stability Hotfix

### 🛡️ Bug Fixes & Improvements
- **Item Drop Desync Fixed:** Completely redesigned the item drop prevention mechanism. Intercepts drops at the network packet level (PlayerActionC2SPacket, ClickSlotC2SPacket) to prevent the client from becoming visually desynced when attempting to drop items before logging in.

## v2.1.0 â€” The Ultimate Security & Stability Update ðŸš€
**Release Date:** 2026-05-24

This massive update focuses on bulletproofing your server against data-loss, exploits, and crashes, while vastly improving password security.

### ðŸ›¡ï¸ Major Security Features
- **Double-Login / Name Spoofing Prevention (Offline Mode fixes):**
  - **Critical Exploit Patched:** Malicious users can no longer log in using an identical username to kick out a legitimate player before authentication!
  - **Native Protections:** Implemented surgical packet interception via JVM Reflection Hooks removing the vanilla behavior that drops the original connection before verifying identity.
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

## ðŸš€ Fabric Conversion Update
- âœ… **Converted from Forge to Fabric**: A complete port of the mod for fabric environments.
- âœ… **Updated APIs**: Transitioned all systems to use Fabric instead of MinecraftForge.
- âœ… **Version Support**: Official support for Minecraft 1.20 to 1.20.6.
- âœ… **Fabric API Integration**: Fully compatible and integrated with Fabric API.
- âœ… **Maintained Features**: Preserved all original functionality including:
  - Secure Player registration & login with SHA-256
  - Database support (MySQL/MariaDB) & File storage fallback
  - Waiting area teleportation & Inventory hiding
  - Blindness effect, movement restriction, and 13+ player protections
  - Comprehensive admin tools and commands.

