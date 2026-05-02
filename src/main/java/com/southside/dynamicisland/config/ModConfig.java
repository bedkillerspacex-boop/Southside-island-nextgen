package com.southside.dynamicisland.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.awt.Color;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "dynamic-island.json");

    public int posX = -1;
    public int posY = 20;
    public double relativeX = -1.0;
    public double relativeY = 0.05;
    public double scale = 1.0;
    public boolean enabled = true;
    public boolean customTablistEnabled = true;
    public boolean killNotificationEnabled = true;
    public boolean showServerAddress = true;
    public boolean showLatency = true;
    public boolean showRoleId = true;
    public boolean showPlayTime = true;
    public boolean showCooldownBar = true;
    public boolean smtcEnabled = true;
    public boolean smtcArtistEnabled = true;
    public boolean smtcCustomApiEnabled = false;
    public boolean smtcLocalApiEnabled = false;
    public boolean smtcBuiltinApiEnabled = true;
    public boolean smtcAutoOffset = false;
    public String smtcCustomApiUrl = "https://api.lrc.cx/lyrics?title=%title%&artist=%artist%";
    public int smtcLyricOffset = 0;
    public int smtcLyricAdvance = 0;
    public float smtcX = 0.50f;
    public float smtcY = 0.85f;
    public float smtcScale = 1.0f;
    public float smtcLyricHue = 264.0f;
    public float smtcLyricSaturation = 0.30f;
    public float smtcLyricBrightness = 1.0f;
    public int smtcMaxLyricWidth = 200;
    public int smtcMinLyricWidth = 100;
    public int smtcLyricLines = 3;
    public float killNotifX = 0.98f;
    public float killNotifY = 0.08f;
    public float killNotifFontSize = 12.0f;
    public int killNotifDuration = 2500;
    public float backgroundHue = 233.0f;
    public float backgroundSaturation = 0.0f;
    public float backgroundBrightness = 0.69f;
    public float fontHue = 210.0f;
    public float fontSaturation = 0.02f;
    public float fontBrightness = 1.0f;
    public float fontScale = 1.0f;
    public float tabBackgroundHue = 0.0f;
    public float tabBackgroundSaturation = 0.0f;
    public float tabBackgroundBrightness = 1.0f;
    public float tabFontHue = 255.0f;
    public float tabFontSaturation = 0.13f;
    public float tabFontBrightness = 0.12f;
    public int fontStyle = 0;
    public int acrylicOpacity = 195;
    public int backgroundRed = 242;
    public int backgroundGreen = 246;
    public int backgroundBlue = 250;

    private static ModConfig INSTANCE;

    public static ModConfig get() {
        if (INSTANCE == null) {
            INSTANCE = new ModConfig();
        }
        return INSTANCE;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, ModConfig.class);
                if (INSTANCE == null) {
                    INSTANCE = new ModConfig();
                }
                INSTANCE.migrateLegacyColor();
                INSTANCE.migrateLegacyFont();
            } catch (IOException e) {
                System.err.println("[Dynamic Island] Failed to load config: " + e.getMessage());
                INSTANCE = new ModConfig();
            }
        } else {
            INSTANCE = new ModConfig();
            save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(get(), writer);
        } catch (IOException e) {
            System.err.println("[Dynamic Island] Failed to save config: " + e.getMessage());
        }
    }

    private void migrateLegacyColor() {
        if (backgroundHue == 0.0f && backgroundSaturation == 0.0f && backgroundBrightness == 0.0f) {
            float[] hsb = Color.RGBtoHSB(backgroundRed, backgroundGreen, backgroundBlue, null);
            backgroundHue = hsb[0] * 360.0f;
            backgroundSaturation = hsb[1];
            backgroundBrightness = hsb[2];
        }
    }

    private void migrateLegacyFont() {
        if (fontScale <= 0.0f) {
            fontScale = 1.0f;
        }
        if (fontHue == 0.0f && fontSaturation == 0.0f && fontBrightness == 0.0f) {
            fontHue = 210.0f;
            fontSaturation = 0.02f;
            fontBrightness = 1.0f;
        }
        if (fontStyle < 0 || fontStyle > 2) {
            fontStyle = 0;
        }
        if (acrylicOpacity <= 0 || acrylicOpacity > 255) {
            acrylicOpacity = 195;
        }
    }
}
