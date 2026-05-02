package com.southside.dynamicisland.system;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SystemStatusProvider {

    private static final long REFRESH_INTERVAL_MS = 30_000L;
    private static final Pattern BATTERY_PATTERN = Pattern.compile("(\\d{1,3})");

    private static long lastBatteryRefresh = 0L;
    private static String cachedBatteryText = "--%";

    private SystemStatusProvider() {
    }

    public static String getBatteryText() {
        long now = System.currentTimeMillis();
        if (now - lastBatteryRefresh < REFRESH_INTERVAL_MS) {
            return cachedBatteryText;
        }

        lastBatteryRefresh = now;
        cachedBatteryText = queryBatteryPercentage();
        return cachedBatteryText;
    }

    private static String queryBatteryPercentage() {
        String osName = System.getProperty("os.name", "").toLowerCase();

        if (osName.contains("win")) {
            String result = runCommand(
                    "powershell",
                    "-NoProfile",
                    "-Command",
                    "$b=Get-CimInstance Win32_Battery | Select-Object -First 1 -ExpandProperty EstimatedChargeRemaining; if ($null -ne $b) { [string]$b }"
            );
            String normalized = sanitizeBatteryValue(result);
            if (!normalized.equals("--%")) {
                return normalized;
            }
        }

        return "--%";
    }

    private static String sanitizeBatteryValue(String value) {
        if (value == null || value.isBlank()) {
            return "--%";
        }

        Matcher matcher = BATTERY_PATTERN.matcher(value);
        while (matcher.find()) {
            int batteryValue;
            try {
                batteryValue = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                continue;
            }

            if (batteryValue >= 0 && batteryValue <= 100) {
                return batteryValue + "%";
            }
        }

        return "--%";
    }

    private static String runCommand(String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
                process.waitFor();
                return output.toString().trim();
            }
        } catch (IOException | InterruptedException ignored) {
            if (ignored instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return "";
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
