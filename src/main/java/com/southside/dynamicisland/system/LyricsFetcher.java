package com.southside.dynamicisland.system;

import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import com.southside.dynamicisland.config.ModConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;

public class LyricsFetcher {

    private static final Pattern LRC_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)");

    private static volatile String currentTitle = "";
    private static volatile TreeMap<Long, String> currentLyrics = new TreeMap<>();
    private static volatile boolean fetching = false;
    private static volatile String fetchingStatus = "";

    public static String getFetchingStatus() { return fetching ? fetchingStatus : ""; }

    public static void requestLyrics(String title, String artist) {
        String key = artist.isEmpty() ? title : title + "|" + artist;
        if (title == null || title.isEmpty() || key.equals(currentTitle)) {
            return;
        }
        currentTitle = key;
        currentLyrics = new TreeMap<>();
        fetching = true;

        CompletableFuture.runAsync(() -> {
            try {
                // Priority 1: Local External API (like LrcApi python)
                if (ModConfig.get().smtcLocalApiEnabled) {
                    if (fetchFromUrl("http://127.0.0.1:28883/lyrics?title=" + URLEncoder.encode(title, "UTF-8") + (artist.isEmpty() ? "" : "&artist=" + URLEncoder.encode(artist, "UTF-8")), key, title)) return;
                }

                // Priority 2: Custom User API
                if (ModConfig.get().smtcCustomApiEnabled && !ModConfig.get().smtcCustomApiUrl.isEmpty()) {
                    String custom = ModConfig.get().smtcCustomApiUrl;
                    custom = custom.replace("%title%", URLEncoder.encode(title, "UTF-8"));
                    custom = custom.replace("%artist%", URLEncoder.encode(artist, "UTF-8"));
                    if (fetchFromUrl(custom, key, title)) return;
                }

                // Priority 3: Builtin "api.lrc.cx" (if not disabled)
                if (fetchFromUrl("https://api.lrc.cx/lyrics?title=" + URLEncoder.encode(title, "UTF-8") + (artist.isEmpty() ? "" : "&artist=" + URLEncoder.encode(artist, "UTF-8")), key, title)) return;

                // Priority 4: Direct Builtin Search Engine (Netease/Internal)
                if (ModConfig.get().smtcBuiltinApiEnabled) {
                    fetchBuiltinLyrics(title, artist, key);
                    if (!currentLyrics.isEmpty()) return;
                }
                // Priority 5: Kugou
                fetchKugouLyrics(title, artist, key);
            } catch (Exception e) {
                if (ModConfig.get().smtcBuiltinApiEnabled) {
                    fetchBuiltinLyrics(title, artist, key);
                }
                if (currentLyrics.isEmpty()) {
                    fetchKugouLyrics(title, artist, key);
                }
            } finally {
                fetching = false;
            }
        });
    }

    private static boolean fetchFromUrl(String urlStr, String key, String title) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (conn.getResponseCode() == 200) {
                parseLrcStream(conn.getInputStream(), key, title);
                return !currentLyrics.isEmpty();
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static void parseLrcStream(java.io.InputStream is, String key, String title) throws Exception {
        TreeMap<Long, String> parsed = new TreeMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher m = LRC_PATTERN.matcher(line);
                if (m.find()) {
                    long min = Long.parseLong(m.group(1));
                    long sec = Long.parseLong(m.group(2));
                    String msStr = m.group(3);
                    if (msStr.length() == 2) msStr += "0";
                    long ms = Long.parseLong(msStr);
                    long time = min * 60000 + sec * 1000 + ms;
                    String text = m.group(4).trim();
                    if (!text.isEmpty()) {
                        parsed.put(time, text);
                    }
                }
            }
        }
        if (!parsed.isEmpty() && key.equals(currentTitle)) {
            currentLyrics = parsed;
            System.out.println("[Dynamic Island] Lyrics loaded for: " + title);
        }
    }

    public static String searchBuiltinSync(String title, String artist) {
        // Try Netease EAPI first (Lyricify approach: mimics mobile app, more stable)
        String result = searchNeteaseEapi(title, artist);
        if (result != null) return result;
        // Fall back to old web API
        return searchNeteaseWebApi(title, artist);
    }

    private static final String EAPI_KEY = "e82ckenh8dichen8";

    private static String searchNeteaseEapi(String title, String artist) {
        try {
            String query = title + (artist.isEmpty() ? "" : " " + artist);
            String jsonBody = "{\"s\":\"" + escapeJson(query) + "\",\"type\":1,\"limit\":5,\"offset\":0,\"total\":true}";
            String path = "/api/cloudsearch/pc";
            String params = eapiEncrypt(path, jsonBody);

            HttpURLConnection conn = (HttpURLConnection) new URL("https://interface.music.163.com/eapi/cloudsearch/pc").openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9; PCT-AL10) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36");
            conn.setRequestProperty("Referer", "https://music.163.com");
            conn.getOutputStream().write(("params=" + params).getBytes(StandardCharsets.UTF_8));

            if (conn.getResponseCode() != 200) return null;
            JsonObject json;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                json = JsonParser.parseReader(r).getAsJsonObject();
            }
            if (!json.has("result")) return null;
            JsonArray songs = json.getAsJsonObject("result").getAsJsonArray("songs");
            if (songs == null || songs.size() == 0) return null;
            long id = songs.get(0).getAsJsonObject().get("id").getAsLong();

            String lrcPath = "/api/song/lyric/v1";
            String lrcBody = "{\"id\":" + id + ",\"cp\":false,\"lv\":0,\"kv\":0,\"tv\":-1,\"rv\":false,\"yv\":0,\"ytv\":1,\"yrv\":1}";
            String lrcParams = eapiEncrypt(lrcPath, lrcBody);

            conn = (HttpURLConnection) new URL("https://interface3.music.163.com/eapi/song/lyric/v1").openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9; PCT-AL10) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36");
            conn.setRequestProperty("Referer", "https://music.163.com");
            conn.getOutputStream().write(("params=" + lrcParams).getBytes(StandardCharsets.UTF_8));

            if (conn.getResponseCode() != 200) return null;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                JsonObject lrcJson = JsonParser.parseReader(r).getAsJsonObject();
                if (lrcJson.has("lrc")) {
                    String lyric = lrcJson.getAsJsonObject("lrc").get("lyric").getAsString();
                    if (!lyric.isEmpty()) return lyric;
                }
            }
        } catch (Exception e) {
            System.err.println("[Dynamic Island] Netease EAPI failed: " + e.getMessage());
        }
        return null;
    }

    private static String searchNeteaseWebApi(String title, String artist) {
        try {
            String searchUrl = "https://music.163.com/api/search/get/web?s=" + URLEncoder.encode(title + " " + artist, "UTF-8") + "&type=1&limit=1";
            HttpURLConnection searchConn = (HttpURLConnection) new URL(searchUrl).openConnection();
            searchConn.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (searchConn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(searchConn.getInputStream(), StandardCharsets.UTF_8))) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    if (json.has("result")) {
                        JsonObject result = json.getAsJsonObject("result");
                        if (result.has("songs")) {
                            JsonArray songs = result.getAsJsonArray("songs");
                            if (songs != null && songs.size() > 0) {
                                long id = songs.get(0).getAsJsonObject().get("id").getAsLong();
                                String lrcUrl = "https://music.163.com/api/song/lyric?id=" + id + "&lv=1";
                                HttpURLConnection lrcConn = (HttpURLConnection) new URL(lrcUrl).openConnection();
                                if (lrcConn.getResponseCode() == 200) {
                                    try (BufferedReader lrcReader = new BufferedReader(new InputStreamReader(lrcConn.getInputStream(), StandardCharsets.UTF_8))) {
                                        JsonObject lrcJson = JsonParser.parseReader(lrcReader).getAsJsonObject();
                                        if (lrcJson.has("lrc")) {
                                            return lrcJson.getAsJsonObject("lrc").get("lyric").getAsString();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Dynamic Island] Netease web API failed: " + e.getMessage());
        }
        return null;
    }

    // AES-ECB + MD5 digest encryption matching Lyricify's EapiHelper
    private static String eapiEncrypt(String path, String jsonBody) throws Exception {
        String message = "nobody" + path + "use" + jsonBody + "md5forencrypt";
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hash = md.digest(message.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        String digest = sb.toString().toUpperCase();

        String combined = path + "-36cd479b6b5-" + jsonBody + "-36cd479b6b5-" + digest;
        SecretKeySpec keySpec = new SecretKeySpec(EAPI_KEY.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encrypted = cipher.doFinal(combined.getBytes(StandardCharsets.UTF_8));

        StringBuilder hex = new StringBuilder();
        for (byte b : encrypted) hex.append(String.format("%02X", b));
        return hex.toString();
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void fetchBuiltinLyrics(String title, String artist, String key) {
        String lyrics = searchBuiltinSync(title, artist);
        if (lyrics != null) {
            parseLrcString(lyrics, key, title);
        }
    }

    private static void parseLrcString(String lrc, String key, String title) {
        TreeMap<Long, String> parsed = new TreeMap<>();
        String[] lines = lrc.split("\\n");
        for (String line : lines) {
            Matcher m = LRC_PATTERN.matcher(line);
            if (m.find()) {
                long min = Long.parseLong(m.group(1));
                long sec = Long.parseLong(m.group(2));
                String msStr = m.group(3);
                if (msStr.length() == 2) msStr += "0";
                long ms = Long.parseLong(msStr);
                long time = min * 60000 + sec * 1000 + ms;
                String text = m.group(4).trim();
                if (!text.isEmpty()) {
                    parsed.put(time, text);
                }
            }
        }
        if (!parsed.isEmpty() && key.equals(currentTitle)) {
            currentLyrics = parsed;
            System.out.println("[Dynamic Island] Builtin lyrics loaded for: " + title);
        }
    }

    public static String getCurrentLyric(long timeMs) {
        if (currentLyrics.isEmpty()) return "";
        long offsetTime = timeMs + ModConfig.get().smtcLyricAdvance - ModConfig.get().smtcLyricOffset;
        var entry = currentLyrics.floorEntry(offsetTime);
        return entry != null ? entry.getValue() : "";
    }

    public static String getPrevLyric(long timeMs) {
        if (currentLyrics.isEmpty()) return "";
        long offsetTime = timeMs + ModConfig.get().smtcLyricAdvance - ModConfig.get().smtcLyricOffset;
        Long cur = currentLyrics.floorKey(offsetTime);
        if (cur == null) return "";
        Long prev = currentLyrics.lowerKey(cur);
        return prev != null ? currentLyrics.get(prev) : "";
    }

    public static String getNextLyric(long timeMs) {
        if (currentLyrics.isEmpty()) return "";
        long offsetTime = timeMs + ModConfig.get().smtcLyricAdvance - ModConfig.get().smtcLyricOffset;
        Long cur = currentLyrics.floorKey(offsetTime);
        Long next = cur != null ? currentLyrics.higherKey(cur) : currentLyrics.firstKey();
        return next != null ? currentLyrics.get(next) : "";
    }

    // Kugou 3-step: search song → search lyrics candidates → download LRC
    public static String searchKugouSync(String title, String artist) {
        try {
            String keyword = URLEncoder.encode(title + (artist.isEmpty() ? "" : " " + artist), "UTF-8");

            // Step 1: search song to get file hash
            HttpURLConnection conn = openConn("http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=" + keyword + "&page=1&pagesize=5&showtype=1");
            if (conn.getResponseCode() != 200) return null;
            JsonObject songResult;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                songResult = JsonParser.parseReader(r).getAsJsonObject();
            }
            if (!songResult.has("data")) return null;
            JsonArray info = songResult.getAsJsonObject("data").getAsJsonArray("info");
            if (info == null || info.size() == 0) return null;
            String hash = info.get(0).getAsJsonObject().get("hash").getAsString();
            if (hash.isEmpty()) return null;

            // Step 2: find lyrics candidates using hash
            conn = openConn("https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=" + keyword + "&hash=" + hash);
            if (conn.getResponseCode() != 200) return null;
            JsonObject candidateResult;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                candidateResult = JsonParser.parseReader(r).getAsJsonObject();
            }
            JsonArray candidates = candidateResult.getAsJsonArray("candidates");
            if (candidates == null || candidates.size() == 0) return null;
            JsonObject best = candidates.get(0).getAsJsonObject();
            String id = best.get("id").getAsString();
            String accesskey = best.get("accesskey").getAsString();

            // Step 3: download LRC (content is base64-encoded)
            conn = openConn("https://lyrics.kugou.com/download?ver=1&client=pc&id=" + id + "&accesskey=" + accesskey + "&fmt=lrc&charset=utf8");
            if (conn.getResponseCode() != 200) return null;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                JsonObject lrcJson = JsonParser.parseReader(r).getAsJsonObject();
                if (lrcJson.has("content")) {
                    byte[] decoded = Base64.getDecoder().decode(lrcJson.get("content").getAsString());
                    return new String(decoded, StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            System.err.println("[Dynamic Island] Kugou search failed: " + e.getMessage());
        }
        return null;
    }

    private static void fetchKugouLyrics(String title, String artist, String key) {
        String lyrics = searchKugouSync(title, artist);
        if (lyrics != null) parseLrcString(lyrics, key, title);
    }

    private static HttpURLConnection openConn(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(4000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        return conn;
    }
}