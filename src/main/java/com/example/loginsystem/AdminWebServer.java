package com.example.loginsystem;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

public class AdminWebServer {

    private HttpServer server;
    private final LoginSystem loginSystem;
    private final int port;
    private final String password;
    private final Map<String, Long> activeSessions = new HashMap<>(); // Token -> Expiry time
    private java.util.concurrent.ExecutorService executor;

    public AdminWebServer(LoginSystem loginSystem, int port, String password) {
        this.loginSystem = loginSystem;
        this.port = port;
        this.password = password;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new UIHandler());
            server.createContext("/api/login", new LoginHandler());
            server.createContext("/api/players", new PlayersHandler());
            server.createContext("/api/delete", new DeleteHandler());
            
            // New Endpoints
            server.createContext("/api/action", new ActionHandler());
            server.createContext("/api/changepassword", new ChangePasswordHandler());
            server.createContext("/api/inventory", new InventoryHandler());
            server.createContext("/api/broadcast", new BroadcastHandler());
            
            executor = Executors.newFixedThreadPool(2);
            server.setExecutor(executor);
            server.start();
            printStartupMessage();
        } catch (IOException e) {
            LoginSystem.LOGGER.error("Failed to start Admin Web Panel", e);
        }
    }

    private void printStartupMessage() {
        LoginSystem.LOGGER.info("=================================================");
        LoginSystem.LOGGER.info("🌐 Admin Web Panel is ONLINE!");
        LoginSystem.LOGGER.info("👉 Local Server (If hosting on your PC): http://localhost:" + port);
        
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("https://api.ipify.org"))
                        .timeout(java.time.Duration.ofSeconds(5))
                        .build();
                java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                String publicIp = response.body().trim();
                
                LoginSystem.LOGGER.info("👉 Hosted Server (Pterodactyl/VPS): http://" + publicIp + ":" + port);
                LoginSystem.LOGGER.info("=================================================");
            } catch (Exception e) {
                LoginSystem.LOGGER.info("👉 Hosted Server: http://<Your_Server_IP>:" + port);
                LoginSystem.LOGGER.info("=================================================");
            }
        });
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        LoginSystem.LOGGER.info("Admin Web Panel stopped.");
    }

    private boolean isAuthenticated(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader != null) {
            String[] cookies = cookieHeader.split("; ");
            for (String cookie : cookies) {
                if (cookie.startsWith("admin_token=")) {
                    String token = cookie.substring("admin_token=".length());
                    Long expiry = activeSessions.get(token);
                    if (expiry != null && System.currentTimeMillis() < expiry) {
                        activeSessions.put(token, System.currentTimeMillis() + 3600000); // 1 hour
                        return true;
                    } else {
                        activeSessions.remove(token);
                    }
                }
            }
        }
        return false;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response, String contentType) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
    
    private JsonObject parseJsonBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        JsonObject json = new JsonObject();
        // A simple JSON parser just for our specific use cases
        body = body.replace("{", "").replace("}", "").trim();
        String[] pairs = body.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":");
            if (kv.length >= 2) {
                String key = kv[0].replace("\"", "").trim();
                String value = kv[1].replace("\"", "").trim();
                if (kv.length > 2) {
                    for(int i=2; i<kv.length; i++) value += ":" + kv[i].replace("\"", "").trim();
                }
                json.addProperty(key, value);
            }
        }
        return json;
    }

    private class UIHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                String html = getHTMLContent();
                sendResponse(exchange, 200, html, "text/html; charset=UTF-8");
            } else {
                sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
            }
        }
    }

    private class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                JsonObject json = parseJsonBody(exchange);
                String providedPassword = json.has("password") ? json.get("password").getAsString() : "";

                if (password.equals(providedPassword)) {
                    String token = UUID.randomUUID().toString();
                    activeSessions.put(token, System.currentTimeMillis() + 3600000);
                    exchange.getResponseHeaders().add("Set-Cookie", "admin_token=" + token + "; Path=/; HttpOnly; SameSite=Strict");
                    sendResponse(exchange, 200, "{\"success\":true}", "application/json");
                } else {
                    sendResponse(exchange, 401, "{\"success\":false}", "application/json");
                }
            } else {
                sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
            }
        }
    }

    private class PlayersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthenticated(exchange)) {
                sendResponse(exchange, 401, "{\"error\":\"Unauthorized\"}", "application/json");
                return;
            }

            if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                JsonArray playersArray = new JsonArray();
                Map<UUID, String> passwords = loginSystem.getPlayerPasswords();

                for (UUID uuid : passwords.keySet()) {
                    JsonObject playerObj = new JsonObject();
                    playerObj.addProperty("uuid", uuid.toString());
                    
                    String playerName = loginSystem.getPlayerName(LoginSystem.serverInstance, uuid);
                    playerObj.addProperty("name", playerName);
                    
                    String pass = loginSystem.getPasswordForPlayer(uuid);
                    playerObj.addProperty("password", pass);

                    boolean isOnline = false;
                    String coords = "N/A";
                    if (LoginSystem.serverInstance != null) {
                        ServerPlayerEntity playerEntity = LoginSystem.serverInstance.getPlayerManager().getPlayer(uuid);
                        if (playerEntity != null) {
                            isOnline = true;
                            coords = String.format("X: %.1f, Y: %.1f, Z: %.1f", 
                                playerEntity.getX(), playerEntity.getY(), playerEntity.getZ());
                        }
                    }
                    
                    playerObj.addProperty("isOnline", isOnline);
                    playerObj.addProperty("coordinates", coords);
                    playerObj.addProperty("isMuted", loginSystem.isMuted(uuid));
                    
                    Long lastLogin = loginSystem.getLastLogin(uuid);
                    String lastLoginStr = "Never";
                    if (lastLogin != null) {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        lastLoginStr = sdf.format(new java.util.Date(lastLogin));
                    }
                    playerObj.addProperty("lastLogin", lastLoginStr);
                    playerObj.addProperty("isBanned", loginSystem.isBanned(uuid));
                    
                    playersArray.add(playerObj);
                }

                sendResponse(exchange, 200, playersArray.toString(), "application/json");
            } else {
                sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
            }
        }
    }

    private class DeleteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthenticated(exchange)) {
                sendResponse(exchange, 401, "{\"error\":\"Unauthorized\"}", "application/json");
                return;
            }

            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                JsonObject json = parseJsonBody(exchange);
                String uuidStr = json.has("uuid") ? json.get("uuid").getAsString() : "";

                if (!uuidStr.isEmpty()) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        boolean success = loginSystem.deletePlayerViaWeb(uuid);
                        if (success) {
                            sendResponse(exchange, 200, "{\"success\":true}", "application/json");
                        } else {
                            sendResponse(exchange, 500, "{\"success\":false, \"error\":\"Failed to delete\"}", "application/json");
                        }
                    } catch (IllegalArgumentException e) {
                        sendResponse(exchange, 400, "{\"success\":false, \"error\":\"Invalid UUID\"}", "application/json");
                    }
                } else {
                    sendResponse(exchange, 400, "{\"success\":false, \"error\":\"Missing UUID\"}", "application/json");
                }
            } else {
                sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
            }
        }
    }
    
    private class ActionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthenticated(exchange)) {
                sendResponse(exchange, 401, "{\"error\":\"Unauthorized\"}", "application/json");
                return;
            }

            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                JsonObject json = parseJsonBody(exchange);
                String uuidStr = json.has("uuid") ? json.get("uuid").getAsString() : "";
                String action = json.has("action") ? json.get("action").getAsString() : "";
                
                if (!uuidStr.isEmpty() && !action.isEmpty()) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        if (action.equals("kick")) {
                            loginSystem.kickPlayer(uuid, "Kicked by Web Admin");
                        } else if (action.equals("ban")) {
                            int duration = json.has("duration") ? Integer.parseInt(json.get("duration").getAsString()) : 0;
                            loginSystem.banPlayer(uuid, "Banned by Web Admin", duration);
                        } else if (action.equals("unban")) {
                            loginSystem.unbanPlayer(uuid);
                        } else if (action.equals("mute")) {
                            loginSystem.mutePlayer(uuid);
                        } else if (action.equals("unmute")) {
                            loginSystem.unmutePlayer(uuid);
                        }
                        sendResponse(exchange, 200, "{\"success\":true}", "application/json");
                    } catch (Exception e) {
                        sendResponse(exchange, 500, "{\"success\":false, \"error\":\"Action failed\"}", "application/json");
                    }
                } else {
                    sendResponse(exchange, 400, "{\"success\":false, \"error\":\"Missing UUID or action\"}", "application/json");
                }
            }
        }
    }

    private class ChangePasswordHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthenticated(exchange)) {
                sendResponse(exchange, 401, "{\"error\":\"Unauthorized\"}", "application/json");
                return;
            }

            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                JsonObject json = parseJsonBody(exchange);
                String uuidStr = json.has("uuid") ? json.get("uuid").getAsString() : "";
                String newPass = json.has("password") ? json.get("password").getAsString() : "";

                if (!uuidStr.isEmpty() && !newPass.isEmpty()) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        loginSystem.forceChangePassword(uuid, newPass);
                        sendResponse(exchange, 200, "{\"success\":true}", "application/json");
                    } catch (Exception e) {
                        sendResponse(exchange, 500, "{\"success\":false, \"error\":\"Failed to change\"}", "application/json");
                    }
                } else {
                    sendResponse(exchange, 400, "{\"success\":false}", "application/json");
                }
            }
        }
    }

    private class InventoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthenticated(exchange)) {
                sendResponse(exchange, 401, "{\"error\":\"Unauthorized\"}", "application/json");
                return;
            }

            if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.contains("uuid=")) {
                    String uuidStr = query.substring(query.indexOf("uuid=") + 5).split("&")[0];
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        JsonArray inv = loginSystem.getInventoryData(uuid);
                        sendResponse(exchange, 200, inv.toString(), "application/json");
                    } catch (Exception e) {
                        sendResponse(exchange, 400, "[]", "application/json");
                    }
                }
            }
        }
    }

    private class BroadcastHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthenticated(exchange)) {
                sendResponse(exchange, 401, "{\"error\":\"Unauthorized\"}", "application/json");
                return;
            }

            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                JsonObject json = parseJsonBody(exchange);
                String message = json.has("message") ? json.get("message").getAsString() : "";
                // Replace URL encoded spaces if any (basic fix)
                message = message.replace("%20", " ");
                
                if (!message.isEmpty()) {
                    loginSystem.broadcastMessage(message);
                    sendResponse(exchange, 200, "{\"success\":true}", "application/json");
                } else {
                    sendResponse(exchange, 400, "{\"success\":false}", "application/json");
                }
            }
        }
    }

    private String getHTMLContent() {
        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Login System Admin</title>
            <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;600;800&display=swap" rel="stylesheet">
            <style>
                :root {
                    --bg-color: #0f172a;
                    --panel-bg: rgba(30, 41, 59, 0.7);
                    --text-color: #f8fafc;
                    --accent-color: #3b82f6;
                    --accent-hover: #2563eb;
                    --danger-color: #ef4444;
                    --danger-hover: #dc2626;
                    --warning-color: #f59e0b;
                    --success-color: #10b981;
                }
                * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Inter', sans-serif; }
                body {
                    background-color: var(--bg-color);
                    color: var(--text-color);
                    min-height: 100vh;
                    display: flex;
                    flex-direction: column;
                    background: linear-gradient(135deg, #0f172a 0%, #1e1b4b 100%);
                }
                .container { max-width: 1200px; margin: 0 auto; padding: 2rem; width: 100%; }
                header { margin-bottom: 2rem; text-align: center; }
                h1 { font-size: 2.5rem; font-weight: 800; background: linear-gradient(to right, #60a5fa, #a78bfa); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
                .glass-panel {
                    background: var(--panel-bg);
                    backdrop-filter: blur(12px);
                    -webkit-backdrop-filter: blur(12px);
                    border: 1px solid rgba(255,255,255,0.1);
                    border-radius: 1rem;
                    padding: 2rem;
                    box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.2), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
                }
                .hidden { display: none !important; }
                
                input[type="password"], input[type="text"] {
                    padding: 0.75rem 1rem; border-radius: 0.5rem;
                    border: 1px solid rgba(255,255,255,0.2); background: rgba(0,0,0,0.2); color: white;
                    font-size: 1rem; outline: none; transition: border-color 0.2s; width: 100%;
                }
                input:focus { border-color: var(--accent-color); }
                button {
                    padding: 0.75rem 1rem; border: none; border-radius: 0.5rem;
                    background-color: var(--accent-color); color: white;
                    font-size: 0.9rem; font-weight: 600; cursor: pointer;
                    transition: background-color 0.2s, transform 0.1s;
                }
                button:hover { background-color: var(--accent-hover); }
                button:active { transform: scale(0.98); }
                
                .login-form { max-width: 400px; margin: 4rem auto; display: flex; flex-direction: column; gap: 1rem; }
                
                .metrics-bar { display: flex; gap: 1rem; margin-bottom: 1.5rem; }
                .metric-card { flex: 1; background: rgba(0,0,0,0.3); padding: 1rem; border-radius: 0.5rem; text-align: center; border: 1px solid rgba(255,255,255,0.05); }
                .metric-value { font-size: 1.5rem; font-weight: bold; color: var(--accent-color); }
                
                .dashboard-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 2rem; }
                .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 1.5rem; }
                
                .player-card {
                    background: rgba(15, 23, 42, 0.6); border: 1px solid rgba(255,255,255,0.05);
                    border-radius: 0.75rem; padding: 1.25rem; position: relative;
                }
                .player-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; border-bottom: 1px solid rgba(255,255,255,0.1); padding-bottom: 0.5rem; }
                .player-name-area { display: flex; align-items: center; gap: 1rem; }
                .player-skin { width: 48px; height: 48px; border-radius: 8px; background-color: #334155; }
                .player-name { font-size: 1.2rem; font-weight: 600; }
                .status-dot { width: 10px; height: 10px; border-radius: 50%; display: inline-block; margin-left: 8px;}
                .status-online { background-color: var(--success-color); box-shadow: 0 0 8px var(--success-color); }
                .status-offline { background-color: #64748b; }
                
                .player-info { display: flex; flex-direction: column; gap: 0.5rem; font-size: 0.9rem; color: #cbd5e1; margin-bottom: 1rem; }
                
                .action-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0.5rem; margin-top: 1rem;}
                .btn-danger { background-color: transparent; border: 1px solid var(--danger-color); color: var(--danger-color); }
                .btn-danger:hover { background-color: var(--danger-color); color: white; }
                .btn-warning { background-color: transparent; border: 1px solid var(--warning-color); color: var(--warning-color); }
                .btn-warning:hover { background-color: var(--warning-color); color: white; }
                .btn-full { grid-column: 1 / -1; }
                
                .chat-box { margin-top: 2rem; background: rgba(0,0,0,0.3); padding: 1.5rem; border-radius: 1rem; display: flex; gap: 1rem; }
                
                /* Modal Styles */
                .modal-overlay { position: fixed; top:0; left:0; right:0; bottom:0; background: rgba(0,0,0,0.7); display: flex; justify-content: center; align-items: center; z-index: 100; backdrop-filter: blur(5px);}
                .modal-content { background: var(--panel-bg); border: 1px solid rgba(255,255,255,0.1); border-radius: 1rem; padding: 2rem; width: 90%; max-width: 500px; box-shadow: 0 25px 50px -12px rgba(0,0,0,0.5); }
                .inv-grid { display: grid; grid-template-columns: repeat(9, 1fr); gap: 2px; margin-top: 1rem; background: #8b8b8b; padding: 2px; border: 2px solid #373737; border-radius: 4px; }
                .inv-slot { aspect-ratio: 1; background: #c6c6c6; position: relative; border-top: 2px solid #fff; border-left: 2px solid #fff; border-bottom: 2px solid #555; border-right: 2px solid #555; display:flex; justify-content:center; align-items:center; }
                .inv-item { width: 80%; height: 80%; background-size: contain; background-repeat: no-repeat; background-position: center; }
                .inv-count { position: absolute; bottom: -2px; right: 2px; color: white; font-weight: bold; text-shadow: 1px 1px 0 #000; font-size: 0.7rem; }
            </style>
        </head>
        <body>
            <div class="container">
                <header>
                    <h1>Admin Dashboard</h1>
                    <p style="color: #94a3b8; margin-top: 0.5rem;">Minecraft Login System - Advanced Mode</p>
                </header>

                <!-- Login View -->
                <div id="loginView" class="glass-panel login-form">
                    <h2 style="text-align:center; margin-bottom:1rem;">Access Panel</h2>
                    <input type="password" id="adminPassword" placeholder="Enter admin password" onkeypress="if(event.key==='Enter') login()">
                    <button onclick="login()" style="width:100%">Log In</button>
                    <div id="errorMsg" style="color:var(--danger-color); text-align:center; margin-top:1rem;"></div>
                </div>

                <!-- Dashboard View -->
                <div id="dashboardView" class="hidden">
                    <div class="metrics-bar">
                        <div class="metric-card">
                            <div style="color:#94a3b8; font-size:0.9rem">Total Registered</div>
                            <div class="metric-value" id="stat-total">0</div>
                        </div>
                        <div class="metric-card">
                            <div style="color:#94a3b8; font-size:0.9rem">Online Now</div>
                            <div class="metric-value" id="stat-online">0</div>
                        </div>
                    </div>

                    <div class="glass-panel">
                        <div class="dashboard-header">
                            <h2>Player Management</h2>
                            <button onclick="fetchPlayers()">Refresh Data</button>
                        </div>
                        <div id="playersGrid" class="grid"></div>
                    </div>
                    
                    <div class="chat-box">
                        <input type="text" id="chatMsg" placeholder="Broadcast a message to the server..." onkeypress="if(event.key==='Enter') sendChat()">
                        <button onclick="sendChat()" style="white-space:nowrap">Send Broadcast</button>
                    </div>
                </div>

                <div style="text-align: center; margin-top: 2.5rem; color: #64748b; font-size: 0.9rem; font-weight: 500;">
                    &copy; 2026 Notch. All rights reserved.
                </div>
            </div>

            <!-- Modals -->
            <div id="pwdModal" class="modal-overlay hidden">
                <div class="modal-content">
                    <h2 style="margin-bottom: 1rem">Change Password for <span id="pwdTargetName" style="color:var(--accent-color)"></span></h2>
                    <input type="hidden" id="pwdTargetUuid">
                    <input type="text" id="newPwdInput" placeholder="New Password" style="margin-bottom: 1rem">
                    <div style="display:flex; gap:1rem; justify-content:flex-end">
                        <button class="btn-danger" onclick="closeModal('pwdModal')">Cancel</button>
                        <button onclick="submitNewPassword()">Save Password</button>
                    </div>
                </div>
            </div>

            <div id="invModal" class="modal-overlay hidden">
                <div class="modal-content" style="max-width: 600px;">
                    <div style="display:flex; justify-content:space-between; margin-bottom: 1rem">
                        <h2>Inventory: <span id="invTargetName" style="color:var(--accent-color)"></span></h2>
                        <button class="btn-danger" style="padding: 0.25rem 0.5rem" onclick="closeModal('invModal')">X</button>
                    </div>
                    <div id="invGrid" class="inv-grid">
                        <!-- 36 slots (27 main + 9 hotbar) -->
                    </div>
                    <p style="color: #94a3b8; font-size: 0.8rem; margin-top: 0.5rem;">Note: Offline players may not show complete inventory data.</p>
                </div>
            </div>

            <script>
                if (document.cookie.includes('admin_token=')) showDashboard();

                function apiPost(url, bodyObj) {
                    return fetch(url, {
                        method: 'POST',
                        body: JSON.stringify(bodyObj),
                        headers: { 'Content-Type': 'application/json' }
                    }).then(res => res.json());
                }

                function login() {
                    const pass = document.getElementById('adminPassword').value;
                    apiPost('/api/login', { password: pass }).then(data => {
                        if (data.success) showDashboard();
                        else document.getElementById('errorMsg').textContent = 'Invalid password';
                    }).catch(() => document.getElementById('errorMsg').textContent = 'Connection error');
                }

                function showDashboard() {
                    document.getElementById('loginView').classList.add('hidden');
                    document.getElementById('dashboardView').classList.remove('hidden');
                    fetchPlayers();
                }

                function fetchPlayers() {
                    fetch('/api/players').then(res => {
                        if (res.status === 401) { location.reload(); return; }
                        return res.json();
                    }).then(data => {
                        const grid = document.getElementById('playersGrid');
                        grid.innerHTML = '';
                        document.getElementById('stat-total').textContent = data.length;
                        document.getElementById('stat-online').textContent = data.filter(p => p.isOnline).length;
                        
                        data.forEach(player => {
                            const statusClass = player.isOnline ? 'status-online' : 'status-offline';
                            const muteBtnTxt = player.isMuted ? 'Unmute' : 'Mute';
                            const muteAction = player.isMuted ? 'unmute' : 'mute';
                            const banBtnTxt = player.isBanned ? 'Unban' : 'Ban';
                            const banAction = player.isBanned ? 'unban' : 'ban';
                            
                            const card = document.createElement('div');
                            card.className = 'player-card';
                            card.innerHTML = `
                                <div class="player-header">
                                    <div class="player-name-area">
                                        <img src="https://minotar.net/helm/${player.name}/64.png" class="player-skin" alt="skin" onerror="this.src='https://minotar.net/helm/steve/64.png'">
                                        <div class="player-name">
                                            ${escapeHtml(player.name)}
                                            <span class="status-dot ${statusClass}"></span>
                                        </div>
                                    </div>
                                </div>
                                <div class="player-info">
                                    <div><b>Pass:</b> <span style="color:#fcd34d">${escapeHtml(player.password)}</span></div>
                                    <div><b>Coords:</b> ${escapeHtml(player.coordinates)}</div>
                                    <div style="font-size: 0.8rem; color: #a1a1aa; margin-top: 4px;">🕒 Last Login: ${escapeHtml(player.lastLogin)}</div>
                                    ${player.isBanned ? '<div style="font-size: 0.8rem; color: #ef4444; margin-top: 4px; font-weight: bold;">⛔ BANNED</div>' : ''}
                                </div>
                                <div class="action-grid">
                                    <button class="btn-full" onclick="openPwdModal('${player.uuid}', '${escapeHtml(player.name)}')">Change Password</button>
                                    <button onclick="viewInventory('${player.uuid}', '${escapeHtml(player.name)}')">View Inv</button>
                                    <button class="btn-warning" onclick="doAction('${player.uuid}', '${muteAction}')">${muteBtnTxt}</button>
                                    <button class="btn-warning" onclick="doAction('${player.uuid}', 'kick')">Kick</button>
                                    <button class="btn-danger" onclick="doAction('${player.uuid}', '${banAction}')">${banBtnTxt}</button>
                                    <button class="btn-danger btn-full" onclick="deletePlayer('${player.uuid}', '${escapeHtml(player.name)}')">Delete Data</button>
                                </div>
                            `;
                            grid.appendChild(card);
                        });
                    });
                }

                function doAction(uuid, action) {
                    let payload = { uuid: uuid, action: action };
                    if (action === 'ban') {
                        let days = prompt("Enter ban duration in days (Leave blank or 0 for permanent):", "0");
                        if (days === null) return; // User cancelled
                        payload.duration = parseInt(days) || 0;
                    }
                    apiPost('/api/action', payload).then(d => {
                        if (d.success) fetchPlayers();
                    });
                }

                function deletePlayer(uuid, name) {
                    if (confirm(`Delete ALL data for ${name}?`)) {
                        apiPost('/api/delete', { uuid: uuid }).then(d => {
                            if (d.success) fetchPlayers();
                        });
                    }
                }

                function sendChat() {
                    const msg = document.getElementById('chatMsg').value;
                    if(!msg) return;
                    apiPost('/api/broadcast', { message: msg }).then(d => {
                        if (d.success) document.getElementById('chatMsg').value = '';
                    });
                }

                // Password Modal
                function openPwdModal(uuid, name) {
                    document.getElementById('pwdTargetUuid').value = uuid;
                    document.getElementById('pwdTargetName').textContent = name;
                    document.getElementById('newPwdInput').value = '';
                    document.getElementById('pwdModal').classList.remove('hidden');
                }
                
                function submitNewPassword() {
                    const uuid = document.getElementById('pwdTargetUuid').value;
                    const pwd = document.getElementById('newPwdInput').value;
                    if(!pwd) return;
                    apiPost('/api/changepassword', { uuid: uuid, password: pwd }).then(d => {
                        if (d.success) {
                            closeModal('pwdModal');
                            fetchPlayers();
                        }
                    });
                }

                // Inventory Modal
                function viewInventory(uuid, name) {
                    document.getElementById('invTargetName').textContent = name;
                    const grid = document.getElementById('invGrid');
                    grid.innerHTML = '';
                    // Create 36 empty slots
                    for(let i=0; i<36; i++) {
                        grid.innerHTML += `<div class="inv-slot" id="slot-${i}"></div>`;
                    }
                    
                    fetch('/api/inventory?uuid=' + uuid).then(r=>r.json()).then(items => {
                        items.forEach(item => {
                            if(item.slot < 36) {
                                const slot = document.getElementById('slot-' + item.slot);
                                slot.title = item.name;
                                slot.innerHTML = `
                                    <div style="font-size: 0.6rem; text-align: center; word-break: break-all; margin-top: 15%; color: white;">${escapeHtml(item.name.substring(0, 15))}</div>
                                    <div class="inv-count">${item.count > 1 ? item.count : ''}</div>
                                `;
                            }
                        });
                        document.getElementById('invModal').classList.remove('hidden');
                    });
                }

                function closeModal(id) {
                    document.getElementById(id).classList.add('hidden');
                }

                function escapeHtml(unsafe) {
                    if (!unsafe) return '';
                    return unsafe.toString().replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
                }
            </script>
        </body>
        </html>
        """;
    }
}
