package com.itsnotjasper.dynmap.preview;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.itsnotjasper.dynmapdraw.config.DynmapDrawConfig;
import com.itsnotjasper.dynmap.DynmapServices;
import com.itsnotjasper.dynmap.export.DynmapExporter;
import com.itsnotjasper.dynmap.model.LineDraft;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class DynmapBrowserPreview {
    private static final int PREVIEW_PORT = 17755;
    private static final long SERVER_TTL_SECONDS = 120;
    private static final Gson GSON = new Gson();

    private static HttpServer server;
    private static ScheduledExecutorService shutdownExecutor;
    private static final AtomicReference<String> payload = new AtomicReference<>("");

    private DynmapBrowserPreview() {
    }

    public static boolean launchFromGame() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || DynmapServices.holder() == null) {
            return false;
        }

        String author = client.player.getGameProfile().name();
        var holder = DynmapServices.holder();
        return launch(DynmapPreviewCollector.collect(author, holder.session(), holder.drafts()));
    }

    public static boolean launch(List<LineDraft> drafts) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return false;
        }

        List<LineDraft> previewable = drafts.stream()
                .filter(draft -> draft.line != null
                        && draft.line.corners != null
                        && draft.line.corners.size() >= 2)
                .toList();
        if (previewable.isEmpty()) {
            client.player.displayClientMessage(
                    Component.literal("Nothing to preview — add at least 2 corners or select a saved draft."),
                    false
            );
            return false;
        }

        String json = DynmapExporter.toMarkerJson(previewable);
        if (!startServer(json)) {
            client.player.displayClientMessage(Component.literal("Could not start local preview server."), false);
            return false;
        }

        String url = buildPreviewUrl();
        client.execute(() -> client.execute(() -> openBrowserOrNotify(client, url)));
        return true;
    }

    private static String buildPreviewUrl() {
        return "http://127.0.0.1:" + PREVIEW_PORT + "/preview";
    }

    private static String normalizeDynmapBaseUrl() {
        String baseUrl = DynmapDrawConfig.get().dynmapPreviewUrl;
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://dynmap.minecartrapidtransit.net/main/";
        }
        baseUrl = baseUrl.trim();
        int hashIndex = baseUrl.indexOf('#');
        if (hashIndex >= 0) {
            baseUrl = baseUrl.substring(0, hashIndex);
        }
        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }
        return baseUrl;
    }

    private static void openBrowserOrNotify(Minecraft client, String url) {
        if (openBrowser(url)) {
            if (client.player != null) {
                client.player.displayClientMessage(
                        Component.literal("Opening map preview in browser."),
                        false
                );
            }
        } else if (client.player != null) {
            client.player.displayClientMessage(Component.literal("Preview ready. Open: " + url), false);
        }
    }

    private static boolean startServer(String json) {
        stopServer();
        payload.set(json);
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PREVIEW_PORT), 0);
            server.createContext("/preview.json", DynmapBrowserPreview::handlePreview);
            server.createContext("/preview/config.json", DynmapBrowserPreview::handlePreviewConfig);
            server.createContext("/preview/preview.js", exchange -> handleStaticResource(exchange, "/dynmap-preview/preview.js", "application/javascript; charset=utf-8"));
            server.createContext("/preview", exchange -> handleStaticResource(exchange, "/dynmap-preview/index.html", "text/html; charset=utf-8"));
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();

            shutdownExecutor = Executors.newSingleThreadScheduledExecutor();
            shutdownExecutor.schedule(DynmapBrowserPreview::stopServer, SERVER_TTL_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (IOException e) {
            stopServer();
            return false;
        }
    }

    private static void handlePreviewConfig(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            addCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        Map<String, String> config = new LinkedHashMap<>();
        config.put("dynmapBaseUrl", normalizeDynmapBaseUrl());
        writeJson(exchange, GSON.toJson(config));
    }

    private static void handlePreview(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            addCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        byte[] body = payload.get().getBytes(StandardCharsets.UTF_8);
        addCorsHeaders(exchange);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static void handleStaticResource(HttpExchange exchange, String resourcePath, String contentType) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            addCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        byte[] body = readResource(resourcePath);
        if (body == null) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }

        addCorsHeaders(exchange);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static void writeJson(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        addCorsHeaders(exchange);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
    }

    private static byte[] readResource(String resourcePath) {
        try (InputStream in = DynmapBrowserPreview.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static void stopServer() {
        if (shutdownExecutor != null) {
            shutdownExecutor.shutdownNow();
            shutdownExecutor = null;
        }
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private static boolean openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(URI.create(url));
                    return true;
                }
            }
        } catch (Exception ignored) {
        }

        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
                return true;
            }
            if (os.contains("mac")) {
                Runtime.getRuntime().exec(new String[]{"open", url});
                return true;
            }
            Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}
