package com.southside.dynamicisland.render;

import com.southside.dynamicisland.config.ModConfig;
import com.southside.dynamicisland.system.LyricAutoOffset;
import com.southside.dynamicisland.system.LyricsFetcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.skia.Canvas;
import org.jetbrains.skia.Rect;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.net.InetSocketAddress;
import java.util.Base64;

public class IslandRenderer {

    private static final int BASE_ISLAND_HEIGHT = 30;
    private static final int BASE_CORNER_RADIUS = 15;

    private int currentX;
    private int currentY;

    private float smoothX;
    private float smoothY;

    private long sessionStartTime = 0;
    private boolean wasOnServer = false;
    private int requiredRoleWidth = 90;
    private int requiredEventWidth = 0;

    private float tabAnimation = 0.0f; // 0.0 = island, 1.0 = tablist
    private float killAnimation = 0.0f;
    private float victoryAnimation = 0.0f;
    private String lastVictim = "";
    private int killCount = 0;
    private long killStartTime = 0;

    // Game start countdown (-1 = not active)
    private int countdownSeconds = -1;
    private long lastCountdownEndTime = 0;

    // Killsay API states
    private long cooldownRemaining = 0;
    private long totalCooldown = 1;
    private long cooldownStartTime = 0;   // wall-clock ms when cooldown began
    private boolean isGufa = false;
    private boolean isDead = false;
    private boolean isVictory = false;
    private float cooldownAnimation = 0.0f;

    private float smoothWidth;
    private float smoothHeight;
    private boolean initialized = false;

    public IslandRenderer() {
        this.smoothWidth = getIslandWidth();
        this.smoothHeight = getIslandHeight();
    }

    public void onKill(String victimName) {
        this.lastVictim = victimName;
        this.killCount++;
        this.killStartTime = System.currentTimeMillis();
    }

    public void onCooldownStart(long cdMs) {
        this.totalCooldown = Math.max(1, cdMs);
        this.cooldownRemaining = cdMs;
        this.cooldownStartTime = System.currentTimeMillis();
    }

    public void onKillDone(String victimName) {
        if (victimName != null && !victimName.isEmpty()) {
            this.lastVictim = victimName;
        }
    }

    public void onDeath() {
        this.isDead = true;
    }

    public void onVictory() {
        this.isVictory = true;
        this.killStartTime = System.currentTimeMillis(); // reuse timer for toast
    }

    public void setCountdownSeconds(int seconds) {
        this.countdownSeconds = seconds;
    }

    public void updateKillsayState(long cd, boolean gufa, boolean victory, boolean dead) {
        // cd from API: use it to re-sync totalCooldown and start a fresh local timer
        // when a new cooldown begins (cd > current local remaining by a significant margin).
        if (cd > 0 && cooldownStartTime == 0) {
            // API reported an active cooldown but we never got onCooldownStart — start now
            this.totalCooldown = Math.max(1, cd);
            this.cooldownStartTime = System.currentTimeMillis();
        }
        // Local elapsed-based remaining (smooth, frame-rate independent)
        if (cooldownStartTime > 0) {
            long elapsed = System.currentTimeMillis() - cooldownStartTime;
            this.cooldownRemaining = Math.max(0, totalCooldown - elapsed);
            if (this.cooldownRemaining == 0) cooldownStartTime = 0;
        } else {
            this.cooldownRemaining = 0;
        }
        this.isGufa = gufa;
        this.isVictory = victory;
        this.isDead = dead;
    }

    // SMTC State
    private String smtcTitle = "";
    private String smtcArtist = "";
    private long smtcPos = 0;
    private long smtcDur = 0;
    private boolean isPlaying = false;
    private float smtcAnimation = 0.0f;
    private float smtcAnimVelocity = 0.0f;   // spring velocity for pop-in
    private long lastSmtcUpdate = 0;
    private long lastLyricTimeMs = 0;

    private int smtcCoverImage = -1;
    private String lastCoverBase64 = "";

    // Lyric transition state
    private String displayedLyric = "";
    private String incomingLyric = "";
    private float lyricTransition = 1.0f;     // 0 = old sliding out, 1 = new fully in
    private float lyricTransVelocity = 0.0f;
    // Smooth height for the SMTC card (spring)
    private float smtcCardHeight = 36.0f;
    private float smtcCardHeightVel = 0.0f;
    // Lyric scroll state
    private float lyricScrollOffset = 0.0f;
    private long lyricScrollStartTime = 0;
    // Title scroll state
    private float titleScrollOffset = 0.0f;
    private long titleScrollStartTime = 0;

    private int pendingDeleteImage = -1;

    public void updateSmtc(String rawInfo) {
        if (rawInfo == null || rawInfo.isEmpty() || rawInfo.startsWith("No media") || rawInfo.startsWith("Paused")) {
            isPlaying = false;
            return;
        }
        String[] parts = rawInfo.split("\\|", -1);
        if (parts.length >= 3) {
            String newTitle = parts[0];
            String newCoverBase64 = parts.length > 3 ? parts[3] : "";

            if (!newTitle.equals(smtcTitle)) {
                smtcTitle = newTitle;
                smtcArtist = "";
                lastLyricTimeMs = 0;
                displayedLyric = "";
                incomingLyric = "";
                lyricTransition = 1.0f;
                lyricScrollOffset = 0.0f;
                lyricScrollStartTime = 0;
                titleScrollOffset = 0.0f;
                titleScrollStartTime = 0;
                isPlaying = true;
                String artist = ModConfig.get().smtcArtistEnabled ? smtcArtist : "";
                LyricsFetcher.requestLyrics(smtcTitle, artist);
            }

            if (!newCoverBase64.equals(lastCoverBase64)) {
                lastCoverBase64 = newCoverBase64;
                if (smtcCoverImage > 0) {
                    pendingDeleteImage = smtcCoverImage;
                }
                smtcCoverImage = -2;
            }

            try {
                long newPos = Long.parseLong(parts[1]);
                if (newPos != smtcPos) {
                    smtcPos = newPos;
                    lastSmtcUpdate = System.currentTimeMillis();
                    isPlaying = true;
                } else if (System.currentTimeMillis() - lastSmtcUpdate > 2500) {
                    isPlaying = false;
                }
                smtcDur = Long.parseLong(parts[2]);
            } catch (NumberFormatException ignored) {}
        } else {
            isPlaying = false;
        }
    }

    public void render(DrawContext context) {
        ModConfig config = ModConfig.get();
        if (!config.enabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getDebugHud().shouldShowDebugHud()) return;

        // Force Minecraft to flush its rendering queue before we hijack the GL state
        try {
            context.draw();
        } catch (Exception ignored) {}

        // Only allow TAB expansion if no screen is open (like vanilla)
        boolean tabPressed = config.customTablistEnabled && client.currentScreen == null && GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_TAB) == GLFW.GLFW_PRESS;
        tabAnimation += (tabPressed ? 1.0f - tabAnimation : 0.0f - tabAnimation) * 0.15f;

        // Victory suppression logic: Don't show victory during countdown or for 10s after it ends
        if (countdownSeconds > 0) {
            lastCountdownEndTime = System.currentTimeMillis();
        }
        boolean isVictorySuppressed = countdownSeconds >= 0 || (System.currentTimeMillis() - lastCountdownEndTime < 10000);

