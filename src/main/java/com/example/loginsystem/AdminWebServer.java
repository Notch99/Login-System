package com.example.loginsystem;

import net.neoforged.neoforge.server.ServerLifecycleHooks;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

public class AdminWebServer {

    private HttpServer server;
    private final LoginSystem loginSystem;
    private final int port;
    private final String password;
    private final Map<String, Long> activeSessions = new HashMap<>();
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
            server.createContext("/api/action", new ActionHandler());
            server.createContext("/api/changepassword", new ChangePasswordHandler());
            server.createContext("/api/inventory", new InventoryHandler());
            server.createContext("/api/broadcast", new BroadcastHandler());
            server.createContext("/api/claims", new ClaimsHandler());
            server.createContext("/api/deleteclaim", new DeleteClaimHandler());

            executor = Executors.newFixedThreadPool(2);
            server.setExecutor(executor);
            server.start();
            printStartupMessage();
        } catch (java.net.BindException e) {
            System.out.println("");
            System.out.println("========================================================================");
            System.out.println("  ⚠️  [Admin Web Panel] Port " + port + " is ALREADY IN USE!  ⚠️");
            System.out.println("  👉 Please change 'webPanelPort' in your config file (e.g. 20037)");
            System.out.println("========================================================================");
            System.out.println("");
        } catch (IOException e) {
            System.err.println("[Admin Web Panel] Failed to start: " + e.getMessage());
        }
    }

        private void printStartupMessage() {
        CompletableFuture.runAsync(() -> {
            String host = loginSystem.getWebPanelHost();
            String finalHost = host;
            if (finalHost == null || finalHost.trim().isEmpty() || finalHost.equalsIgnoreCase("auto")) {
                String publicIp = "127.0.0.1";
                String[] ipServices = {
                    "https://api.ipify.org",
                    "https://checkip.amazonaws.com",
                    "https://icanhazip.com"
                };
                for (String service : ipServices) {
                    try {
                        java.net.URI uri = java.net.URI.create(service);
                        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                                .connectTimeout(java.time.Duration.ofSeconds(3))
                                .build();
                        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                                .uri(uri)
                                .timeout(java.time.Duration.ofSeconds(3))
                                .GET()
                                .build();
                        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() == 200 && response.body() != null && !response.body().trim().isEmpty()) {
                            publicIp = response.body().trim();
                            break;
                        }
                    } catch (Exception ignored) {}
                }
                finalHost = publicIp;
            } else {
                finalHost = finalHost.trim();
            }

            String url = "http://" + finalHost + ":" + port;
            System.out.println("");
            System.out.println("========================================================================");
            System.out.println("          🌐  SERVER ADMIN WEB DASHBOARD IS ONLINE!  🌐");
            System.out.println("========================================================================");
            System.out.println("  👉 Open Dashboard : " + url);
            System.out.println("  🔑 Web Password   : " + password);
            System.out.println("  ⚙️ Server Port     : " + port);
            System.out.println("  🛡️ Mod Features   : Login Security + Claims & 2D World Map (Unified)");
            System.out.println("========================================================================");
            System.out.println("");
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
                    if (activeSessions.containsKey(token)) {
                        long expiry = activeSessions.get(token);
                        if (System.currentTimeMillis() < expiry) {
                            return true;
                        } else {
                            activeSessions.remove(token);
                        }
                    }
                }
            }
        }
        return false;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response, String contentType) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
        exchange.getResponseHeaders().set("Pragma", "no-cache");
        exchange.getResponseHeaders().set("Expires", "0");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private JsonObject parseJsonBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (body.isEmpty()) return new JsonObject();
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private class UIHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || !path.startsWith("/api/")) {
                String html = getHTMLContent();
                sendResponse(exchange, 200, html, "text/html");
            } else {
                sendResponse(exchange, 404, "Not Found", "text/plain");
            }
        }
    }

    private class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                JsonObject json = parseJsonBody(exchange);
                String pass = json.has("password") ? json.get("password").getAsString() : "";

                if (password.equals(pass)) {
                    String token = UUID.randomUUID().toString();
                    activeSessions.put(token, System.currentTimeMillis() + (3600 * 1000 * 24));
                    exchange.getResponseHeaders().add("Set-Cookie", "admin_token=" + token + "; Path=/; HttpOnly; Max-Age=86400");
                    sendResponse(exchange, 200, "{\"success\":true}", "application/json");
                } else {
                    sendResponse(exchange, 401, "{\"success\":false, \"error\":\"Invalid Password\"}", "application/json");
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
                JsonArray jsonArray = new JsonArray();
                Map<UUID, String> users = loginSystem.getPlayerPasswords();
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

                for (Map.Entry<UUID, String> entry : users.entrySet()) {
                    UUID uuid = entry.getKey();
                    JsonObject playerObj = new JsonObject();
                    playerObj.addProperty("uuid", uuid.toString());
                    String playerName = loginSystem.getPlayerName(server, uuid);
                    playerObj.addProperty("name", playerName != null ? playerName : "Unknown (" + uuid.toString().substring(0, 8) + ")");
                    playerObj.addProperty("password", entry.getValue());
                    
                    ServerPlayer playerEntity = server != null ? server.getPlayerList().getPlayer(uuid) : null;
                    boolean isOnline = playerEntity != null;
                    playerObj.addProperty("isOnline", isOnline);
                    String coords = playerEntity != null ? String.format("%.0f, %.0f, %.0f", playerEntity.getX(), playerEntity.getY(), playerEntity.getZ()) : "Offline";
                    playerObj.addProperty("coordinates", coords);
                    if (playerEntity != null) {
                        playerObj.addProperty("x", playerEntity.getX());
                        playerObj.addProperty("y", playerEntity.getY());
                        playerObj.addProperty("z", playerEntity.getZ());
                        playerObj.addProperty("dimension", playerEntity.level().dimension().toString());
                    }
                    playerObj.addProperty("lastLogin", "Recorded");
                    playerObj.addProperty("isBanned", loginSystem.isBanned(uuid));
                    playerObj.addProperty("isMuted", loginSystem.isMuted(uuid));
                    jsonArray.add(playerObj);
                }

                sendResponse(exchange, 200, jsonArray.toString(), "application/json");
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
                        boolean deleted = loginSystem.deletePlayerData(uuid);
                        if (deleted) {
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
                        sendResponse(exchange, 500, "{\"success\":false, \"error\":\"Failed to change password\"}", "application/json");
                    }
                } else {
                    sendResponse(exchange, 400, "{\"success\":false, \"error\":\"Missing UUID or password\"}", "application/json");
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

            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.startsWith("uuid=")) {
                String uuidStr = query.substring("uuid=".length());
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    JsonArray invJson = loginSystem.getInventoryData(uuid);
                    sendResponse(exchange, 200, invJson.toString(), "application/json");
                    return;
                } catch (Exception e) {
                    sendResponse(exchange, 400, "{\"error\":\"Invalid UUID\"}", "application/json");
                    return;
                }
            }
            sendResponse(exchange, 400, "{\"error\":\"Missing uuid query\"}", "application/json");
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
                String msg = json.has("message") ? json.get("message").getAsString() : "";
                if (!msg.isEmpty()) {
                    loginSystem.broadcastMessage(msg);
                    sendResponse(exchange, 200, "{\"success\":true}", "application/json");
                } else {
                    sendResponse(exchange, 400, "{\"error\":\"Empty message\"}", "application/json");
                }
            }
        }
    }

    private class ClaimsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthenticated(exchange)) {
                sendResponse(exchange, 401, "{\"error\":\"Unauthorized\"}", "application/json");
                return;
            }
            if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                File claimsFile = new File("config/claimmod_claims.json");
                if (claimsFile.exists()) {
                    try {
                        String content = Files.readString(claimsFile.toPath(), StandardCharsets.UTF_8);
                        sendResponse(exchange, 200, content, "application/json");
                        return;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                sendResponse(exchange, 200, "[]", "application/json");
            } else {
                sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
            }
        }
    }

    private class DeleteClaimHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthenticated(exchange)) {
                sendResponse(exchange, 401, "{\"error\":\"Unauthorized\"}", "application/json");
                return;
            }
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                JsonObject json = parseJsonBody(exchange);
                int claimId = json.has("claimId") ? json.get("claimId").getAsInt() : 0;
                if (claimId > 0) {
                    boolean removed = false;
                    try {
                        Class<?> clazz = Class.forName("com.example.claimmod.ClaimManager");
                        java.lang.reflect.Method m = clazz.getMethod("removeClaim", int.class, java.util.UUID.class);
                        Object res = m.invoke(null, claimId, null);
                        if (res instanceof Boolean b && b) {
                            removed = true;
                        }
                    } catch (Throwable ignored) {}

                    File claimsFile = new File("config/claimmod_claims.json");
                    if (claimsFile.exists()) {
                        try {
                            String content = Files.readString(claimsFile.toPath(), StandardCharsets.UTF_8);
                            JsonArray arr = JsonParser.parseString(content).getAsJsonArray();
                            JsonArray newArr = new JsonArray();
                            for (JsonElement el : arr) {
                                JsonObject obj = el.getAsJsonObject();
                                if (obj.has("id") && obj.get("id").getAsInt() == claimId) {
                                    removed = true;
                                } else {
                                    newArr.add(obj);
                                }
                            }
                            if (removed) {
                                Files.writeString(claimsFile.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(newArr));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    if (removed) {
                        sendResponse(exchange, 200, "{\"success\":true}", "application/json");
                        return;
                    }
                }
                sendResponse(exchange, 400, "{\"success\":false, \"error\":\"Claim not found\"}", "application/json");
            } else {
                sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
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
    <title>Server Admin Panel</title>
    <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-color: #0b0f19;
            --panel-bg: #121829;
            --surface-hover: #1a2238;
            --border: #1f293d;
            --accent-color: #38bdf8;
            --accent-gradient: linear-gradient(135deg, #38bdf8 0%, #818cf8 100%);
            --text-main: #f8fafc;
            --text-dim: #94a3b8;
            --danger-color: #ef4444;
            --success-color: #10b981;
            --warning-color: #f59e0b;
        }

        * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Outfit', sans-serif; }
        body { background-color: var(--bg-color); color: var(--text-main); min-height: 100vh; display: flex; flex-direction: column; }

        .hidden { display: none !important; }

        /* App Header */
        header {
            background: rgba(18, 24, 41, 0.85); backdrop-filter: blur(12px); border-bottom: 1px solid var(--border);
            padding: 16px 36px; display: flex; align-items: center; justify-content: space-between; position: sticky; top: 0; z-index: 100;
        }
        .header-title-box { display: flex; align-items: center; gap: 12px; }
        .header-title-box h1 { font-size: 20px; font-weight: 700; }
        .header-title-box .badge {
            background: rgba(56,189,248,0.15); color: #38bdf8; border: 1px solid rgba(56,189,248,0.3);
            font-size: 12px; padding: 4px 10px; border-radius: 20px; font-weight: 600;
        }

        /* Mod Switcher Tabs */
        .mod-tabs-nav {
            display: flex; background: #070a12; border: 1px solid var(--border); border-radius: 14px; padding: 4px; gap: 4px;
        }
        .mod-tab-btn {
            background: transparent; border: none; color: var(--text-dim); padding: 9px 20px; border-radius: 10px;
            font-size: 14px; font-weight: 600; cursor: pointer; transition: 0.25s; display: flex; align-items: center; gap: 8px;
        }
        .mod-tab-btn:hover { color: #fff; background: rgba(255,255,255,0.05); }
        .mod-tab-btn.active {
            background: var(--accent-gradient); color: #fff; box-shadow: 0 4px 14px rgba(56,189,248,0.3);
        }

        .container { max-width: 1440px; margin: 0 auto; padding: 28px 24px; width: 100%; display: flex; flex-direction: column; gap: 24px; }

        /* Login Modal */
        .login-wrapper { display: flex; justify-content: center; align-items: center; min-height: 70vh; }
        .login-card {
            background: var(--panel-bg); border: 1px solid var(--border); border-radius: 20px;
            padding: 40px; width: 100%; max-width: 420px; text-align: center; box-shadow: 0 20px 40px rgba(0,0,0,0.5);
        }
        .login-card h2 { font-size: 26px; font-weight: 700; margin-bottom: 8px; background: var(--accent-gradient); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
        .login-card p { color: var(--text-dim); font-size: 14px; margin-bottom: 24px; }
        .login-input {
            width: 100%; padding: 14px 18px; border-radius: 12px; background: #070a12;
            border: 1px solid var(--border); color: #fff; font-size: 15px; margin-bottom: 18px; outline: none; transition: 0.2s;
        }
        .login-input:focus { border-color: var(--accent-color); box-shadow: 0 0 0 3px rgba(56,189,248,0.25); }
        .btn-primary {
            width: 100%; padding: 14px; border: none; border-radius: 12px; background: var(--accent-gradient);
            color: #fff; font-size: 15px; font-weight: 600; cursor: pointer; transition: 0.3s;
        }
        .btn-primary:hover { opacity: 0.92; transform: translateY(-1px); box-shadow: 0 8px 20px rgba(56,189,248,0.35); }

        /* Metrics */
        .metrics-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 16px; }
        .metric-card {
            background: var(--panel-bg); border: 1px solid var(--border); border-radius: 16px; padding: 22px;
            display: flex; flex-direction: column; gap: 6px; position: relative; overflow: hidden;
        }
        .metric-card::before { content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px; background: var(--accent-gradient); }
        .metric-label { font-size: 13px; color: var(--text-dim); font-weight: 500; text-transform: uppercase; letter-spacing: 0.5px; }
        .metric-value { font-size: 28px; font-weight: 800; color: #fff; }

        /* Panels */
        .glass-panel {
            background: var(--panel-bg); border: 1px solid var(--border); border-radius: 18px; padding: 24px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.2);
        }
        .panel-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 14px; margin-bottom: 20px; }
        .panel-title { font-size: 18px; font-weight: 700; display: flex; align-items: center; gap: 10px; }

        /* Player Grid */
        .players-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 16px; }
        .player-card {
            background: #070a12; border: 1px solid var(--border); border-radius: 14px; padding: 18px;
            display: flex; flex-direction: column; gap: 12px; transition: 0.2s;
        }
        .player-card:hover { border-color: rgba(56,189,248,0.4); transform: translateY(-2px); }
        .player-card-head { display: flex; align-items: center; justify-content: space-between; }
        .player-card-name-box { display: flex; align-items: center; gap: 10px; }
        .player-skin { width: 40px; height: 40px; border-radius: 8px; background: #1e293b; }
        .player-name { font-size: 16px; font-weight: 700; }
        .status-dot { width: 9px; height: 9px; border-radius: 50%; display: inline-block; margin-left: 6px; }
        .online { background: #10b981; box-shadow: 0 0 8px #10b981; }
        .offline { background: #64748b; }
        .player-meta { font-size: 13px; color: var(--text-dim); display: flex; flex-direction: column; gap: 4px; }
        .player-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; margin-top: 6px; }

        button.btn-ctrl {
            background: #121829; border: 1px solid var(--border); color: var(--text-main); padding: 8px 12px; border-radius: 8px;
            font-size: 12px; font-weight: 600; cursor: pointer; transition: 0.2s;
        }
        button.btn-ctrl:hover { background: var(--surface-hover); border-color: var(--accent-color); }
        button.btn-danger { color: #f87171; border-color: rgba(239,68,68,0.3); background: rgba(239,68,68,0.1); }
        button.btn-danger:hover { background: #ef4444; color: #fff; }
        button.btn-warning { color: #fbbf24; border-color: rgba(245,158,11,0.3); background: rgba(245,158,11,0.1); }
        button.btn-warning:hover { background: #f59e0b; color: #fff; }
        button.btn-full { grid-column: 1 / -1; }

        /* Broadcast box */
        .broadcast-bar { display: flex; gap: 10px; margin-top: 14px; }
        .broadcast-input {
            flex: 1; padding: 12px 16px; border-radius: 10px; background: #070a12; border: 1px solid var(--border);
            color: #fff; font-size: 14px; outline: none;
        }
        .broadcast-input:focus { border-color: var(--accent-color); }

        /* 🗺️ Interactive 60FPS World Map */
        .canvas-container {
            position: relative; width: 100%; height: 560px; background: #050811; border: 1px solid var(--border);
            border-radius: 14px; overflow: hidden; cursor: grab; box-shadow: inset 0 0 40px rgba(0,0,0,0.8);
        }
        .canvas-container:active { cursor: grabbing; }
        canvas#loginWorldMap { width: 100%; height: 100%; display: block; }
        .map-coords-badge {
            position: absolute; bottom: 12px; left: 12px; background: rgba(11,15,25,0.88); backdrop-filter: blur(8px);
            border: 1px solid var(--border); border-radius: 8px; padding: 7px 14px; font-size: 12px; color: var(--text-main); font-family: monospace;
            box-shadow: 0 4px 12px rgba(0,0,0,0.4);
        }
        .map-legend {
            position: absolute; top: 12px; right: 12px; background: rgba(11,15,25,0.88); backdrop-filter: blur(8px);
            border: 1px solid var(--border); border-radius: 10px; padding: 10px 14px; display: flex; flex-direction: column; gap: 6px; font-size: 11px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.4);
        }
        .legend-row { display: flex; align-items: center; gap: 7px; }
        .legend-dot { width: 9px; height: 9px; border-radius: 3px; }
        #mapTooltip {
            position: absolute; display: none; pointer-events: none; background: rgba(18,24,41,0.95); backdrop-filter: blur(12px);
            border: 1px solid var(--accent-color); border-radius: 10px; padding: 10px 14px; box-shadow: 0 10px 25px rgba(0,0,0,0.6);
            z-index: 50; max-width: 280px; font-size: 12px; transform: translate(15px, 15px);
        }

        /* Claims Table */
        .table-wrap { overflow-x: auto; margin-top: 14px; }
        table { width: 100%; border-collapse: collapse; text-align: left; font-size: 13px; }
        th { background: #070a12; color: var(--text-dim); padding: 12px 16px; font-weight: 600; border-bottom: 1px solid var(--border); }
        td { padding: 12px 16px; border-bottom: 1px solid rgba(31,41,61,0.4); vertical-align: middle; }
        tr:hover td { background: var(--surface-hover); }
        .badge-level { display: inline-block; padding: 2px 7px; border-radius: 5px; font-size: 11px; font-weight: 700; }
        .lvl-1 { background: rgba(0,210,255,0.15); color: #00d2ff; border: 1px solid #00d2ff; }
        .lvl-2 { background: rgba(0,255,136,0.15); color: #00ff88; border: 1px solid #00ff88; }
        .lvl-3 { background: rgba(255,221,0,0.15); color: #ffdd00; border: 1px solid #ffdd00; }
        .lvl-4 { background: rgba(255,153,0,0.15); color: #ff9900; border: 1px solid #ff9900; }
        .lvl-5 { background: rgba(179,71,255,0.15); color: #b347ff; border: 1px solid #b347ff; }
        .lvl-6 { background: rgba(255,46,99,0.15); color: #ff2e63; border: 1px solid #ff2e63; }

        /* Inventory modal */
        .modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.75); display: flex; justify-content: center; align-items: center; z-index: 200; backdrop-filter: blur(6px); }
        .modal-card { background: var(--panel-bg); border: 1px solid var(--border); border-radius: 16px; padding: 24px; width: 90%; max-width: 520px; }
        .inv-grid { display: grid; grid-template-columns: repeat(9, 1fr); gap: 3px; background: #334155; padding: 4px; border-radius: 8px; margin-top: 12px; }
        .inv-slot { aspect-ratio: 1; background: #1e293b; border-radius: 4px; display: flex; justify-content: center; align-items: center; font-size: 10px; color: #fff; position: relative; }
        .inv-count { position: absolute; bottom: 2px; right: 4px; font-weight: bold; color: #facc15; font-size: 10px; }
    </style>
</head>
<body>

    <!-- Header -->
    <header>
        <div class="header-title-box">
            <h1>🌐 Server Admin Dashboard</h1>
            <span class="badge">Live 26.1+</span>
        </div>

        <!-- 🚀 MOD SWITCHER TABS -->
        <div class="mod-tabs-nav" id="modTabsNav">
            <button class="mod-tab-btn active" id="tabBtnLogin" onclick="switchModTab('login')">
                <span>🔐</span> Login System Mod
            </button>
            <button class="mod-tab-btn" id="tabBtnClaim" onclick="switchModTab('claim')">
                <span>🛡️</span> Claim Mod & World Map
            </button>
        </div>

        <div style="display:flex; gap:10px;">
            <button class="btn-ctrl" onclick="refreshCurrentTab()">🔄 Refresh</button>
            <button class="btn-ctrl btn-danger" onclick="logout()">Logout</button>
        </div>
    </header>

    <div class="container">

        <!-- Login View -->
        <div id="loginView" class="login-wrapper">
            <div class="login-card">
                <h2>🔐 Admin Access</h2>
                <p>Enter the administrator password to access the control panel</p>
                <input type="password" id="adminPassword" class="login-input" placeholder="Admin Password" onkeypress="if(event.key==='Enter') login()">
                <button class="btn-primary" onclick="login()">Enter Dashboard</button>
                <div id="errorMsg" style="color: #ef4444; font-size: 13px; margin-top: 12px; display: none;"></div>
            </div>
        </div>

        <!-- Main Dashboard View -->
        <div id="dashboardView" class="hidden">

            <!-- ================= SECTION 1: LOGIN MOD ================= -->
            <div id="loginModSection">
                <div class="metrics-grid" style="margin-bottom: 24px;">
                    <div class="metric-card">
                        <span class="metric-label">Total Registered Players</span>
                        <span class="metric-value" id="stat-total">0</span>
                    </div>
                    <div class="metric-card">
                        <span class="metric-label">Online Players</span>
                        <span class="metric-value" id="stat-online" style="color: #10b981;">0</span>
                    </div>
                    <div class="metric-card">
                        <span class="metric-label">Banned Players</span>
                        <span class="metric-value" id="stat-banned" style="color: #ef4444;">0</span>
                    </div>
                </div>

                <div class="glass-panel" style="margin-bottom: 24px;">
                    <div class="panel-header">
                        <div class="panel-title">👥 Player Accounts & Passwords</div>
                        <input type="text" id="playerSearch" class="broadcast-input" style="max-width: 240px; padding: 8px 12px;" placeholder="🔍 Search player..." onkeyup="filterPlayers()">
                    </div>
                    <div id="playersGrid" class="players-grid"></div>
                </div>

                <div class="glass-panel">
                    <div class="panel-title" style="margin-bottom: 12px;">📢 Server Announcement (Broadcast)</div>
                    <div class="broadcast-bar">
                        <input type="text" id="chatMsg" class="broadcast-input" placeholder="Type message to broadcast in chat..." onkeypress="if(event.key==='Enter') sendChat()">
                        <button class="btn-primary" style="width: auto; padding: 0 24px;" onclick="sendChat()">Broadcast</button>
                    </div>
                </div>
            </div>

            <!-- ================= SECTION 2: CLAIM MOD ================= -->
            <div id="claimModSection" class="hidden">
                <div class="metrics-grid" style="margin-bottom: 24px;">
                    <div class="metric-card">
                        <span class="metric-label">Total Active Claims</span>
                        <span class="metric-value" id="stat-claims-count">0</span>
                    </div>
                    <div class="metric-card">
                        <span class="metric-label">Protected Blocks Area</span>
                        <span class="metric-value" id="stat-claims-blocks">0</span>
                    </div>
                    <div class="metric-card">
                        <span class="metric-label">Unique Claim Owners</span>
                        <span class="metric-value" id="stat-claims-owners">0</span>
                    </div>
                </div>

                <!-- 🗺️ Interactive 60FPS World Claims Map -->
                <div class="glass-panel" style="margin-bottom: 24px;">
                    <div class="panel-header">
                        <div class="panel-title">🗺️ Interactive 2D World Claims Map & Radar</div>
                        <div style="display:flex; gap:8px; align-items:center;">
                            <select id="claimDimSelect" onchange="clearTileCacheAndRender()" class="broadcast-input" style="width:auto; padding:6px 14px; background:#070a12; color:#fff; border:1px solid var(--border); cursor:pointer;">
                                <option value="minecraft:overworld" style="background:#070a12; color:#fff;">🌍 Overworld</option>
                                <option value="minecraft:the_nether" style="background:#070a12; color:#fff;">🔥 The Nether</option>
                                <option value="minecraft:the_end" style="background:#070a12; color:#fff;">🔮 The End</option>
                            </select>
                            <button class="btn-ctrl" onclick="zoomMapIn()">➕ Zoom In</button>
                            <button class="btn-ctrl" onclick="zoomMapOut()">➖ Zoom Out</button>
                            <button class="btn-ctrl" onclick="resetClaimsMap()">🎯 Reset</button>
                        </div>
                    </div>

                    <div class="canvas-container" id="canvasMapWrap">
                        <canvas id="loginWorldMap"></canvas>
                        <div class="map-coords-badge" id="loginMapCoords">Center: [0, 0] | Zoom: 1.00x</div>
                        <div class="map-legend">
                            <div class="legend-row"><div class="legend-dot" style="background:#00d2ff;"></div> Level 1 (100b)</div>
                            <div class="legend-row"><div class="legend-dot" style="background:#00ff88;"></div> Level 2 (400b)</div>
                            <div class="legend-row"><div class="legend-dot" style="background:#ffdd00;"></div> Level 3 (900b)</div>
                            <div class="legend-row"><div class="legend-dot" style="background:#ff9900;"></div> Level 4 (1600b)</div>
                            <div class="legend-row"><div class="legend-dot" style="background:#b347ff;"></div> Level 5 (2500b)</div>
                            <div class="legend-row"><div class="legend-dot" style="background:#ff2e63;"></div> Level 6 (4096b)</div>
                            <div class="legend-row" style="margin-top:4px; padding-top:4px; border-top:1px solid #1f293d;"><div class="legend-dot" style="background:#22c55e;"></div> 👤 Online Players</div>
                            <div class="legend-row"><div class="legend-dot" style="background:#fbbf24;"></div> 🎯 Spawn (0, 0)</div>
                        </div>
                        <div id="mapTooltip"></div>
                    </div>
                </div>

                <!-- Claims Table -->
                <div class="glass-panel">
                    <div class="panel-header">
                        <div class="panel-title">🛡️ All Claims List</div>
                        <input type="text" id="claimsSearch" class="broadcast-input" style="max-width: 240px; padding: 8px 12px;" placeholder="🔍 Search owner or ID..." onkeyup="filterClaims()">
                    </div>
                    <div class="table-wrap">
                        <table>
                            <thead>
                                <tr>
                                    <th>ID</th>
                                    <th>Owner</th>
                                    <th>Dimension</th>
                                    <th>Bounds (X, Z)</th>
                                    <th>Size</th>
                                    <th>Level</th>
                                    <th>Trusted</th>
                                    <th>Action</th>
                                </tr>
                            </thead>
                            <tbody id="claimsTableBody">
                                <tr><td colspan="8" style="text-align: center; color: var(--text-dim);">Loading claims...</td></tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>

        </div>
    </div>

    <!-- Inventory Modal -->
    <div id="invModal" class="modal-overlay hidden">
        <div class="modal-card">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px;">
                <h3 style="font-size:16px;">🎒 Inventory: <span id="invTargetName" style="color:var(--accent-color)"></span></h3>
                <button class="btn-ctrl btn-danger" style="padding:4px 8px;" onclick="closeModal('invModal')">✕</button>
            </div>
            <div id="invGrid" class="inv-grid"></div>
        </div>
    </div>

    <!-- Password Modal -->
    <div id="pwdModal" class="modal-overlay hidden">
        <div class="modal-card" style="max-width:380px;">
            <h3 style="margin-bottom:12px;">🔑 Change Password: <span id="pwdTargetName" style="color:var(--accent-color)"></span></h3>
            <input type="hidden" id="pwdTargetUuid">
            <input type="text" id="newPwdInput" class="broadcast-input" placeholder="Enter new password" style="width:100%; margin-bottom:14px;">
            <div style="display:flex; gap:10px; justify-content:flex-end;">
                <button class="btn-ctrl btn-danger" onclick="closeModal('pwdModal')">Cancel</button>
                <button class="btn-primary" style="width:auto; padding:8px 18px;" onclick="submitNewPassword()">Save</button>
            </div>
        </div>
    </div>

    <script>
        let currentActiveMod = 'login';
        let gPlayers = [];
        let gClaims = [];

        // Map state
        let cMapScale = 1.0;
        let cMapOffX = 0;
        let cMapOffZ = 0;
        let cDragging = false;
        let cDragStartX = 0;
        let cDragStartY = 0;
        let renderScheduled = false;

        // GPU / Tile Caching Map
        const tileCache = new Map();
        const TILE_SIZE = 256; // 256 blocks per tile
        const TILE_PIXELS = 64; // 64x64 buffer per tile (ultra-crisp and 0 CPU overhead)

        const LEVEL_COLORS = {
            1: '#00d2ff',
            2: '#00ff88',
            3: '#ffdd00',
            4: '#ff9900',
            5: '#b347ff',
            6: '#ff2e63'
        };

        function getNormDim(dim) {
            if (!dim) return 'minecraft:overworld';
            const s = String(dim).toLowerCase();
            if (s.includes('nether')) return 'minecraft:the_nether';
            if (s.includes('end')) return 'minecraft:the_end';
            return 'minecraft:overworld';
        }

        // Fast Biome Function
        function getBiomeInfo(x, z, dim) {
            if (dim === 'minecraft:the_nether') {
                const n = Math.sin(x * 0.005) * Math.cos(z * 0.005);
                if (n < -0.28) return { name: 'Lava Sea', color: '#c2410c' };
                if (n < 0.1) return { name: 'Nether Wastes', color: '#450a0a' };
                if (n < 0.35) return { name: 'Crimson Forest', color: '#881337' };
                return { name: 'Warped Forest', color: '#0f766e' };
            }
            if (dim === 'minecraft:the_end') {
                const dist = Math.sqrt(x*x + z*z);
                if (dist < 140) return { name: 'The End (Main Island)', color: '#fef08a' };
                const ring = Math.sin(dist * 0.015);
                if (dist > 350 && ring > 0.25) return { name: 'End Highlands', color: '#fde047' };
                return { name: 'The Void', color: '#030712' };
            }

            // Overworld Real Biome Simulation
            const elevation = (Math.sin(x * 0.002) + Math.cos(z * 0.002) + Math.sin(x * 0.008 + z * 0.008) * 0.4) / 2.2;
            const moisture = (Math.sin(x * 0.0018 + 12) + Math.cos(z * 0.0018 + 12)) / 2;

            // River system
            const river = Math.abs(Math.sin(x * 0.0035 + Math.cos(z * 0.0035) * 2));
            if (river < 0.038 && elevation > -0.2) return { name: 'River', color: '#2563eb' };

            // Oceans & Coasts
            if (elevation < -0.35) return { name: 'Deep Ocean', color: '#172554' };
            if (elevation < -0.15) return { name: 'Ocean', color: '#1d4ed8' };
            if (elevation < -0.06) return { name: 'Warm Ocean', color: '#0284c7' };
            if (elevation < -0.01) return { name: 'Beach', color: '#eab308' };

            // Mountains
            if (elevation > 0.45) return { name: 'Frozen Peaks', color: '#f8fafc' };
            if (elevation > 0.35) return { name: 'Stony Peaks', color: '#64748b' };
            if (elevation > 0.22) return { name: 'Meadow', color: '#4ade80' };

            // Terrestrial biomes
            if (moisture < -0.3) return { name: 'Desert', color: '#d97706' };
            if (moisture < -0.1) return { name: 'Savanna', color: '#ca8a04' };
            if (moisture < 0.15) return { name: 'Plains', color: '#65a30d' };
            if (moisture < 0.35) return { name: 'Forest', color: '#15803d' };
            if (moisture < 0.5) return { name: 'Dark Forest', color: '#064e3b' };
            return { name: 'Jungle', color: '#047857' };
        }

        // Pre-render 256x256 world block tiles to Offscreen Canvas (60 FPS Caching)
        function getOrCreateTile(dim, tx, tz) {
            const key = `${dim}_${tx}_${tz}`;
            if (tileCache.has(key)) return tileCache.get(key);

            const canvas = document.createElement('canvas');
            canvas.width = TILE_PIXELS;
            canvas.height = TILE_PIXELS;
            const ctx = canvas.getContext('2d');

            const startWX = tx * TILE_SIZE;
            const startWZ = tz * TILE_SIZE;
            const step = TILE_SIZE / TILE_PIXELS; // 4 blocks per pixel

            for (let px = 0; px < TILE_PIXELS; px++) {
                for (let pz = 0; pz < TILE_PIXELS; pz++) {
                    const wx = startWX + px * step;
                    const wz = startWZ + pz * step;
                    const b = getBiomeInfo(wx, wz, dim);
                    ctx.fillStyle = b.color;
                    ctx.fillRect(px, pz, 1, 1);
                }
            }

            if (tileCache.size > 250) {
                const firstKey = tileCache.keys().next().value;
                tileCache.delete(firstKey);
            }
            tileCache.set(key, canvas);
            return canvas;
        }

        function clearTileCacheAndRender() {
            tileCache.clear();
            requestMapRender();
        }

        function requestMapRender() {
            if (!renderScheduled) {
                renderScheduled = true;
                requestAnimationFrame(() => {
                    renderScheduled = false;
                    renderClaimsMap();
                });
            }
        }

        if (document.cookie.includes('admin_token=')) {
            showDashboard();
        }

        function switchModTab(tab) {
            currentActiveMod = tab;
            document.getElementById('tabBtnLogin').classList.toggle('active', tab === 'login');
            document.getElementById('tabBtnClaim').classList.toggle('active', tab === 'claim');
            document.getElementById('loginModSection').classList.toggle('hidden', tab !== 'login');
            document.getElementById('claimModSection').classList.toggle('hidden', tab !== 'claim');

            if (tab === 'claim') {
                fetchClaims();
                setTimeout(initClaimsMap, 60);
                setTimeout(requestMapRender, 140);
            }
        }

        function refreshCurrentTab() {
            if (currentActiveMod === 'login') fetchPlayers();
            else fetchClaims();
        }

        async function login() {
            const pass = document.getElementById('adminPassword').value;
            const res = await fetch('/api/login', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({password: pass})
            });
            const data = await res.json();
            if (data.success) {
                showDashboard();
            } else {
                const err = document.getElementById('errorMsg');
                err.textContent = data.error || 'Invalid password';
                err.style.display = 'block';
            }
        }

        function logout() {
            document.cookie = 'admin_token=; Max-Age=0; path=/;';
            location.reload();
        }

        function showDashboard() {
            document.getElementById('loginView').classList.add('hidden');
            document.getElementById('dashboardView').classList.remove('hidden');
            fetchPlayers();
            fetchClaims();
        }

        // ================= LOGIN MOD LOGIC =================
        async function fetchPlayers() {
            const res = await fetch('/api/players');
            if (res.status === 401) { location.reload(); return; }
            gPlayers = await res.json();
            document.getElementById('stat-total').textContent = gPlayers.length;
            document.getElementById('stat-online').textContent = gPlayers.filter(p => p.isOnline).length;
            document.getElementById('stat-banned').textContent = gPlayers.filter(p => p.isBanned).length;
            renderPlayersGrid(gPlayers);
            if (currentActiveMod === 'claim') requestMapRender();
        }

        function renderPlayersGrid(players) {
            const grid = document.getElementById('playersGrid');
            grid.innerHTML = '';
            players.forEach(p => {
                const card = document.createElement('div');
                card.className = 'player-card';
                card.innerHTML = `
                    <div class="player-card-head">
                        <div class="player-card-name-box">
                            <img src="https://minotar.net/helm/${p.name}/64.png" class="player-skin" onerror="this.src='https://minotar.net/helm/steve/64.png'">
                            <div>
                                <div class="player-name">${escapeHtml(p.name)} <span class="status-dot ${p.isOnline ? 'online' : 'offline'}"></span></div>
                                <div style="font-size:11px; color:#64748b;">${p.uuid.substring(0,18)}...</div>
                            </div>
                        </div>
                        ${p.isBanned ? '<span style="color:#ef4444; font-weight:700; font-size:12px;">⛔ BANNED</span>' : ''}
                    </div>
                    <div class="player-meta">
                        <div>🔑 <b>Password:</b> <span style="color:#fde047;">${escapeHtml(p.password)}</span></div>
                        <div>📍 <b>Coords:</b> ${escapeHtml(p.coordinates || 'N/A')}</div>
                        <div style="font-size:11px;">🕒 <b>Last Login:</b> ${escapeHtml(p.lastLogin || 'N/A')}</div>
                    </div>
                    <div class="player-actions">
                        <button class="btn-ctrl btn-full" onclick="openPwdModal('${p.uuid}', '${escapeHtml(p.name)}')">Change Password</button>
                        <button class="btn-ctrl" onclick="viewInventory('${p.uuid}', '${escapeHtml(p.name)}')">🎒 View Inv</button>
                        <button class="btn-ctrl btn-warning" onclick="doPlayerAction('${p.uuid}', '${p.isMuted ? 'unmute' : 'mute'}')">${p.isMuted ? 'Unmute' : 'Mute'}</button>
                        <button class="btn-ctrl btn-warning" onclick="doPlayerAction('${p.uuid}', 'kick')">Kick</button>
                        <button class="btn-ctrl btn-danger" onclick="doPlayerAction('${p.uuid}', '${p.isBanned ? 'unban' : 'ban'}')">${p.isBanned ? 'Unban' : 'Ban'}</button>
                        <button class="btn-ctrl btn-danger btn-full" onclick="deletePlayer('${p.uuid}', '${escapeHtml(p.name)}')">🗑 Delete Data</button>
                    </div>
                `;
                grid.appendChild(card);
            });
        }

        function filterPlayers() {
            const q = document.getElementById('playerSearch').value.toLowerCase();
            renderPlayersGrid(gPlayers.filter(p => p.name.toLowerCase().includes(q) || p.uuid.includes(q)));
        }

        async function doPlayerAction(uuid, action) {
            let payload = { uuid: uuid, action: action };
            if (action === 'ban') {
                let d = prompt("Ban duration in days (0 for permanent):", "0");
                if (d === null) return;
                payload.duration = parseInt(d) || 0;
            }
            const res = await fetch('/api/action', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify(payload)
            });
            const data = await res.json();
            if (data.success) fetchPlayers();
        }

        async function deletePlayer(uuid, name) {
            if (!confirm(`Delete all data for ${name}?`)) return;
            const res = await fetch('/api/delete', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({uuid: uuid})
            });
            const data = await res.json();
            if (data.success) fetchPlayers();
        }

        async function sendChat() {
            const msg = document.getElementById('chatMsg').value;
            if (!msg) return;
            const res = await fetch('/api/broadcast', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({message: msg})
            });
            const data = await res.json();
            if (data.success) document.getElementById('chatMsg').value = '';
        }

        // ================= CLAIM MOD LOGIC =================
        async function fetchClaims() {
            try {
                const res = await fetch('/api/claims');
                gClaims = await res.json();
                document.getElementById('stat-claims-count').textContent = gClaims.length;
                let blocks = 0;
                let owners = new Set();
                gClaims.forEach(c => {
                    const w = (c.maxX - c.minX) + 1;
                    const l = (c.maxZ - c.minZ) + 1;
                    blocks += (c.totalArea || (w * l) || 0);
                    if (c.ownerName) owners.add(c.ownerName);
                });
                document.getElementById('stat-claims-blocks').textContent = blocks.toLocaleString() + 'b';
                document.getElementById('stat-claims-owners').textContent = owners.size;
                renderClaimsTable(gClaims);
                requestMapRender();
            } catch(e) {
                console.error("Claims fetch error:", e);
            }
        }

        function renderClaimsTable(claims) {
            const tbody = document.getElementById('claimsTableBody');
            if (!claims || !claims.length) {
                tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; color: var(--text-dim); padding: 24px;">No claims registered yet in world.</td></tr>';
                return;
            }
            tbody.innerHTML = claims.map(c => {
                const lvl = c.level || 1;
                const normDim = getNormDim(c.dimension);
                const displayDim = normDim.replace('minecraft:', '').replace('the_', '');
                const w = (c.maxX - c.minX) + 1;
                const l = (c.maxZ - c.minZ) + 1;
                const area = c.totalArea || (w * l);

                const trusted = Object.keys(c.trustedPlayers || {}).map(k => {
                    const name = (c.trustedNames && c.trustedNames[k]) ? c.trustedNames[k] : k.substring(0,6);
                    return `<span style="background:rgba(56,189,248,0.15); padding:2px 6px; border-radius:4px; font-size:11px;">${name} (L${c.trustedPlayers[k]})</span>`;
                }).join(' ') || '<span style="color:var(--text-dim);">None</span>';

                return `
                    <tr>
                        <td><b>#${c.id}</b></td>
                        <td><span style="color:#38bdf8; font-weight:600;">${escapeHtml(c.ownerName)}</span></td>
                        <td style="text-transform:capitalize;">${displayDim}</td>
                        <td style="font-family:monospace;">[${c.minX}, ${c.minZ}] ➜ [${c.maxX}, ${c.maxZ}]</td>
                        <td>${w}x${l} (${area.toLocaleString()}b)</td>
                        <td><span class="badge-level lvl-${lvl}">Level ${lvl}</span></td>
                        <td>${trusted}</td>
                        <td>
                            <button class="btn-ctrl" onclick="locateClaimOnMap(${c.id})">🎯 Locate</button>
                            <button class="btn-ctrl btn-danger" onclick="deleteClaim(${c.id})">🗑</button>
                        </td>
                    </tr>
                `;
            }).join('');
        }

        function filterClaims() {
            const q = document.getElementById('claimsSearch').value.toLowerCase();
            renderClaimsTable(gClaims.filter(c => (c.ownerName && c.ownerName.toLowerCase().includes(q)) || String(c.id).includes(q)));
        }

        async function deleteClaim(id) {
            if (!confirm(`Delete Claim #${id}? This will remove protection in-game immediately.`)) return;
            const res = await fetch('/api/deleteclaim', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({claimId: id})
            });
            const data = await res.json();
            if (data.success) {
                fetchClaims();
            } else {
                alert("Failed to delete claim: " + (data.error || "Unknown error"));
            }
        }

        // ================= 2D MAP ENGINE (60 FPS HARDWARE ACCELERATED) =================
        function initClaimsMap() {
            const wrap = document.getElementById('canvasMapWrap');
            const canvas = document.getElementById('loginWorldMap');
            if (!canvas || !wrap) return;

            const w = wrap.clientWidth || 900;
            const h = wrap.clientHeight || 560;
            canvas.width = w;
            canvas.height = h;

            if (!cMapScale || isNaN(cMapScale) || cMapScale <= 0) cMapScale = 1.0;
            
            // Auto focus on the first claim if available
            if (gClaims && gClaims.length > 0 && (cMapOffX === 0 && cMapOffZ === 0)) {
                const c = gClaims[0];
                const cx = (c.minX + c.maxX) / 2;
                const cz = (c.minZ + c.maxZ) / 2;
                cMapScale = 2.0;
                cMapOffX = (w / 2) - (cx * cMapScale);
                cMapOffZ = (h / 2) - (cz * cMapScale);
            } else if (isNaN(cMapOffX) || isNaN(cMapOffZ) || (cMapOffX === 0 && cMapOffZ === 0)) {
                cMapOffX = w / 2;
                cMapOffZ = h / 2;
            }

            requestMapRender();

            canvas.onmousedown = (e) => {
                cDragging = true;
                cDragStartX = e.clientX - cMapOffX;
                cDragStartY = e.clientY - cMapOffZ;
            };
            window.onmouseup = () => cDragging = false;
            canvas.onmousemove = (e) => {
                const rect = canvas.getBoundingClientRect();
                const mx = e.clientX - rect.left;
                const my = e.clientY - rect.top;
                if (cDragging) {
                    cMapOffX = e.clientX - cDragStartX;
                    cMapOffZ = e.clientY - cDragStartY;
                    requestMapRender();
                }
                const wx = Math.round((mx - cMapOffX) / cMapScale);
                const wz = Math.round((my - cMapOffZ) / cMapScale);
                const selDim = document.getElementById('claimDimSelect') ? document.getElementById('claimDimSelect').value : 'minecraft:overworld';
                const bInfo = getBiomeInfo(wx, wz, selDim);

                document.getElementById('loginMapCoords').textContent = `X: ${wx}, Z: ${wz} | Biome: ${bInfo.name} | Zoom: ${cMapScale.toFixed(2)}x`;

                // Hover detection
                let found = null;
                for (let i = gClaims.length - 1; i >= 0; i--) {
                    const c = gClaims[i];
                    if (getNormDim(c.dimension) !== selDim) continue;
                    if (wx >= c.minX && wx <= c.maxX && wz >= c.minZ && wz <= c.maxZ) {
                        found = c;
                        break;
                    }
                }
                const tt = document.getElementById('mapTooltip');
                if (found) {
                    const fw = (found.maxX - found.minX) + 1;
                    const fl = (found.maxZ - found.minZ) + 1;
                    const fArea = found.totalArea || (fw * fl);
                    tt.style.display = 'block';
                    tt.style.left = mx + 'px';
                    tt.style.top = my + 'px';
                    tt.innerHTML = `
                        <div style="font-weight:700; color:#fff; font-size:13px;">🛡️ Claim #${found.id} (${escapeHtml(found.ownerName)})</div>
                        <div style="color:var(--text-dim); margin-top:2px;">📍 Bounds: [${found.minX}, ${found.minZ}] ➜ [${found.maxX}, ${found.maxZ}]</div>
                        <div style="color:#38bdf8;">📐 Size: ${fw}x${fl} (${fArea.toLocaleString()} blocks)</div>
                        <div style="color:#fde047; font-size:11px; margin-top:2px;">⭐ Level ${found.level || 1} Protected Region</div>
                    `;
                } else {
                    tt.style.display = 'none';
                }
            };
            canvas.onwheel = (e) => {
                e.preventDefault();
                const rect = canvas.getBoundingClientRect();
                const mx = e.clientX - rect.left;
                const my = e.clientY - rect.top;
                const wx = (mx - cMapOffX) / cMapScale;
                const wz = (my - cMapOffZ) / cMapScale;
                const factor = e.deltaY < 0 ? 1.2 : 0.83;
                cMapScale = Math.min(Math.max(0.05, cMapScale * factor), 15.0);
                cMapOffX = mx - wx * cMapScale;
                cMapOffZ = my - wz * cMapScale;
                requestMapRender();
            };
        }

        function zoomMapIn() { cMapScale = Math.min(cMapScale * 1.3, 15.0); requestMapRender(); }
        function zoomMapOut() { cMapScale = Math.max(cMapScale * 0.77, 0.05); requestMapRender(); }
        function resetClaimsMap() {
            const canvas = document.getElementById('loginWorldMap');
            cMapScale = 1.0;
            cMapOffX = (canvas && canvas.width) ? canvas.width / 2 : 450;
            cMapOffZ = (canvas && canvas.height) ? canvas.height / 2 : 280;
            if (gClaims && gClaims.length > 0) {
                const c = gClaims[0];
                const cx = (c.minX + c.maxX) / 2;
                const cz = (c.minZ + c.maxZ) / 2;
                cMapScale = 2.0;
                cMapOffX = ((canvas ? canvas.width : 900) / 2) - (cx * cMapScale);
                cMapOffZ = ((canvas ? canvas.height : 560) / 2) - (cz * cMapScale);
            }
            requestMapRender();
        }

        function locateClaimOnMap(id) {
            const claim = gClaims.find(c => c.id === id);
            if (!claim) return;
            if (claim.dimension && document.getElementById('claimDimSelect')) {
                document.getElementById('claimDimSelect').value = getNormDim(claim.dimension);
            }
            const canvas = document.getElementById('loginWorldMap');
            const w = (canvas && canvas.width) ? canvas.width : 900;
            const h = (canvas && canvas.height) ? canvas.height : 560;
            const cx = (claim.minX + claim.maxX) / 2;
            const cz = (claim.minZ + claim.maxZ) / 2;
            const claimW = (claim.maxX - claim.minX) + 1;
            const claimL = (claim.maxZ - claim.minZ) + 1;

            cMapScale = Math.max(1.5, Math.min(4.0, (w / (Math.max(claimW, claimL) * 3))));
            cMapOffX = (w / 2) - (cx * cMapScale);
            cMapOffZ = (h / 2) - (cz * cMapScale);
            requestMapRender();
            window.scrollTo({ top: document.getElementById('canvasMapWrap').offsetTop - 100, behavior: 'smooth' });
        }

        function renderClaimsMap() {
            const canvas = document.getElementById('loginWorldMap');
            if (!canvas) return;
            const ctx = canvas.getContext('2d');
            if (!ctx) return;

            const w = canvas.width || 900;
            const h = canvas.height || 560;

            if (!cMapScale || isNaN(cMapScale) || cMapScale <= 0) cMapScale = 1.0;
            if (isNaN(cMapOffX)) cMapOffX = w / 2;
            if (isNaN(cMapOffZ)) cMapOffZ = h / 2;

            const selDim = document.getElementById('claimDimSelect') ? document.getElementById('claimDimSelect').value : 'minecraft:overworld';

            // Disable smoothing for authentic crisp pixel art look
            ctx.imageSmoothingEnabled = false;

            // Calculate visible tile range (Only draw visible 256x256 block tiles)
            const minWX = Math.floor((-cMapOffX) / cMapScale);
            const maxWX = Math.ceil((-cMapOffX + w) / cMapScale);
            const minWZ = Math.floor((-cMapOffZ) / cMapScale);
            const maxWZ = Math.ceil((-cMapOffZ + h) / cMapScale);

            const startTileX = Math.floor(minWX / TILE_SIZE);
            const endTileX = Math.ceil(maxWX / TILE_SIZE);
            const startTileZ = Math.floor(minWZ / TILE_SIZE);
            const endTileZ = Math.ceil(maxWZ / TILE_SIZE);

            // 1. Blit cached GPU tiles with drawImage (Sub-millisecond 60 FPS)
            for (let tx = startTileX; tx <= endTileX; tx++) {
                for (let tz = startTileZ; tz <= endTileZ; tz++) {
                    const tileImg = getOrCreateTile(selDim, tx, tz);
                    const screenX = cMapOffX + (tx * TILE_SIZE) * cMapScale;
                    const screenZ = cMapOffZ + (tz * TILE_SIZE) * cMapScale;
                    const screenDim = TILE_SIZE * cMapScale;
                    ctx.drawImage(tileImg, screenX, screenZ, screenDim + 0.5, screenDim + 0.5);
                }
            }

            // 2. Adaptive Grid Lines (Level of Detail - LOD)
            // Ensures lines NEVER bunch together or create moire lag!
            let gridInterval = 100;
            while (gridInterval * cMapScale < 60) {
                gridInterval *= 5; // 100 -> 500 -> 2500 -> 12500
            }

            ctx.strokeStyle = 'rgba(255, 255, 255, 0.15)';
            ctx.lineWidth = 1;
            const gridStartX = Math.floor(minWX / gridInterval) * gridInterval;
            const gridStartZ = Math.floor(minWZ / gridInterval) * gridInterval;

            ctx.beginPath();
            for (let x = gridStartX; x <= maxWX; x += gridInterval) {
                let sx = cMapOffX + x * cMapScale;
                ctx.moveTo(sx, 0); ctx.lineTo(sx, h);
            }
            for (let z = gridStartZ; z <= maxWZ; z += gridInterval) {
                let sy = cMapOffZ + z * cMapScale;
                ctx.moveTo(0, sy); ctx.lineTo(w, sy);
            }
            ctx.stroke();

            // 3. Origin Coordinate Axes (Spawn Marker at 0, 0)
            if (cMapOffX >= -10 && cMapOffX <= w + 10 || cMapOffZ >= -10 && cMapOffZ <= h + 10) {
                ctx.strokeStyle = 'rgba(250, 204, 21, 0.65)';
                ctx.lineWidth = 1.5;
                ctx.beginPath();
                ctx.moveTo(cMapOffX, 0); ctx.lineTo(cMapOffX, h);
                ctx.moveTo(0, cMapOffZ); ctx.lineTo(w, cMapOffZ);
                ctx.stroke();

                // Spawn Icon
                ctx.fillStyle = '#facc15';
                ctx.beginPath();
                ctx.arc(cMapOffX, cMapOffZ, 5, 0, Math.PI * 2);
                ctx.fill();
            }

            // 4. Render Claim Polygons
            gClaims.forEach(c => {
                if (getNormDim(c.dimension) !== selDim) return;
                let x = cMapOffX + c.minX * cMapScale;
                let y = cMapOffZ + c.minZ * cMapScale;
                let rw = ((c.maxX - c.minX) + 1) * cMapScale;
                let rh = ((c.maxZ - c.minZ) + 1) * cMapScale;
                let lvl = c.level || 1;
                let col = LEVEL_COLORS[lvl] || '#38bdf8';

                // Claim fill with glow border
                ctx.fillStyle = col + '55';
                ctx.fillRect(x, y, rw, rh);
                ctx.strokeStyle = col;
                ctx.lineWidth = 2.5;
                ctx.strokeRect(x, y, rw, rh);

                // Label
                if (rw > 35 && rh > 18) {
                    ctx.fillStyle = 'rgba(11, 15, 25, 0.85)';
                    ctx.fillRect(x + 2, y + 2, Math.min(rw - 4, 140), 18);
                    ctx.fillStyle = '#ffffff';
                    ctx.font = 'bold 11px Outfit, sans-serif';
                    ctx.fillText(`🛡️ #${c.id} ${c.ownerName || ''}`, x + 6, y + 15);
                }
            });

            // 5. Render Online Players on Map Radar
            gPlayers.filter(p => p.isOnline && p.x !== undefined).forEach(p => {
                if (getNormDim(p.dimension) !== selDim) return;
                let px = cMapOffX + p.x * cMapScale;
                let pz = cMapOffZ + p.z * cMapScale;

                // Player radar point
                ctx.fillStyle = '#22c55e';
                ctx.beginPath();
                ctx.arc(px, pz, 6, 0, Math.PI * 2);
                ctx.fill();
                ctx.strokeStyle = '#ffffff';
                ctx.lineWidth = 2;
                ctx.stroke();

                // Player name tag
                ctx.fillStyle = 'rgba(0, 0, 0, 0.85)';
                ctx.fillRect(px - 20, pz - 22, 40, 14);
                ctx.fillStyle = '#22c55e';
                ctx.font = 'bold 10px Outfit, sans-serif';
                ctx.textAlign = 'center';
                ctx.fillText(p.name, px, pz - 12);
                ctx.textAlign = 'left';
            });
        }

        // Modals
        function openPwdModal(uuid, name) {
            document.getElementById('pwdTargetUuid').value = uuid;
            document.getElementById('pwdTargetName').textContent = name;
            document.getElementById('newPwdInput').value = '';
            document.getElementById('pwdModal').classList.remove('hidden');
        }

        async function submitNewPassword() {
            const uuid = document.getElementById('pwdTargetUuid').value;
            const pwd = document.getElementById('newPwdInput').value;
            if (!pwd) return;
            const res = await fetch('/api/changepassword', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({uuid: uuid, password: pwd})
            });
            const d = await res.json();
            if (d.success) {
                closeModal('pwdModal');
                fetchPlayers();
            }
        }

        async function viewInventory(uuid, name) {
            document.getElementById('invTargetName').textContent = name;
            const grid = document.getElementById('invGrid');
            grid.innerHTML = '';
            for (let i = 0; i < 36; i++) grid.innerHTML += `<div class="inv-slot" id="slot-${i}"></div>`;
            const res = await fetch('/api/inventory?uuid=' + uuid);
            const items = await res.json();
            items.forEach(item => {
                if (item.slot < 36) {
                    const slot = document.getElementById('slot-' + item.slot);
                    slot.title = item.name;
                    slot.innerHTML = `
                        <div style="font-size: 8px; text-align:center; padding:2px; word-break:break-all;">${escapeHtml(item.name.substring(0, 10))}</div>
                        <div class="inv-count">${item.count > 1 ? item.count : ''}</div>
                    `;
                }
            });
            document.getElementById('invModal').classList.remove('hidden');
        }

        function closeModal(id) {
            document.getElementById(id).classList.add('hidden');
        }

        function escapeHtml(str) {
            if (!str) return '';
            return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
        }
    </script>
</body>
</html>
""";
    }
}
