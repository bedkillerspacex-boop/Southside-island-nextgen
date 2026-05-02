package com.southside.dynamicisland.system;

import com.southside.dynamicisland.config.ModConfig;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LyricAutoOffset {
    private static long lastPingMs = 0;
    private static long smoothedPingMs = -1;
    private static long currentOffset = 0;
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static boolean schedulerStarted = false;
    
    private static final float EMA_ALPHA = 0.3f; // Smoothing factor (30% new, 70% old)

    public static long getOffset() {
        if (ModConfig.get().smtcAutoOffset) {
            return currentOffset;
        }
        return ModConfig.get().smtcLyricOffset;
    }

    public static void startAutoOffsetScheduler() {
        if (!schedulerStarted) {
            // Initial update
            updateAutoOffset();
            // Schedule periodic updates every 15 seconds
            scheduler.scheduleAtFixedRate(LyricAutoOffset::updateAutoOffset, 15, 15, TimeUnit.SECONDS);
            schedulerStarted = true;
        }
    }

    public static void stopAutoOffsetScheduler() {
        if (schedulerStarted) {
            scheduler.shutdownNow();
            schedulerStarted = false;
        }
    }

    public static void updateAutoOffset() {
        if (!ModConfig.get().smtcAutoOffset) {
            currentOffset = ModConfig.get().smtcLyricOffset; // Reset if auto-offset is disabled
            return;
        }

        String apiUrl = ModConfig.get().smtcCustomApiUrl;
        if (ModConfig.get().smtcBuiltinApiEnabled) {
            apiUrl = "https://api.lrc.cx/lyrics";
        } else if (ModConfig.get().smtcLocalApiEnabled) {
            apiUrl = "http://127.0.0.1:28883/lyrics";
        }

        final String finalUrl = apiUrl;
        CompletableFuture.runAsync(() -> {
            try {
                long start = System.currentTimeMillis();
                URL url = new URL(finalUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                conn.connect();
                conn.getResponseCode();
                long end = System.currentTimeMillis();
                
                long newPing = end - start;
                lastPingMs = newPing;

                // Optimization: Exponential Moving Average (EMA) to smooth out network jitter
                if (smoothedPingMs < 0) {
                    smoothedPingMs = newPing; // Initial value
                } else {
                    // Ignore extreme outliers (spikes > 2s or 10x current smoothed) to maintain stability
                    if (newPing < 2000 && (smoothedPingMs == 0 || newPing < (smoothedPingMs * 10))) {
                        smoothedPingMs = (long) (newPing * EMA_ALPHA + smoothedPingMs * (1.0f - EMA_ALPHA));
                    }
                }
                
                // Use half-RTT as a heuristic for one-way delay compensation
                currentOffset = (smoothedPingMs / 2) + ModConfig.get().smtcLyricOffset;
            } catch (Exception ignored) {
                // If ping fails, keep last known good offset for stability
            }
        });
    }

    public static long getLastPing() {
        return lastPingMs;
    }
}