        // Kill / Victory / Death notification toast logic
        // Priority: Victory > Died > Kill
        boolean showToast = config.killNotificationEnabled &&
            ((System.currentTimeMillis() - killStartTime < config.killNotifDuration) || isDead);
        boolean toastIsVictory = showToast && isVictory && !isVictorySuppressed;
        boolean toastIsDead    = showToast && !isVictory && isDead;
        boolean toastIsKill    = showToast && !isVictory && !isDead && (System.currentTimeMillis() - killStartTime < config.killNotifDuration);
        
        float toastTarget = (toastIsVictory || toastIsDead || toastIsKill) ? 1.0f : 0.0f;
        killAnimation += (toastTarget - killAnimation) * 0.15f;
        victoryAnimation += ((toastIsVictory ? 1.0f : 0.0f) - victoryAnimation) * 0.15f;

        // Cooldown animation logic
        boolean showCd = cooldownRemaining > 0;
        cooldownAnimation += (showCd ? 1.0f - cooldownAnimation : 0.0f - cooldownAnimation) * 0.1f;

        updateServerSession(client);

        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();

        org.jetbrains.skia.Canvas canvas = SkijaRenderer.beginFrame();
        try {
            // Handle pending texture deletions (prevent memory leaks)
            if (pendingDeleteImage > 0) {
                SkijaRenderer.deleteImage(pendingDeleteImage);
                pendingDeleteImage = -1;
            }

            // Measure role ID text width inside the frame so island can expand to fit
            String roleId = getRoleId(client);
            float measured = SkijaRenderer.textWidth(
                    config.fontStyle == 2 ? "ui-bold" : "ui",
                    7.0f * config.fontScale, roleId);
            requiredRoleWidth = (int) Math.ceil(measured);

            // (event width measurement removed — victory now uses external toast only)
            requiredEventWidth = 0;

            int baseWidth = getIslandWidth();
            int baseHeight = getIslandHeight();
            
            int targetWidth = baseWidth;
            int targetHeight = baseHeight;
            
            if (tabAnimation > 0.01f) {
                int playerCount = client.getNetworkHandler() != null ? client.getNetworkHandler().getPlayerList().size() : 0;
                int cols = 4;
                int rows = (int) Math.ceil((double) Math.max(1, playerCount) / cols);
                if (rows > 10) rows = 10;

                float sc = (float) config.scale;
                targetWidth  = Math.round(752 * sc);
                targetHeight = Math.max(baseHeight, Math.round((baseHeight + 45 + rows * 36 + 10) * sc));
            } else if (cooldownAnimation > 0.05f || isGufa || isDead) {
                targetHeight = baseHeight + 8;
            }

            smoothWidth += (targetWidth - smoothWidth) * 0.15f;
            smoothHeight += (targetHeight - smoothHeight) * 0.15f;

            // Keep position logic based on the BASE island size
            updatePosition(client, baseWidth, baseHeight);

            // Calculate render X to expand outward from the center
            float renderX = smoothX + (baseWidth - smoothWidth) / 2.0f;
            float renderY = smoothY;
            float radius = getCornerRadius();
            
            // Morph color towards user-defined tablist colors when tablist is open
            int backgroundColor = getBackgroundColor(config);
            int tabBackgroundColor = getTabBackgroundColor(config);
            int currentBackgroundColor = mixColors(backgroundColor, tabBackgroundColor, tabAnimation * 0.8f);
            
            int fontColor = getFontColor(config);
            int tabFontColor = getTabFontColor(config);
            int currentFontColor = mixColors(fontColor, tabFontColor, tabAnimation * 0.9f);

            renderIsland(canvas, client, renderX, renderY, smoothWidth, smoothHeight, radius, currentBackgroundColor, currentFontColor, config, roleId);

            if (tabAnimation > 0.1f) {
                canvas.save();
                canvas.clipRect(org.jetbrains.skia.Rect.makeXYWH(renderX, renderY, smoothWidth, smoothHeight));
                renderTabList(canvas, client, renderX, renderY, smoothWidth, smoothHeight, currentFontColor, tabAnimation);
                canvas.restore();
            }

            if (killAnimation > 0.01f && config.killNotificationEnabled) {
                renderEventToast(canvas, client, config);
            }

            if (config.smtcEnabled) {
                boolean hasMedia = !smtcTitle.isEmpty() && isPlaying;
                
                float targetAnim = hasMedia ? 1.0f : 0.0f;
                float force = (targetAnim - smtcAnimation) * 0.22f;
                smtcAnimVelocity = smtcAnimVelocity * 0.68f + force;
                smtcAnimation = Math.max(0.0f, Math.min(1.0f, smtcAnimation + smtcAnimVelocity));

                if (smtcAnimation > 0.001f) {
                    renderSmtcToast(canvas, client, config);
                }
            }
        } finally {
            SkijaRenderer.endFrame();
        }
    }

    private void renderSmtcToast(org.jetbrains.skia.Canvas canvas, MinecraftClient client, ModConfig config) {
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();

        float s = config.smtcScale;

        // ── Spring constants ─────────────────────────────────────────────────
        final float STIFFNESS = 0.22f;
        final float DAMPING   = 0.68f;

        // ── Time / position ──────────────────────────────────────────────────
        long estimatedPos = smtcPos;
        if (smtcDur > 0 && lastSmtcUpdate > 0) {
            long el = (System.currentTimeMillis() - lastSmtcUpdate) / 1000;
            estimatedPos = Math.min(smtcPos + el, smtcDur);
        }
        long timeMs = smtcDur > 0 && lastSmtcUpdate > 0
                ? smtcPos * 1000L + (System.currentTimeMillis() - lastSmtcUpdate)
                : estimatedPos * 1000L;
        
        // Apply manual and auto offsets
        timeMs += LyricAutoOffset.getOffset();
        timeMs += config.smtcLyricAdvance;
        
        // Jitter protection + Seek support: 
        // Allow time to move backwards only if it's a significant jump (> 2.0s for seek), 
        // otherwise if it's small jitter (< 200ms), lock it. If it's a medium jump,
        // it's likely a sync correction from the player, so allow it (fix drift).
        long delta = timeMs - lastLyricTimeMs;
        if (delta > 0 || delta < -200) {
            lastLyricTimeMs = timeMs;
        } else {
            timeMs = lastLyricTimeMs;
        }

        String currentLyric = LyricsFetcher.getCurrentLyric(timeMs);
        String prevLyric    = LyricsFetcher.getPrevLyric(timeMs);
        String nextLyric    = LyricsFetcher.getNextLyric(timeMs);
        boolean hasLyric    = !currentLyric.isEmpty();
        String fetchStatus = LyricsFetcher.getFetchingStatus();
        boolean isFetching = !fetchStatus.isEmpty();

        // ── Lyric change transition (spring) ─────────────────────────────────
        if (!currentLyric.equals(displayedLyric) && !currentLyric.equals(incomingLyric)) {
            incomingLyric = currentLyric;
            lyricTransition = 0.0f;
            lyricTransVelocity = 0.0f;
        }
        if (lyricTransition < 1.0f) {
            float force = (1.0f - lyricTransition) * STIFFNESS;
            lyricTransVelocity = lyricTransVelocity * DAMPING + force;
            lyricTransition = Math.min(1.0f, lyricTransition + lyricTransVelocity);
            if (lyricTransition >= 0.98f) {
                lyricTransition = 1.0f;
                displayedLyric  = incomingLyric;
            }
        }

        // ── Card height spring ───────────────────────────────────────────────
        // Music row = 44px, lyric section = 56px (3 lines × ~16 + 8 padding)
        float lyricHBase = config.smtcLyricLines == 1 ? 28 : 68;
        float targetH = (hasLyric || isFetching) ? (44 + lyricHBase) * s : 44 * s;
        float hForce = (targetH - smtcCardHeight) * STIFFNESS;
        smtcCardHeightVel = smtcCardHeightVel * DAMPING + hForce;
        smtcCardHeight += smtcCardHeightVel;

        // ── Layout ───────────────────────────────────────────────────────────
        float titleSize = 9.5f * s;
        float timeSize  = 8.5f * s;
        float lyricCur  = 13.5f * s;
        float lyricAdj  = 10.5f * s;

        float tw = Math.max(
            SkijaRenderer.textWidth("ui-bold", titleSize, smtcTitle),
            SkijaRenderer.textWidth("ui",      timeSize,  smtcArtist.isEmpty()
                ? String.format("%d:%02d / %d:%02d", estimatedPos/60, estimatedPos%60, smtcDur/60, smtcDur%60)
                : smtcArtist)
        );
        if (hasLyric) {
            String measure = lyricTransition < 1.0f ? incomingLyric : displayedLyric;
            tw = Math.max(tw, SkijaRenderer.textWidth("ui", lyricCur, measure));
            if (config.smtcLyricLines != 1) {
                if (!prevLyric.isEmpty()) tw = Math.max(tw, SkijaRenderer.textWidth("ui", lyricAdj, prevLyric) * 0.9f);
                if (!nextLyric.isEmpty()) tw = Math.max(tw, SkijaRenderer.textWidth("ui", lyricAdj, nextLyric) * 0.9f);
            }

            // Enforce minimum lyric area width
            tw = Math.max(tw, config.smtcMinLyricWidth * s);
        }
        tw += 38 * s;
        tw = Math.min(tw, config.smtcMaxLyricWidth * s + 38 * s);

        float th  = smtcCardHeight;
        float tr  = 14 * s;
        float cx  = sw * config.smtcX;
        float cy  = sh * config.smtcY;
        float tx  = cx - tw / 2.0f;
        float ty  = cy - th / 2.0f + (1.0f - smtcAnimation) * 18.0f;
        int   a   = (int)(smtcAnimation * 255);

        // ── Lyric accent color from config ───────────────────────────────────
        int accentRgb = java.awt.Color.HSBtoRGB(
            config.smtcLyricHue / 360.0f,
            config.smtcLyricSaturation,
            config.smtcLyricBrightness
        ) & 0xFFFFFF;

        // ── Background card ──────────────────────────────────────────────────
        SkijaRenderer.roundedRect(canvas, tx + 1, ty + 3, tw, th, tr, withAlpha(0x000000, (int)(a * 0.18f)));
        SkijaRenderer.roundedRect(canvas, tx,     ty,     tw, th, tr, withAlpha(0x141218, a));
        SkijaRenderer.roundedRectStroke(canvas, tx + 0.5f, ty + 0.5f, tw - 1, th - 1, tr, 1.0f,
            withAlpha(0xFFFFFF, (int)(a * 0.08f)));

        // ── Music note icon or Cover Art ─────────────────────────────────────
        float iconX = tx + 11 * s;
        float iconCY = ty + 22 * s;
        float iconSize = 18 * s;

        // Handle cover image loading/unloading
        if (pendingDeleteImage > 0) {
            SkijaRenderer.deleteImage(pendingDeleteImage);
            pendingDeleteImage = -1;
        }

        if (smtcCoverImage == -2) {
            if (!lastCoverBase64.isEmpty()) {
                try {
                    byte[] data = Base64.getDecoder().decode(lastCoverBase64);
                    smtcCoverImage = SkijaRenderer.createImage(data);
                } catch (Exception e) {
                    smtcCoverImage = -1;
                }
            } else {
                smtcCoverImage = -1;
            }
        }

        if (smtcCoverImage > 0) {
            SkijaRenderer.imageRect(canvas, iconX, iconCY - 9 * s, iconSize, iconSize, 4 * s, smtcCoverImage, a / 255f);
        } else {
            int iconFg  = withAlpha(accentRgb, a);
            int iconBg  = withAlpha(accentRgb, (int)(a * 0.15f));
            SkijaRenderer.roundedRect(canvas, iconX, iconCY - 9 * s, iconSize, iconSize, 9 * s, iconBg);
            SkijaRenderer.roundedRect(canvas, iconX + 10 * s, iconCY - 7 * s, 2.5f * s, 10 * s, 1.2f * s, iconFg);
            SkijaRenderer.roundedRect(canvas, iconX + 4.5f * s, iconCY + 1.5f * s, 6.5f * s, 5.5f * s, 2.8f * s, iconFg);
        }

        // ── Title & artist/time row ──────────────────────────────────────────
        float textX = tx + 36 * s;
        float maxTitleWidth = tx + tw - 8 * s - textX;
        String timeStr = String.format("%d:%02d / %d:%02d",
            estimatedPos/60, estimatedPos%60, smtcDur/60, smtcDur%60);
        String subtitleStr = smtcArtist.isEmpty() ? timeStr : smtcArtist;

        // Title scroll logic
        float titleWidth = SkijaRenderer.textWidth("ui-bold", titleSize, smtcTitle);
        boolean titleNeedsScroll = titleWidth > maxTitleWidth;

        if (titleNeedsScroll && titleScrollStartTime == 0) {
            titleScrollStartTime = System.currentTimeMillis();
        } else if (!titleNeedsScroll) {
            titleScrollOffset = 0.0f;
            titleScrollStartTime = 0;
        }

        if (titleNeedsScroll) {
            long elapsed = System.currentTimeMillis() - titleScrollStartTime;
            float scrollSpeed = 40.0f;
            float spaceWidth = 8.0f * s;
            float totalWidth = titleWidth + spaceWidth;
            float scrollCycle = (elapsed * scrollSpeed / 1000.0f) % totalWidth;
            titleScrollOffset = -scrollCycle;
        }

        // Clip title area
        canvas.save();
        canvas.clipRect(org.jetbrains.skia.Rect.makeXYWH(textX, ty + 8 * s, maxTitleWidth, 12 * s));

        if (titleNeedsScroll) {
            float spaceWidth = 8.0f * s;
            float repeatOffset = titleWidth + spaceWidth;
            SkijaRenderer.text(canvas, "ui-bold", titleSize, textX + titleScrollOffset, ty + 14 * s, withAlpha(0xECE6F0, a), false, smtcTitle);
            SkijaRenderer.text(canvas, "ui-bold", titleSize, textX + titleScrollOffset + repeatOffset, ty + 14 * s, withAlpha(0xECE6F0, a), false, smtcTitle);
        } else {
            SkijaRenderer.text(canvas, "ui-bold", titleSize, textX, ty + 14 * s, withAlpha(0xECE6F0, a), false, smtcTitle);
        }

        canvas.restore();

        SkijaRenderer.text(canvas, "ui",      timeSize,  textX, ty + 26 * s, withAlpha(0xCAC4D0, (int)(a*0.75f)), false, subtitleStr);

        // ── Progress bar ─────────────────────────────────────────────────────
        if (smtcDur > 0) {
            float prog  = Math.max(0, Math.min(1, (float)estimatedPos / smtcDur));
            float barW  = tw - 44 * s;
            float barH  = 2.5f * s;
            float barX  = textX;
            float barY  = ty + 36 * s;
            SkijaRenderer.roundedRect(canvas, barX, barY, barW,        barH, barH/2, withAlpha(0x49454F, a));
            SkijaRenderer.roundedRect(canvas, barX, barY, barW * prog, barH, barH/2, withAlpha(accentRgb, a));
            // show time only when artist is shown in subtitle
            if (!smtcArtist.isEmpty()) {
                SkijaRenderer.text(canvas, "ui", 7.5f * s, textX + barW + 3 * s, barY + barH / 2,
                    withAlpha(0xCAC4D0, (int)(a * 0.55f)), false, timeStr);
            }
        }

        // ── Lyric section ────────────────────────────────────────────────────
        if ((hasLyric || isFetching) && smtcCardHeight > 45 * s) {
            float lyricSectionY = ty + 44 * s;
            float lyricH        = smtcCardHeight - 44 * s;
            float lyricCX       = tx + tw / 2.0f;

            // Clip lyric area
            canvas.save();
            canvas.clipRect(org.jetbrains.skia.Rect.makeXYWH(tx + 2, lyricSectionY, tw - 4, lyricH));

            // Subtle separator line
            SkijaRenderer.roundedRect(canvas, tx + 16 * s, lyricSectionY + 1, tw - 32 * s, 1, 0.5f,
                withAlpha(0xFFFFFF, (int)(a * 0.08f)));

            // Section progress: how far into this lyric line we are (0–1)
            float sectionFade = lyricTransition < 1.0f ? lyricTransition : 1.0f;
            // ease: smoothstep
            float ease = sectionFade * sectionFade * (3.0f - 2.0f * sectionFade);

            float lineH    = 22 * s;
            float midY     = lyricSectionY + lyricH / 2.0f;

            // Calculate scroll for current lyric
            float maxWidth = config.smtcMaxLyricWidth * s;
            float minWidth = config.smtcMinLyricWidth * s;
            float curLyricWidth = SkijaRenderer.textWidth("ui", lyricCur, displayedLyric);
            float constrainedWidth = Math.max(curLyricWidth, minWidth);
            boolean needsScroll = constrainedWidth > maxWidth;

            if (needsScroll && lyricScrollStartTime == 0) {
                lyricScrollStartTime = System.currentTimeMillis();
            } else if (!needsScroll) {
                lyricScrollOffset = 0.0f;
                lyricScrollStartTime = 0;
            }

            if (needsScroll) {
                long elapsed = System.currentTimeMillis() - lyricScrollStartTime;
                float scrollSpeed = 40.0f;
                float spaceWidth = 8.0f * s;
                float totalWidth = curLyricWidth + spaceWidth;
                float scrollCycle = (elapsed * scrollSpeed / 1000.0f) % totalWidth;
                lyricScrollOffset = -scrollCycle;
            }

            // Previous line (slides up as new line comes in)
            if (config.smtcLyricLines != 1 && !prevLyric.isEmpty()) {
                float slideOff = (1.0f - ease) * 0;
                float prevY    = midY - lineH + slideOff;
                int   prevA    = withAlpha(0xCAC4D0, (int)(a * 0.38f * ease));
                SkijaRenderer.text(canvas, "ui", lyricAdj, lyricCX, prevY, prevA, true, prevLyric);
            }

            // Current line — old fades/slides up, new slides in from below
            String oldLine = displayedLyric.isEmpty() ? incomingLyric : displayedLyric;
            String newLine = incomingLyric;

            if (lyricTransition < 1.0f && !oldLine.equals(newLine)) {
                // outgoing
                float outY = midY - ease * lineH * 0.6f;
                int   outA = withAlpha(accentRgb, (int)(a * (1.0f - ease)));
                SkijaRenderer.text(canvas, "ui", lyricCur, lyricCX + lyricScrollOffset, outY, outA, true, oldLine);
                // incoming
                float inY  = midY + (1.0f - ease) * lineH * 0.8f;
                int   inA  = withAlpha(accentRgb, (int)(a * ease));
                // Glow backdrop
                float bgH = config.smtcLyricLines == 1 ? lineH * 1.1f : lineH * 1.6f;
                float bgY = midY - bgH / 2.0f;
                SkijaRenderer.roundedRect(canvas, tx + 8 * s, bgY, tw - 16 * s, bgH,
                    bgH / 2.0f, withAlpha(accentRgb, (int)(a * 0.07f * ease)));
                SkijaRenderer.text(canvas, "ui", lyricCur, lyricCX + lyricScrollOffset, inY, inA, true, newLine);
            } else {
                // Glow backdrop on current line
                float bgH = config.smtcLyricLines == 1 ? lineH * 1.1f : lineH * 1.6f;
                float bgY = midY - bgH / 2.0f;
                SkijaRenderer.roundedRect(canvas, tx + 8 * s, bgY, tw - 16 * s, bgH,
                    bgH / 2.0f, withAlpha(accentRgb, (int)(a * 0.09f)));
                int curA = withAlpha(accentRgb, a);

                if (needsScroll) {
                    float spaceWidth = 8.0f * s;
                    float repeatOffset = curLyricWidth + spaceWidth;
                    SkijaRenderer.text(canvas, "ui", lyricCur, lyricCX + lyricScrollOffset, midY, curA, true, displayedLyric);
                    SkijaRenderer.text(canvas, "ui", lyricCur, lyricCX + lyricScrollOffset + repeatOffset, midY, curA, true, displayedLyric);
                } else {
                    SkijaRenderer.text(canvas, "ui", lyricCur, lyricCX + lyricScrollOffset, midY, curA, true, displayedLyric);
                }
            }

            // Next line
            if (config.smtcLyricLines != 1 && !nextLyric.isEmpty()) {
                float slideOff = (1.0f - ease) * 0;
                float nextY    = midY + lineH * 1.3f + slideOff;
                int   nextA    = withAlpha(0xCAC4D0, (int)(a * 0.38f * ease));
                SkijaRenderer.text(canvas, "ui", lyricAdj, lyricCX, nextY, nextA, true, nextLyric);
            }

            canvas.restore();
        }
    }

    private void renderEventToast(org.jetbrains.skia.Canvas canvas, MinecraftClient client, ModConfig config) {
        int sw = client.getWindow().getScaledWidth();
        int h = client.getWindow().getScaledHeight();

        // Priority: Victory > Died > Kill
        boolean victory = victoryAnimation > 0.3f;
        boolean dead    = !victory && isDead;
        
        String line1;
        String line2;
        int accentColor;
        int bgTint;
        
        if (victory) {
            line1 = "GAME OVER";
            line2 = "VICTORY!";
            accentColor = 0xFFD700;
            bgTint = 0xFFD700;
        } else if (dead) {
            line1 = "STATUS";
            line2 = "RESPAWNING...";
            accentColor = 0xCAC4D0;
            bgTint      = 0x1C1B1F;
        } else {
            line1 = "击杀";
            line2 = (lastVictim.isEmpty() ? "???" : lastVictim);
            accentColor = 0xFF5252;
            bgTint      = 0xFF5252;
        }

        float fontSize  = config.killNotifFontSize;
        float tw = Math.max(
            SkijaRenderer.textWidth("ui-bold", fontSize, line2),
            SkijaRenderer.textWidth("ui",      fontSize * 0.72f, line1)
        ) + 32;
        float th = fontSize * 3.0f;
        float tr = 10.0f;

        float anchorX = config.killNotifX * sw;
        float anchorY = config.killNotifY * h;

        float slideOffset = (1.0f - killAnimation) * (tw + 20);
        float tx = anchorX - tw - slideOffset;
        float ty = anchorY - th / 2.0f;

        int alpha = (int)(killAnimation * 230);

        // Layered shadow
        SkijaRenderer.roundedRect(canvas, tx + 2, ty + 5, tw, th, tr, withAlpha(0x000000, (int)(killAnimation * 35)));
        SkijaRenderer.roundedRect(canvas, tx + 1, ty + 2, tw, th, tr, withAlpha(0x000000, (int)(killAnimation * 25)));

        // Background: MD3 Surface tinted toward accent
        int bgBase = mixColors(0x1C1B1F, bgTint, dead ? 0.0f : 0.08f);
        SkijaRenderer.roundedRect(canvas, tx, ty, tw, th, tr, withAlpha(bgBase, alpha));
        SkijaRenderer.roundedRectGradient(canvas, tx, ty, tw, th, tr,
            withAlpha(0xFFFFFF, (int)(killAnimation * 18)),
            withAlpha(0x000000, (int)(killAnimation * 8)));

        // Left accent pill
        SkijaRenderer.roundedRect(canvas, tx, ty + th * 0.2f, 3.5f, th * 0.6f, 1.75f, withAlpha(accentColor, alpha));
        SkijaRenderer.roundedRectStroke(canvas, tx + 0.5f, ty + 0.5f, tw - 1, th - 1, tr, 0.75f,
            withAlpha(accentColor, (int)(killAnimation * 55)));
        SkijaRenderer.roundedRectStroke(canvas, tx + 1.0f, ty + 1.0f, tw - 2, th - 2, tr - 0.5f, 0.5f,
            withAlpha(0xFFFFFF, (int)(killAnimation * 22)));

        // Label line
        SkijaRenderer.textSpaced(canvas, "ui", fontSize * 0.72f, 0.8f, tx + 14, ty + th * 0.30f,
            withAlpha(victory ? 0xFFD060 : (dead ? 0xCAC4D0 : 0xFF7070), alpha), false, line1);
        // Value line
        SkijaRenderer.text(canvas, "ui-bold", fontSize, tx + 14, ty + th * 0.68f,
            withAlpha(0xFFFFFF, alpha), false, line2);
    }

    private void renderTabList(org.jetbrains.skia.Canvas canvas, MinecraftClient client, float x, float y, float width, float height, int fontColor, float alpha) {
        if (client.getNetworkHandler() == null) return;
        var players = client.getNetworkHandler().getPlayerList().stream().toList();
        if (players.isEmpty()) return;

        float sc = (float) ModConfig.get().scale;
        float startY = y + getIslandHeight() + Math.round(12 * sc);
        int alphaInt = (int)(alpha * 255);

        float headerSize = 12.5f * sc;
        float chipSize   = 9.5f  * sc;
        float killSize   = 10.0f * sc;
        float nameSize   = 10.5f * sc;
        float pingSize   = 8.0f  * sc;
        float initSize   = 11.5f * sc;

        // Header
        SkijaRenderer.text(canvas, "ui-bold", headerSize, x + 22, startY, withAlpha(fontColor, alphaInt), false, "Players Online");
        String countStr = String.valueOf(players.size());
        float chipW = SkijaRenderer.textWidth("ui-bold", chipSize, countStr) + 10 * sc;
        float chipX = x + 22 + SkijaRenderer.textWidth("ui-bold", headerSize, "Players Online") + 6 * sc;
        SkijaRenderer.roundedRect(canvas, chipX, startY - 6.5f * sc, chipW, 13 * sc, 6.5f * sc, withAlpha(fontColor, (int)(alpha * 28)));
        SkijaRenderer.text(canvas, "ui-bold", chipSize, chipX + chipW / 2, startY, withAlpha(fontColor, (int)(alpha * 200)), true, countStr);

        // Kill info right-aligned
        String killText = "击杀: " + killCount;
        if (!lastVictim.isEmpty())
            killText += "  ·  " + net.minecraft.text.Text.translatable("hud.dynamic-island.killed", lastVictim).getString();
        if (com.southside.dynamicisland.DynamicIslandMod.ksApiHooked) killText += "  ·  KSapi";
        float killTextWidth = SkijaRenderer.textWidth("ui", killSize, killText);
        SkijaRenderer.text(canvas, "ui", killSize, x + width - 22 - killTextWidth, startY,
            withAlpha(mixColors(fontColor, 0xFF5252, 0.35f), (int)(alpha * 200)), false, killText);

        // Divider
        SkijaRenderer.line(canvas, x + 18, startY + 18 * sc, x + width - 18, startY + 18 * sc, 0.75f,
            withAlpha(fontColor, (int)(alpha * 22)));

        float itemWidth  = 175 * sc;
        float itemHeight = 34  * sc;
        int cols = 4;
        float gridY      = startY + 28 * sc;
        float gridStartX = x + (width - (cols * itemWidth)) / 2.0f;
        float avatarD    = 24 * sc;
        float avatarR    = avatarD / 2.0f;
        float cardR      = 11.0f * sc;

        int count = 0;
        for (var player : players) {
            if (count >= cols * 10) break;

            int col = count % cols;
            int row = count / cols;
            float itemX = gridStartX + col * itemWidth;
            float itemY = gridY + row * (itemHeight + 5 * sc);

            SkijaRenderer.roundedRect(canvas, itemX + 3 * sc, itemY, itemWidth - 6 * sc, itemHeight, cardR,
                withAlpha(fontColor, (int)(alpha * 20)));
            SkijaRenderer.roundedRectStroke(canvas, itemX + 3.5f * sc, itemY + 0.5f, itemWidth - 7 * sc, itemHeight - 1, cardR - 0.5f, 0.6f,
                withAlpha(0xFFFFFF, (int)(alpha * 14)));

            String rawName = player.getDisplayName() != null ? player.getDisplayName().getString() : player.getProfile().getName();
            String name = rawName.replaceAll("[\\u00A7§][0-9a-fA-Fk-oK-OrR]", "").replaceAll("\\[.*?\\]", "").trim();
            if (name.isEmpty()) name = rawName.replaceAll("[\\u00A7§][0-9a-fA-Fk-oK-OrR]", "");

            int ping = player.getLatency();

            String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
            int avatarColor = getColorForName(name);
            float avatarX  = itemX + 11 * sc;
            float avatarCY = itemY + itemHeight / 2.0f;
            SkijaRenderer.roundedRect(canvas, avatarX, avatarCY - avatarR, avatarD, avatarD, avatarR, withAlpha(avatarColor, alphaInt));
            SkijaRenderer.roundedRectStroke(canvas, avatarX + 0.5f, avatarCY - avatarR + 0.5f, avatarD - 1, avatarD - 1, avatarR - 0.5f, 0.75f,
                withAlpha(0xFFFFFF, (int)(alpha * 30)));
            SkijaRenderer.text(canvas, "ui-bold", initSize, avatarX + avatarR, avatarCY, withAlpha(0xFFFFFF, alphaInt), true, initial);

            String pingText  = ping + " ms";
            float pingWidth  = SkijaRenderer.textWidth("ui", pingSize, pingText);
            float maxNameW   = itemWidth - (64 + pingWidth) * sc;
            String displayName = name;
            if (SkijaRenderer.textWidth("ui", nameSize, name) > maxNameW) {
                while (displayName.length() > 1 && SkijaRenderer.textWidth("ui", nameSize, displayName + "...") > maxNameW)
                    displayName = displayName.substring(0, displayName.length() - 1);
                displayName += "...";
            }
            SkijaRenderer.text(canvas, "ui", nameSize, itemX + 42 * sc, avatarCY - 1,
                withAlpha(fontColor, (int)(alpha * 245)), false, displayName);

            int statusColor = ping < 50 ? 0x4CAF50 : (ping < 150 ? 0xFFC107 : 0xF44336);
            float dotR  = 3.0f * sc;
            float dotCX = itemX + itemWidth - 11 * sc;
            SkijaRenderer.roundedRect(canvas, dotCX - dotR, avatarCY - dotR, dotR * 2, dotR * 2, dotR, withAlpha(statusColor, alphaInt));
            SkijaRenderer.text(canvas, "ui", pingSize, dotCX - 5 * sc - pingWidth, avatarCY,
                withAlpha(fontColor, (int)(alpha * 150)), false, pingText);

            count++;
        }
    }

    private int getColorForName(String name) {
        int hash = name.hashCode();
        // A set of MD3-compatible muted primary colors
        int[] palette = {
            0x6750A4, 0x03314B, 0x006A6A, 0x984061, 0x445E91, 0x006874, 0x006E1C, 0x7D5260
        };
        return palette[Math.abs(hash % palette.length)];
    }

    private void renderIsland(org.jetbrains.skia.Canvas canvas, MinecraftClient client, float x, float y, float width, float height, float radius, int backgroundColor, int fontColor, ModConfig config, String roleId) {
        // pillH = the fixed top-strip height; height = smoothHeight (may be expanded for tablist)
        // ALL content (text, dividers) must use pillH so they stay in the top pill strip
        // and never overlap the tablist rendered below it.
        float pillH = (float) getIslandHeight();

        // --- Acrylic shell (full expanded height) ---
        SkijaRenderer.roundedRect(canvas, x + 1, y + 5, width - 2, height, radius, withAlpha(0x000000, 22));
        SkijaRenderer.roundedRect(canvas, x + 0.5f, y + 2.5f, width - 1, height, radius, withAlpha(0x000000, 16));
        int acrylicBase = mixColors(backgroundColor, 0xFFFFFF, 0.10f);
        SkijaRenderer.roundedRect(canvas, x, y, width, height, radius, withAlpha(acrylicBase, config.acrylicOpacity));
        SkijaRenderer.roundedRectGradient(canvas, x, y, width, height, radius,
            withAlpha(0xFFFFFF, 32), withAlpha(0x000000, 10));
        SkijaRenderer.roundedRectStroke(canvas, x + 0.5f, y + 0.5f, width - 1.0f, height - 1.0f,
            radius - 0.5f, 1.0f, withAlpha(0xFFFFFF, 100));
        SkijaRenderer.roundedRectStroke(canvas, x + 1.5f, y + 1.5f, width - 3.0f, height - 3.0f,
            radius - 1.5f, 0.5f, withAlpha(0x000000, 18));

        // --- Left brand zone (anchored to pill strip) ---
        float iconCY = y + pillH / 2.0f;
        int accentColor = mixColors(fontColor, 0xFFFFFF, 0.20f);
        SkijaRenderer.roundedRect(canvas, x + 6.5f, iconCY - 9.0f, 18.0f, 18.0f, 9.0f,
            withAlpha(accentColor, 55));
        SkijaRenderer.roundedRectStroke(canvas, x + 7.0f, iconCY - 8.5f, 17.0f, 17.0f, 8.5f, 0.75f,
            withAlpha(0xFFFFFF, 35));
        SkijaRenderer.text(canvas, "ui-bold", 10.0f, x + 6.5f + 9.0f, iconCY + 1.0f,
            withAlpha(fontColor, 255), true, "S");

        int titleColor = withAlpha(applyStyleColor(fontColor, config.fontStyle, true), 255);
        SkijaRenderer.text(canvas, config.fontStyle == 2 ? "ui-bold" : "ui",
            11.5f * config.fontScale, x + 28.0f, iconCY, titleColor, false, "Southside Nextgen");

        boolean showEvent = false; // victory moved to external toast

        // --- TAB clock anchored to pill strip top-right ---
        if (tabAnimation > 0.05f) {
            String timeText = java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            SkijaRenderer.text(canvas, "ui", 7.5f * config.fontScale,
                x + width - 38, iconCY - 4,
                withAlpha(mixColors(fontColor, 0xFFFFFF, 0.3f), (int)(tabAnimation * 160)), true, "TIME");
            SkijaRenderer.text(canvas, "ui-bold", 10.5f * config.fontScale,
                x + width - 38, iconCY + 5,
                withAlpha(fontColor, (int)(tabAnimation * 220)), true, timeText);
        }

        // --- Dividers confined to pill strip ---
        float divider1X = x + 147.0f;
        SkijaRenderer.line(canvas, divider1X, y + 8.0f, divider1X, y + pillH - 8.0f, 1.0f,
            withAlpha(mixColors(fontColor, 0xFFFFFF, 0.5f), 80));
        SkijaRenderer.line(canvas, divider1X + 0.5f, y + 8.5f, divider1X + 0.5f, y + pillH - 8.5f, 0.5f,
            withAlpha(0xFFFFFF, 25));

        if (showEvent) {
            float eventAreaCenterX = (divider1X + x + width) / 2.0f;
            SkijaRenderer.roundedRect(canvas, eventAreaCenterX - 38, y + 7, 76, pillH - 14, 6.0f,
                withAlpha(0xFFD700, 18));
            SkijaRenderer.text(canvas, "ui-bold", 12.5f * config.fontScale, eventAreaCenterX,
                iconCY, withAlpha(0xFFD700, 255), true, "VICTORY!");
        } else {
            float infoX = divider1X + 7.5f;
            if (countdownSeconds >= 0) {
                int mins = countdownSeconds / 60;
                int secs = countdownSeconds % 60;
                String countdownLabel = "Game Starting";
                String countdownValue = mins > 0
                    ? mins + ":" + String.format("%02d", secs)
                    : String.valueOf(secs);
                float valW = SkijaRenderer.textWidth("ui-bold", 9.5f * config.fontScale, countdownValue);
                SkijaRenderer.roundedRect(canvas, infoX - 2, iconCY + 1.5f, valW + 8, 11.5f, 4.0f,
                    withAlpha(0xFFD700, 28));
                SkijaRenderer.text(canvas, "ui", 6.5f * config.fontScale, infoX, iconCY - 5.0f,
                    withAlpha(mixColors(fontColor, 0xFFFFFF, 0.2f), 190), false, countdownLabel);
                SkijaRenderer.text(canvas, "ui-bold", 9.5f * config.fontScale, infoX, iconCY + 7.5f,
                    withAlpha(0xFFD700, 255), false, countdownValue);
            } else if (config.showServerAddress || config.showLatency) {
                String serverAddress = config.showServerAddress ? getServerAddress(client) : "";
                String serverPing    = config.showLatency       ? getLatency(client)        : "";
                if (!serverAddress.isEmpty())
                    SkijaRenderer.text(canvas, config.fontStyle == 2 ? "ui-bold" : "ui",
                        7.5f * config.fontScale, infoX, iconCY - 4.5f,
                        withAlpha(applyStyleColor(fontColor, config.fontStyle, false), 240), false, serverAddress);
                if (!serverPing.isEmpty())
                    SkijaRenderer.text(canvas, "ui", 6.0f * config.fontScale, infoX, iconCY + 5.5f,
                        withAlpha(mixColors(fontColor, 0xFFFFFF, 0.35f), 170), false, serverPing);
            }

            // --- Online Players section (TAB only) ---
            float divider2X = x + 222.0f;
            if (tabAnimation > 0.05f) {
                SkijaRenderer.line(canvas, divider2X, y + 8.0f, divider2X, y + pillH - 8.0f, 1.0f,
                    withAlpha(mixColors(fontColor, 0xFFFFFF, 0.5f), (int)(tabAnimation * 80)));
                SkijaRenderer.line(canvas, divider2X + 0.5f, y + 8.5f, divider2X + 0.5f, y + pillH - 8.5f, 0.5f,
                    withAlpha(0xFFFFFF, (int)(tabAnimation * 25)));
                int localPlayers = client.getNetworkHandler() != null
                    ? client.getNetworkHandler().getPlayerList().size() : 0;
                String globalText = "Total: ?";
                ServerInfo serverInfo = client.getNetworkHandler() != null
                    ? client.getNetworkHandler().getServerInfo() : null;
                if (serverInfo != null && serverInfo.playerCountLabel != null) {
                    String label = serverInfo.playerCountLabel.getString();
                    globalText = label.contains("/") ? "Total: " + label.split("/")[0].trim() : "Total: " + label;
                } else if (client.isInSingleplayer()) {
                    globalText = "Total: 1";
                }
                float p2X = divider2X + 7.5f;
                SkijaRenderer.text(canvas, config.fontStyle == 2 ? "ui-bold" : "ui",
                    7.5f * config.fontScale, p2X, iconCY - 4.5f,
                    withAlpha(applyStyleColor(fontColor, config.fontStyle, false), (int)(tabAnimation * 240)), false,
                    localPlayers + " Players");
                SkijaRenderer.text(canvas, "ui", 6.0f * config.fontScale, p2X, iconCY + 5.5f,
                    withAlpha(mixColors(fontColor, 0xFFFFFF, 0.35f), (int)(tabAnimation * 170)), false, globalText);
            }

            // --- Role ID / Play time section ---
            if (config.showRoleId || config.showPlayTime) {
                float divider3X = x + 222.0f + (tabAnimation > 0.05f ? 65.0f : 0.0f);
                SkijaRenderer.line(canvas, divider3X, y + 8.0f, divider3X, y + pillH - 8.0f, 1.0f,
                    withAlpha(mixColors(fontColor, 0xFFFFFF, 0.5f), 80));
                SkijaRenderer.line(canvas, divider3X + 0.5f, y + 8.5f, divider3X + 0.5f, y + pillH - 8.5f, 0.5f,
                    withAlpha(0xFFFFFF, 25));
                float r3X = divider3X + 7.5f;
                if (config.showRoleId)
                    SkijaRenderer.text(canvas, config.fontStyle == 2 ? "ui-bold" : "ui",
                        7.5f * config.fontScale, r3X, iconCY - 4.5f,
                        withAlpha(applyStyleColor(fontColor, config.fontStyle, false), 240), false, roleId);
                if (config.showPlayTime)
                    SkijaRenderer.text(canvas, "ui", 6.0f * config.fontScale, r3X, iconCY + 5.5f,
                        withAlpha(mixColors(fontColor, 0xFFFFFF, 0.35f), 170), false, getPlayTime());
            }
        }

        // --- Cooldown bar and status badge anchored to actual island bottom (height = smoothHeight) ---
        if (config.showCooldownBar && cooldownAnimation > 0.01f) {
            float progress = MathHelper.clamp((float) cooldownRemaining / totalCooldown, 0, 1);

            long remSec   = cooldownRemaining / 1000;
            long remTenth = (cooldownRemaining % 1000) / 100;
            String cdLabel = remSec > 0 ? remSec + "." + remTenth + "s" : "0." + remTenth + "s";
            float labelSize = 6.5f * config.fontScale;
            float labelW = SkijaRenderer.textWidth("ui-bold", labelSize, cdLabel);

            float barX = x + 8;
            float barY = y + height - 6.5f;
            // Extra 4px right margin so "s" glyph never clips the island edge
            float labelW2 = labelW + 4;
            float barW = width - 8 - 8 - labelW2 - 6;
            SkijaRenderer.roundedRect(canvas, barX, barY, barW, 2.5f, 1.25f,
                withAlpha(0x000000, (int)(cooldownAnimation * 35)));
            SkijaRenderer.roundedRect(canvas, barX, barY, barW * progress, 2.5f, 1.25f,
                withAlpha(fontColor, (int)(cooldownAnimation * 210)));
            if (progress > 0.05f)
                SkijaRenderer.roundedRect(canvas, barX, barY, barW * progress, 1.0f, 0.5f,
                    withAlpha(0xFFFFFF, (int)(cooldownAnimation * 60)));

            // Right-aligned: left-edge of text = right-edge of island minus padding minus label width
            float labelX = x + width - 8 - labelW2;
            SkijaRenderer.text(canvas, "ui-bold", labelSize,
                labelX, barY + 1.25f,
                withAlpha(mixColors(fontColor, 0xFFFFFF, 0.1f), (int)(cooldownAnimation * 220)),
                false, cdLabel);
        }

        if (isGufa) {
            String statusText = "请停止移动";
            int statusColor   = 0xF44336;
            float badgeW = SkijaRenderer.textWidth("ui-bold", 7.0f * config.fontScale, statusText) + 12;
            float badgeH = 11.0f;
            float badgeX = x + (width - badgeW) / 2.0f;
            float badgeY = y + height - badgeH - 1.5f;
            SkijaRenderer.roundedRect(canvas, badgeX, badgeY, badgeW, badgeH, 5.5f,
                withAlpha(0xF44336, 35));
            SkijaRenderer.text(canvas, "ui-bold", 7.0f * config.fontScale,
                x + width / 2.0f, badgeY + badgeH / 2.0f,
                withAlpha(statusColor, 255), true, statusText);
        }
    }

    private int applyStyleColor(int baseColor, int style, boolean main) {
        return switch (style) {
            case 1 -> baseColor;
            case 2 -> mixColors(baseColor, 0xFFFFFF, main ? 0.18f : 0.08f);
            default -> mixColors(baseColor, 0xFFFFFF, main ? 0.06f : 0.0f);
        };
    }

    private String getServerAddress(MinecraftClient client) {
        if (client.getNetworkHandler() == null) {
            return "singleplayer";
        }
        // Force display name for [Fis] proxy players
        if (client.player != null) {
            PlayerListEntry selfEntry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
            String selfName = selfEntry != null && selfEntry.getDisplayName() != null
                    ? selfEntry.getDisplayName().getString()
                    : client.player.getName().getString();
            if (selfName != null && selfName.contains("[Fis]")) {
                return "Fisproxy";
            }
        }
        // Try to get the actual resolved IP from the live socket connection
        try {
            var sockAddr = client.getNetworkHandler().getConnection().getAddress();
            if (sockAddr instanceof InetSocketAddress inet && inet.getAddress() != null) {
                String ip = inet.getAddress().getHostAddress();
                return ip.length() > 15 ? ip.substring(0, 13) + "..." : ip;
            }
        } catch (Exception ignored) {}
        // Fallback to the address string from ServerInfo
        ServerInfo serverInfo = client.getNetworkHandler().getServerInfo();
        if (serverInfo == null || serverInfo.address == null || serverInfo.address.isBlank()) {
            return "singleplayer";
        }
        String address = serverInfo.address.toLowerCase();
        return address.length() > 12 ? address.substring(0, 10) + "..." : address;
    }

    private String getRoleId(MinecraftClient client) {
        if (client.player == null) return "";
        String name = null;
        if (client.getNetworkHandler() != null) {
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
            if (entry != null && entry.getDisplayName() != null) {
                name = entry.getDisplayName().getString();
            }
        }
        if (name == null || name.isBlank()) {
            name = client.player.getName().getString();
        }
        // Strip all Minecraft color/format codes: §X and the Unicode section-sign variant
        return name.replaceAll("[\\u00A7§][0-9a-fA-Fk-oK-OrR]", "");
    }

    private String getPlayTime() {
        if (sessionStartTime == 0) return "0:00";
        long elapsed = (System.currentTimeMillis() - sessionStartTime) / 1000;
        long hours = elapsed / 3600;
        long minutes = (elapsed % 3600) / 60;
        long seconds = elapsed % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }

    private void updateServerSession(MinecraftClient client) {
        boolean inSession = client.getNetworkHandler() != null;
        if (!wasOnServer && inSession) {
            sessionStartTime = System.currentTimeMillis();
        } else if (wasOnServer && !inSession) {
            sessionStartTime = 0;
        }
        wasOnServer = inSession;
    }

    private String getLatency(MinecraftClient client) {
        if (client.getNetworkHandler() == null || client.player == null) {
            return "0 ms";
        }
        PlayerListEntry playerListEntry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        if (playerListEntry == null) {
            return "0 ms";
        }
        return Math.max(playerListEntry.getLatency(), 0) + " ms";
    }

    private void updatePosition(MinecraftClient client, int islandWidth, int islandHeight) {
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        ModConfig config = ModConfig.get();

        // Migration: If we have old absolute coords but no relative ones
        if (config.relativeX < 0 && config.posX >= 0) {
            config.relativeX = (double) config.posX / Math.max(1, screenWidth);
            config.relativeY = (double) config.posY / Math.max(1, screenHeight);
            config.posX = -1; // Mark as migrated
        }

        if (config.relativeX < 0) {
            currentX = (screenWidth - islandWidth) / 2;
            currentY = 20;
            // First time initialization
            if (!initialized) {
                smoothX = currentX;
                smoothY = currentY;
                initialized = true;
                config.relativeX = (double) currentX / Math.max(1, screenWidth);
                config.relativeY = (double) currentY / Math.max(1, screenHeight);
            }
        } else {
            // Apply relative position to current screen size
            currentX = (int) (config.relativeX * screenWidth);
            currentY = (int) (config.relativeY * screenHeight);
            
            if (!initialized) {
                smoothX = currentX;
                smoothY = currentY;
                initialized = true;
            }
        }

        // Clamp to screen
        currentX = MathHelper.clamp(currentX, 0, Math.max(0, screenWidth - islandWidth));
        currentY = MathHelper.clamp(currentY, 0, Math.max(0, screenHeight - islandHeight));

        smoothX += (currentX - smoothX) * 0.18f;
        smoothY += (currentY - smoothY) * 0.18f;
    }

    private int getIslandWidth() {
        // title(145) + server(75) + role section(dynamic, min 90) + right padding(10)
        // badge expands the island to the right when kill/victory is active
        int roleSection = Math.max(90, requiredRoleWidth + 15);
        // When event is active it replaces IP+role sections, so use its width instead
        int infoSection = 75 + roleSection;
        int base = 145 + infoSection + 10;
        return Math.max(290, Math.round(base * (float) ModConfig.get().scale));
    }

    private int getIslandHeight() {
        return Math.max(28, Math.round(BASE_ISLAND_HEIGHT * (float) ModConfig.get().scale));
    }

    private int getCornerRadius() {
        return Math.max(14, Math.round(BASE_CORNER_RADIUS * (float) ModConfig.get().scale));
    }

    private int getBackgroundColor(ModConfig config) {
        int rgb = Color.HSBtoRGB(config.backgroundHue / 360.0f, config.backgroundSaturation, config.backgroundBrightness);
        return rgb & 0xFFFFFF;
    }

    private int getFontColor(ModConfig config) {
        int rgb = Color.HSBtoRGB(config.fontHue / 360.0f, config.fontSaturation, config.fontBrightness);
        return rgb & 0xFFFFFF;
    }

    private int getTabBackgroundColor(ModConfig config) {
        int rgb = Color.HSBtoRGB(config.tabBackgroundHue / 360.0f, config.tabBackgroundSaturation, config.tabBackgroundBrightness);
        return rgb & 0xFFFFFF;
    }

    private int getTabFontColor(ModConfig config) {
        int rgb = Color.HSBtoRGB(config.tabFontHue / 360.0f, config.tabFontSaturation, config.tabFontBrightness);
        return rgb & 0xFFFFFF;
    }

    private int withAlpha(int rgb, int alpha) {
        return (alpha & 0xFF) << 24 | (rgb & 0xFFFFFF);
    }

    private int mixColors(int first, int second, float mix) {
        float clamped = MathHelper.clamp(mix, 0.0f, 1.0f);
        int red = Math.round(((first >> 16) & 0xFF) * (1.0f - clamped) + ((second >> 16) & 0xFF) * clamped);
        int green = Math.round(((first >> 8) & 0xFF) * (1.0f - clamped) + ((second >> 8) & 0xFF) * clamped);
        int blue = Math.round((first & 0xFF) * (1.0f - clamped) + (second & 0xFF) * clamped);
        return (MathHelper.clamp(red, 0, 255) << 16)
                | (MathHelper.clamp(green, 0, 255) << 8)
                | (MathHelper.clamp(blue, 0, 255));
    }
}
