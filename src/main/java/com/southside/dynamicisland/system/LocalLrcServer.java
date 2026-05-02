package com.southside.dynamicisland.system;

import com.southside.dynamicisland.config.ModConfig;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class LocalLrcServer {

    private static HttpServer server;
    private static final int PORT = 28883;

    public static void start() {
        if (server != null) return;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            server.createContext("/lyrics", new LyricsHandler());
            server.createContext("/jsonapi", new LyricsHandler()); // Compatibility
            server.setExecutor(Executors.newFixedThreadPool(2));
            server.start();
            System.out.println("[Dynamic Island] Builtin LrcApi Server started on port " + PORT);
        } catch (IOException e) {
            System.err.println("[Dynamic Island] Failed to start builtin LrcApi Server: " + e.getMessage());
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    static class LyricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
            String title = params.getOrDefault("title", "");
            String artist = params.getOrDefault("artist", "");

            if (title.isEmpty()) {
                sendResponse(exchange, 400, "Missing title parameter");
                return;
            }

            // Perform internal search directly, Kugou as fallback
            String lyrics = LyricsFetcher.searchBuiltinSync(title, artist);
            if (lyrics == null || lyrics.isEmpty()) {
                lyrics = LyricsFetcher.searchKugouSync(title, artist);
            }

            if (lyrics != null && !lyrics.isEmpty()) {
                sendResponse(exchange, 200, lyrics);
            } else {
                sendResponse(exchange, 404, "Lyrics not found");
            }
        }
    }

    private static Map<String, String> queryToMap(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null) return result;
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], URLDecoder.decode(entry[1], StandardCharsets.UTF_8));
            } else {
                result.put(entry[0], "");
            }
        }
        return result;
    }

    private static void sendResponse(HttpExchange exchange, int code, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
