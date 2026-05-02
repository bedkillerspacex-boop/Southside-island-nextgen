package com.southside.dynamicisland.screen;

import com.southside.dynamicisland.config.ModConfig;
import com.southside.dynamicisland.render.SkijaRenderer;
import com.southside.dynamicisland.system.LyricAutoOffset;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jetbrains.skia.Canvas;
import org.jetbrains.skia.Rect;
import org.lwjgl.glfw.GLFW;


public class IslandSettingsScreen extends Screen {

    // ── MD3 Dark palette ──────────────────────────────────────────────────────
    private static final int COLOR_SCRIM        = 0xB8000000;
    private static final int COLOR_SURFACE      = 0xFF1C1B1F;
    private static final int COLOR_SURFACE_CONT = 0xFF211F26;
    private static final int COLOR_SURFACE_HIGH = 0xFF2B2930;
    private static final int COLOR_PRIMARY      = 0xFFD0BCFF;
    private static final int COLOR_PRIMARY_DIM  = 0xFF9A82DB;
    private static final int COLOR_PRIMARY_CONT = 0xFF4A4458;
    private static final int COLOR_ON_SURFACE   = 0xFFE6E1E5;
    private static final int COLOR_ON_SURF_VAR  = 0xFFCAC4D0;
    private static final int COLOR_OUTLINE      = 0xFF938F99;
    private static final int COLOR_OUTLINE_VAR  = 0xFF49454F;
    private static final int COLOR_ERROR        = 0xFFCF6679;
    private static final int COLOR_SUCCESS      = 0xFF81C784;

    // Panel geometry — smaller than before
    private static final float PANEL_W  = 460;
    private static final float PANEL_H  = 570;
    private static final float PANEL_R  = 24;
    private static final float CARD_R   = 14;
    private static final float PAD      = 16;
    private static final float CARD_PAD = 12;
    private static final float ROW_H    = 46;
    private static final float TOGGLE_H = 44;
    private static final float TRACK_H  = 5;
    private static final float HDR_H    = 48;
    private static final float BTN_H    = 48;
    private static final float CARD_GAP = 6;

    // Content height (scrollable)
    private static final float CONTENT_H = PANEL_H - HDR_H - BTN_H;

    private static final String F  = "ui";
    private static final String FB = "ui-bold";

    private float panelX, panelY;
    private boolean dragging, draggingScroll;
    private float dragOffsetX, dragOffsetY;
    private int activeSlider = -1;
    private float scrollY = 0;
    private boolean apiFieldFocused = false;
    private static final float UI_SCALE = 0.75f;

    // ── Data descriptors ──────────────────────────────────────────────────────

    private record SliderDef(String label, String group,
                             float min, float max,
                             java.util.function.Supplier<Float> get,
                             java.util.function.Consumer<Float> set,
                             boolean isInt, String unit) {}

    private static final String G_ISLAND = "ISLAND";
    private static final String G_BG     = "BACKGROUND";
    private static final String G_FONT   = "TYPOGRAPHY";
    private static final String G_TAB    = "TABLIST";
    private static final String G_KILL   = "KILL TOAST";
    private static final String G_SMTC   = "NOW PLAYING (SMTC)";

    private final SliderDef[] SLIDERS = {
        new SliderDef("缩放比例",       G_ISLAND, 0.5f, 2.0f,
            () -> (float) ModConfig.get().scale,           v -> ModConfig.get().scale = v, false, "×"),
        new SliderDef("亚克力不透明度", G_ISLAND,   20, 255,
            () -> (float) ModConfig.get().acrylicOpacity,  v -> ModConfig.get().acrylicOpacity = Math.round(v), true, ""),
        new SliderDef("X 位置 (%)",     G_ISLAND,   0, 100,
            () -> (float)(ModConfig.get().relativeX < 0 ? 50.0 : ModConfig.get().relativeX * 100.0),
            v -> ModConfig.get().relativeX = v / 100.0, false, "%"),
        new SliderDef("Y 位置 (%)",     G_ISLAND,   0, 100,
            () -> (float)(ModConfig.get().relativeY * 100.0),
            v -> ModConfig.get().relativeY = v / 100.0, false, "%"),
        new SliderDef("色相",   G_BG,   0, 360, () -> ModConfig.get().backgroundHue,        v -> ModConfig.get().backgroundHue = v,        false, "°"),
        new SliderDef("饱和度", G_BG,   0, 1,   () -> ModConfig.get().backgroundSaturation, v -> ModConfig.get().backgroundSaturation = v, false, ""),
        new SliderDef("亮度",   G_BG,   0, 1,   () -> ModConfig.get().backgroundBrightness, v -> ModConfig.get().backgroundBrightness = v, false, ""),
        new SliderDef("色相",   G_FONT, 0, 360, () -> ModConfig.get().fontHue,              v -> ModConfig.get().fontHue = v,              false, "°"),
        new SliderDef("饱和度", G_FONT, 0, 1,   () -> ModConfig.get().fontSaturation,       v -> ModConfig.get().fontSaturation = v,       false, ""),
        new SliderDef("亮度",   G_FONT, 0, 1,   () -> ModConfig.get().fontBrightness,       v -> ModConfig.get().fontBrightness = v,       false, ""),
        new SliderDef("字体缩放", G_FONT, 0.5f, 2.0f, () -> ModConfig.get().fontScale,      v -> ModConfig.get().fontScale = v,            false, "×"),
        new SliderDef("背景色相",   G_TAB, 0, 360, () -> ModConfig.get().tabBackgroundHue,        v -> ModConfig.get().tabBackgroundHue = v,        false, "°"),
        new SliderDef("背景饱和度", G_TAB, 0, 1,   () -> ModConfig.get().tabBackgroundSaturation, v -> ModConfig.get().tabBackgroundSaturation = v, false, ""),
        new SliderDef("背景亮度",   G_TAB, 0, 1,   () -> ModConfig.get().tabBackgroundBrightness, v -> ModConfig.get().tabBackgroundBrightness = v, false, ""),
        new SliderDef("字体色相",   G_TAB, 0, 360, () -> ModConfig.get().tabFontHue,              v -> ModConfig.get().tabFontHue = v,              false, "°"),
        new SliderDef("字体饱和度", G_TAB, 0, 1,   () -> ModConfig.get().tabFontSaturation,       v -> ModConfig.get().tabFontSaturation = v,       false, ""),
        new SliderDef("字体亮度",   G_TAB, 0, 1,   () -> ModConfig.get().tabFontBrightness,       v -> ModConfig.get().tabFontBrightness = v,       false, ""),
        new SliderDef("X 位置 (%)", G_KILL, 0, 100,
            () -> ModConfig.get().killNotifX * 100f, v -> ModConfig.get().killNotifX = v / 100f, false, "%"),
        new SliderDef("Y 位置 (%)", G_KILL, 0, 100,
            () -> ModConfig.get().killNotifY * 100f, v -> ModConfig.get().killNotifY = v / 100f, false, "%"),
        new SliderDef("字体大小",   G_KILL, 6, 24,
            () -> ModConfig.get().killNotifFontSize, v -> ModConfig.get().killNotifFontSize = v, false, "pt"),
        new SliderDef("显示时长",   G_KILL, 500, 6000,
            () -> (float) ModConfig.get().killNotifDuration, v -> ModConfig.get().killNotifDuration = Math.round(v), true, "ms"),
        new SliderDef("X 位置 (%)", G_SMTC, 0, 100,
            () -> ModConfig.get().smtcX * 100f, v -> ModConfig.get().smtcX = v / 100f, false, "%"),
        new SliderDef("Y 位置 (%)", G_SMTC, 0, 100,
            () -> ModConfig.get().smtcY * 100f, v -> ModConfig.get().smtcY = v / 100f, false, "%"),
        new SliderDef("缩放比例",   G_SMTC, 0.5f, 2.0f,
            () -> ModConfig.get().smtcScale, v -> ModConfig.get().smtcScale = v, false, "×"),
        new SliderDef("歌词色调",   G_SMTC, 0, 360,
            () -> ModConfig.get().smtcLyricHue, v -> ModConfig.get().smtcLyricHue = v, false, "°"),
        new SliderDef("歌词饱和度", G_SMTC, 0, 100,
            () -> ModConfig.get().smtcLyricSaturation * 100f, v -> ModConfig.get().smtcLyricSaturation = v / 100f, false, "%"),
        new SliderDef("歌词时间偏移", G_SMTC, -5000, 5000,
            () -> (float) ModConfig.get().smtcLyricOffset, v -> ModConfig.get().smtcLyricOffset = Math.round(v), true, "ms"),
        new SliderDef("歌词最大宽度", G_SMTC, 100, 400,
            () -> (float) ModConfig.get().smtcMaxLyricWidth, v -> ModConfig.get().smtcMaxLyricWidth = Math.round(v), true, "px"),
        new SliderDef("歌词最小宽度", G_SMTC, 50, 200,
            () -> (float) ModConfig.get().smtcMinLyricWidth, v -> ModConfig.get().smtcMinLyricWidth = Math.round(v), true, "px"),
    };

    private record ToggleDef(String label, java.util.function.Supplier<String> desc,
                             java.util.function.Supplier<Boolean> get,
                             java.util.function.Consumer<Boolean> set) {}

    private final ToggleDef[] TOGGLES = {
        new ToggleDef("启用灵动岛",       () -> "主开关 · 关闭后隐藏整个 HUD",
            () -> ModConfig.get().enabled,                 v -> ModConfig.get().enabled = v),
        new ToggleDef("自定义 Tab 列表",  () -> "按住 TAB 展开玩家列表",
            () -> ModConfig.get().customTablistEnabled,    v -> ModConfig.get().customTablistEnabled = v),
        new ToggleDef("系统媒体播放 (SMTC)",() -> "显示当前播放歌曲及进度",
            () -> ModConfig.get().smtcEnabled,             v -> ModConfig.get().smtcEnabled = v),
        new ToggleDef("单行歌词模式", () -> "开启后仅显示当前 1 行歌词，关闭则显示 3 行",
            () -> ModConfig.get().smtcLyricLines == 1,     v -> ModConfig.get().smtcLyricLines = v ? 1 : 3),
        new ToggleDef("歌词艺术家匹配",     () -> "开启后用艺术家名精准搜索歌词，关闭则仅用歌名",
            () -> ModConfig.get().smtcArtistEnabled,       v -> ModConfig.get().smtcArtistEnabled = v),
        new ToggleDef("启用自定义歌词 API", () -> "开启后使用下方的自定义 API URL",
            () -> ModConfig.get().smtcCustomApiEnabled,       v -> ModConfig.get().smtcCustomApiEnabled = v),
        new ToggleDef("本地 LrcApi (28883)", () -> "使用本地运行的 LrcApi 服务 (127.0.0.1:28883)",
            () -> ModConfig.get().smtcLocalApiEnabled,       v -> ModConfig.get().smtcLocalApiEnabled = v),
        new ToggleDef("内置备选歌词引擎", () -> "外部 API 失效时自动搜索 (Netease)",
            () -> ModConfig.get().smtcBuiltinApiEnabled,       v -> ModConfig.get().smtcBuiltinApiEnabled = v),
        new ToggleDef("歌词延迟自动抵消", () -> "自动 Ping 接口补偿网络延迟" + (LyricAutoOffset.getLastPing() > 0 ? " (" + LyricAutoOffset.getLastPing() + "ms)" : ""),
            () -> ModConfig.get().smtcAutoOffset,       v -> {
                ModConfig.get().smtcAutoOffset = v;
                if (v) {
                    LyricAutoOffset.startAutoOffsetScheduler();
                } else {
                    LyricAutoOffset.stopAutoOffsetScheduler();
                    LyricAutoOffset.updateAutoOffset(); // force reset to manual offset
                }
            }),
        new ToggleDef("击杀通知",         () -> "需要 Killsay-Reborn Mod",
            () -> ModConfig.get().killNotificationEnabled, v -> ModConfig.get().killNotificationEnabled = v),
        new ToggleDef("显示服务器地址",   () -> "岛屿右侧 IP / 服务器地址",
            () -> ModConfig.get().showServerAddress,       v -> ModConfig.get().showServerAddress = v),
        new ToggleDef("显示延迟",         () -> "岛屿右侧 Ping 数值",
            () -> ModConfig.get().showLatency,             v -> ModConfig.get().showLatency = v),
        new ToggleDef("显示角色 ID",      () -> "岛屿右侧玩家名/角色标签",
            () -> ModConfig.get().showRoleId,              v -> ModConfig.get().showRoleId = v),
        new ToggleDef("显示在线时长",     () -> "角色 ID 下方会话时长",
            () -> ModConfig.get().showPlayTime,            v -> ModConfig.get().showPlayTime = v),
        new ToggleDef("显示冷却进度条",   () -> "岛屿底部 Killsay 冷却条",
            () -> ModConfig.get().showCooldownBar,         v -> ModConfig.get().showCooldownBar = v),
    };

    private static final String[] FS_LABELS = {"Soft", "Vivid", "Bold"};
    private static final String[] FS_DESC   = {"柔和", "鲜艳", "加粗"};

    // ── Pre-computed layout ───────────────────────────────────────────────────

    private static final float TOGGLE_CARD_H = CARD_PAD + 22 + CARD_PAD + 15 * TOGGLE_H + CARD_PAD;
    private static final float FS_CARD_H     = CARD_PAD + 22 + CARD_PAD + 56 + CARD_PAD;

    private float yToggleCard, yFsCard;
    private float[] ySliderCardStart;
    private int[]   groupSliderStart, groupSliderCount;
    private String[] groupNames;
    private int numGroups;

    public IslandSettingsScreen() {
        super(Text.literal("Dynamic Island"));
    }

    @Override
    protected void init() {
        panelX = (this.width  - PANEL_W) / 2f;
        panelY = (this.height - PANEL_H) / 2f;
        computeLayout();
        if (ModConfig.get().smtcAutoOffset) LyricAutoOffset.updateAutoOffset();
    }

    private void computeLayout() {
        String[] gs = {G_ISLAND, G_BG, G_FONT, G_TAB, G_KILL, G_SMTC};
        numGroups = gs.length;
        groupNames = gs;
        groupSliderStart = new int[numGroups];
        groupSliderCount = new int[numGroups];
        ySliderCardStart = new float[numGroups];
        for (int g = 0; g < numGroups; g++) {
            int s = -1, c = 0;
            for (int i = 0; i < SLIDERS.length; i++)
                if (SLIDERS[i].group().equals(gs[g])) { if (s < 0) s = i; c++; }
            groupSliderStart[g] = s < 0 ? 0 : s;
            groupSliderCount[g] = c;
        }
        yToggleCard = 0;
        yFsCard = yToggleCard + TOGGLE_CARD_H + CARD_GAP;
        float y = yFsCard + FS_CARD_H + CARD_GAP;
        for (int g = 0; g < numGroups; g++) { ySliderCardStart[g] = y; y += sliderCardH(g) + CARD_GAP; }
    }

    private float sliderCardH(int g) {
        float h = CARD_PAD + 22 + CARD_PAD + groupSliderCount[g] * ROW_H + CARD_PAD;
        if (groupNames[g].equals(G_SMTC)) h += 65;
        return h;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        Canvas canvas = SkijaRenderer.beginFrame();
        try { draw(canvas, mouseX, mouseY); }
        finally { SkijaRenderer.endFrame(); }
    }

    private void draw(Canvas canvas, int mx, int my) {
        // Scrim with subtle vignette gradient effect
        SkijaRenderer.roundedRect(canvas, 0, 0, this.width, this.height, 0, COLOR_SCRIM);

        // Map real mouse to virtual panel space
        float scx = this.width / 2f, scy = this.height / 2f;
        int vmx = (int)(scx + (mx - scx) / UI_SCALE);
        int vmy = (int)(scy + (my - scy) / UI_SCALE);

        // Apply 0.75x scale
        canvas.save();
        canvas.translate(scx, scy);
        canvas.scale(UI_SCALE, UI_SCALE);
        canvas.translate(-scx, -scy);

        // Panel: layered shadow
        SkijaRenderer.roundedRect(canvas, panelX + 1, panelY + 4, PANEL_W, PANEL_H, PANEL_R, 0x55000000);
        SkijaRenderer.roundedRect(canvas, panelX + 0, panelY + 1, PANEL_W, PANEL_H, PANEL_R, 0x30000000);
        // Panel surface
        SkijaRenderer.roundedRect(canvas, panelX, panelY, PANEL_W, PANEL_H, PANEL_R, COLOR_SURFACE);
        // Subtle top-highlight border (glass feel)
        SkijaRenderer.roundedRectStroke(canvas, panelX + 0.5f, panelY + 0.5f, PANEL_W - 1, PANEL_H - 1, PANEL_R, 0.8f, 0x28FFFFFF);


        // Panel-level scissor: clip ALL content to panel bounds
        canvas.save();
        canvas.clipRect(Rect.makeXYWH(panelX, panelY, PANEL_W, PANEL_H));
        drawHeader(canvas, vmx, vmy);

        float contentTop = panelY + HDR_H;
        canvas.save();
        canvas.clipRect(Rect.makeXYWH(panelX + 1, contentTop, PANEL_W - 2, CONTENT_H));
        float oy = contentTop - scrollY;
        drawToggleCard(canvas, vmx, vmy, oy + yToggleCard);
        drawFsCard(canvas, vmx, vmy, oy + yFsCard);
        for (int g = 0; g < numGroups; g++) drawSliderCard(canvas, vmx, vmy, oy + ySliderCardStart[g], g);
        canvas.restore();  // restore to panel-level scissor

        drawScrollbar(canvas);
        drawBottomBar(canvas, vmx, vmy);
        canvas.restore();  // restore panel scissor
        canvas.restore();  // restore scale transform
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private void drawHeader(Canvas canvas, int mx, int my) {
        float hx = panelX + PAD;
        float hy = panelY + HDR_H / 2f;

        // Purple accent pill left of title
        SkijaRenderer.roundedRect(canvas, hx, hy - 9, 4, 18, 2, COLOR_PRIMARY);

        // Title: large, spaced caps feel — big regular weight looks more elegant than bold here
        SkijaRenderer.textSpaced(canvas, FB, 15, 0.8f, hx + 10, hy - 4, COLOR_ON_SURFACE, false, "DYNAMIC ISLAND");
        SkijaRenderer.textSpaced(canvas, F, 9, 1.2f, hx + 11, hy + 8, COLOR_ON_SURF_VAR, false, "SETTINGS");

        // Close button — circular
        float cx = panelX + PANEL_W - PAD - 18;
        float cy = hy - 9;
        boolean hov = mx >= cx && mx <= cx + 18 && my >= cy && my <= cy + 18;
        SkijaRenderer.roundedRect(canvas, cx, cy, 18, 18, 9, hov ? 0x50CF6679 : 0x22FFFFFF);
        SkijaRenderer.text(canvas, FB, 11, cx + 9, cy + 9, hov ? COLOR_ERROR : COLOR_OUTLINE, true, "X");

        // Hairline divider
        SkijaRenderer.roundedRect(canvas, panelX + PAD, panelY + HDR_H - 0.5f, PANEL_W - PAD * 2, 1, 0.5f, COLOR_OUTLINE_VAR);
    }

    // ── Toggle card ───────────────────────────────────────────────────────────

    private void drawToggleCard(Canvas canvas, int mx, int my, float top) {
        float cx = panelX + PAD, cw = PANEL_W - PAD * 2;
        SkijaRenderer.roundedRect(canvas, cx, top, cw, TOGGLE_CARD_H, CARD_R, COLOR_SURFACE_CONT);
        canvas.save();
        canvas.clipRect(Rect.makeXYWH(cx + CARD_R, top + CARD_R, cw - CARD_R * 2, TOGGLE_CARD_H - CARD_R * 2));
        drawCardTitle(canvas, cx, top, "GENERAL");
        float ry = top + CARD_PAD + 22 + CARD_PAD;
        for (int i = 0; i < TOGGLES.length; i++) { drawToggleRow(canvas, mx, my, cx + CARD_PAD, cw - CARD_PAD * 2, ry, i); ry += TOGGLE_H; }
        canvas.restore();
    }

    private void drawToggleRow(Canvas canvas, int mx, int my, float rx, float rw, float ry, int idx) {
        ToggleDef td = TOGGLES[idx];
        boolean val = td.get().get();
        float mid = ry + TOGGLE_H / 2f;
        boolean hov = inBounds(mx, my, rx, ry, rw, TOGGLE_H);
        if (hov) SkijaRenderer.roundedRect(canvas, rx, ry + 1, rw, TOGGLE_H - 2, 10, 0x16FFFFFF);

        SkijaRenderer.text(canvas, FB, 11, rx + 8, mid - 6, COLOR_ON_SURFACE, false, td.label());
        SkijaRenderer.text(canvas, F,  8,  rx + 8, mid + 6, COLOR_ON_SURF_VAR, false, td.desc().get());

        // MD3 switch
        float sw = 40, sh = 22, sx = rx + rw - sw - 4, sy = mid - sh / 2f;
        SkijaRenderer.roundedRect(canvas, sx, sy, sw, sh, sh / 2, val ? COLOR_PRIMARY_DIM : COLOR_PRIMARY_CONT);
        float tx = val ? sx + sw - 20 : sx + 2;
        SkijaRenderer.roundedRect(canvas, tx, sy + 2, 18, 18, 9, val ? COLOR_PRIMARY : COLOR_ON_SURF_VAR);
        // Tiny dot inside thumb for depth
        SkijaRenderer.roundedRect(canvas, tx + 6, sy + 8, 6, 6, 3, val ? 0x40000000 : 0x30FFFFFF);
    }

    // ── Font style card ───────────────────────────────────────────────────────

    private void drawFsCard(Canvas canvas, int mx, int my, float top) {
        float cx = panelX + PAD, cw = PANEL_W - PAD * 2;
        SkijaRenderer.roundedRect(canvas, cx, top, cw, FS_CARD_H, CARD_R, COLOR_SURFACE_CONT);
        canvas.save();
        canvas.clipRect(Rect.makeXYWH(cx + CARD_R, top + CARD_R, cw - CARD_R * 2, FS_CARD_H - CARD_R * 2));
        drawCardTitle(canvas, cx, top, "FONT STYLE");

        float ry = top + CARD_PAD + 22 + CARD_PAD;
        int cur = ModConfig.get().fontStyle;
        float btnW = (cw - CARD_PAD * 2 - 8 * 2) / 3f;
        float bx = cx + CARD_PAD, by = ry + 8;
        float btnH = 32;
        for (int i = 0; i < FS_LABELS.length; i++) {
            float x = bx + i * (btnW + 8);
            boolean active = cur == i;
            boolean hov = inBounds(mx, my, x, by, btnW, btnH);
            // Active: filled pill; hover: subtle tint; default: outlined
            if (active) {
                SkijaRenderer.roundedRect(canvas, x, by, btnW, btnH, btnH / 2, COLOR_PRIMARY);
            } else if (hov) {
                SkijaRenderer.roundedRect(canvas, x, by, btnW, btnH, btnH / 2, COLOR_PRIMARY_CONT);
                SkijaRenderer.roundedRectStroke(canvas, x + 0.5f, by + 0.5f, btnW - 1, btnH - 1, btnH / 2, 0.8f, COLOR_OUTLINE_VAR);
            } else {
                SkijaRenderer.roundedRectStroke(canvas, x + 0.5f, by + 0.5f, btnW - 1, btnH - 1, btnH / 2, 0.8f, COLOR_OUTLINE_VAR);
            }
            SkijaRenderer.text(canvas, active ? FB : F, 10, x + btnW / 2, by + btnH / 2 - 4,
                active ? 0xFF1C1B1F : COLOR_ON_SURFACE, true, FS_LABELS[i]);
            SkijaRenderer.text(canvas, F, 8, x + btnW / 2, by + btnH / 2 + 6,
                active ? 0x991C1B1F : COLOR_ON_SURF_VAR, true, FS_DESC[i]);
        }
        canvas.restore();
    }

    // ── Slider card ───────────────────────────────────────────────────────────

    private void drawSliderCard(Canvas canvas, int mx, int my, float top, int g) {
        float cx = panelX + PAD, cw = PANEL_W - PAD * 2;
        float ch = sliderCardH(g);
        SkijaRenderer.roundedRect(canvas, cx, top, cw, ch, CARD_R, COLOR_SURFACE_CONT);

        canvas.save();
        canvas.clipRect(Rect.makeXYWH(cx + CARD_R, top + CARD_R, cw - CARD_R * 2, ch - CARD_R * 2));
        drawCardTitle(canvas, cx, top, groupNames[g]);

        // Color preview swatch in card title area for BG and FONT groups
        if (groupNames[g].equals(G_BG)) {
            ModConfig cfg = ModConfig.get();
            int rgb = java.awt.Color.HSBtoRGB(cfg.backgroundHue / 360f, cfg.backgroundSaturation, cfg.backgroundBrightness) & 0xFFFFFF;
            drawInlineColorChip(canvas, cx + cw - CARD_PAD - 24, top + CARD_PAD + 4, 24, 14, rgb, "背景色");
        } else if (groupNames[g].equals(G_FONT)) {
            ModConfig cfg = ModConfig.get();
            int rgb = java.awt.Color.HSBtoRGB(cfg.fontHue / 360f, cfg.fontSaturation, cfg.fontBrightness) & 0xFFFFFF;
            drawInlineColorChip(canvas, cx + cw - CARD_PAD - 24, top + CARD_PAD + 4, 24, 14, rgb, "字体色");
        }

        float ry = top + CARD_PAD + 22 + CARD_PAD;
        for (int k = 0; k < groupSliderCount[g]; k++) {
            drawSliderRow(canvas, mx, my, cx + CARD_PAD, cw - CARD_PAD * 2, ry, groupSliderStart[g] + k);
            ry += ROW_H;
        }

        if (groupNames[g].equals(G_SMTC)) {
            float apiBoxY = ry + 4;
            float rx = cx + CARD_PAD, rw = cw - CARD_PAD * 2;

            SkijaRenderer.text(canvas, FB, 10, rx + 8, apiBoxY + 4, COLOR_ON_SURFACE, false, "自定义 API URL (占位符: %title% 和 %artist%)");

            float boxY = apiBoxY + 16, boxH = 26;
            SkijaRenderer.roundedRect(canvas, rx + 8, boxY, rw - 16, boxH, 6, apiFieldFocused ? COLOR_SURFACE_HIGH : 0x18FFFFFF);
            SkijaRenderer.roundedRectStroke(canvas, rx + 8, boxY, rw - 16, boxH, 6, 1, apiFieldFocused ? COLOR_PRIMARY : COLOR_OUTLINE_VAR);

            String text = ModConfig.get().smtcCustomApiUrl;
            if (text.length() > 60) text = "..." + text.substring(text.length() - 57);
            if (apiFieldFocused && (System.currentTimeMillis() / 500) % 2 == 0) text += "|";

            SkijaRenderer.text(canvas, F, 9, rx + 16, boxY + boxH / 2f, COLOR_ON_SURFACE, false, text);
        }
        canvas.restore();
    }

    private void drawInlineColorChip(Canvas canvas, float x, float y, float w, float h, int rgb, String label) {
        SkijaRenderer.roundedRect(canvas, x, y, w, h, h / 2, 0xFF000000 | rgb);
        SkijaRenderer.roundedRectStroke(canvas, x + 0.5f, y + 0.5f, w - 1, h - 1, h / 2, 0.8f, 0x60FFFFFF);
        // Small label to the left of the chip
        float lw = SkijaRenderer.textWidth(F, 8, label);
        SkijaRenderer.text(canvas, F, 8, x - lw - 4, y + h / 2f, COLOR_ON_SURF_VAR, false, label);
    }

    private void drawSliderRow(Canvas canvas, int mx, int my, float rx, float rw, float ry, int idx) {
        SliderDef sd = SLIDERS[idx];
        float val = sd.get().get();
        float pct = Math.max(0, Math.min(1, (val - sd.min()) / (sd.max() - sd.min())));
        boolean active = activeSlider == idx;
        boolean hov = inBounds(mx, my, rx, ry, rw, ROW_H);
        if (hov || active) SkijaRenderer.roundedRect(canvas, rx, ry + 1, rw, ROW_H - 2, 8, 0x10FFFFFF);

        // Label (left, vertically centered in upper half)
        SkijaRenderer.text(canvas, active ? FB : F, 10.5f, rx + 8, ry + 13, active ? COLOR_PRIMARY : COLOR_ON_SURFACE, false, sd.label());

        // Value chip (right-aligned, upper half)
        String valStr = sd.isInt() ? String.valueOf(Math.round(val)) :
                        String.format("%.2f", val) + sd.unit();
        float vw = SkijaRenderer.textWidth(FB, 10, valStr);
        // Value background chip
        SkijaRenderer.roundedRect(canvas, rx + rw - vw - 14, ry + 4, vw + 10, 18, 9,
            active ? COLOR_PRIMARY_CONT : 0x18FFFFFF);
        SkijaRenderer.text(canvas, FB, 10, rx + rw - vw / 2 - 9, ry + 13,
            active ? COLOR_PRIMARY : COLOR_ON_SURF_VAR, true, valStr);

        // Track (lower half of row)
        float tx = rx + 8, ty = ry + ROW_H - 14, tw = rw - 16, fw = tw * pct;
        // Track bg
        SkijaRenderer.roundedRect(canvas, tx, ty, tw, TRACK_H, TRACK_H / 2, COLOR_PRIMARY_CONT);
        // Track fill
        if (fw > TRACK_H) SkijaRenderer.roundedRect(canvas, tx, ty, fw, TRACK_H, TRACK_H / 2,
            active ? COLOR_PRIMARY : COLOR_PRIMARY_DIM);
        // Thumb: taller pill
        float thumbX = tx + fw - 6;
        int thumbCol = active ? 0xFFFFFFFF : COLOR_ON_SURFACE;
        SkijaRenderer.roundedRect(canvas, thumbX, ty - 4, 12, TRACK_H + 8, 6, thumbCol);
    }

    // ── Card shared title ─────────────────────────────────────────────────────

    private void drawCardTitle(Canvas canvas, float cx, float top, String title) {
        float ty = top + CARD_PAD + 11;
        // Small colored left-accent line
        SkijaRenderer.roundedRect(canvas, cx + CARD_PAD, ty - 7, 2, 14, 1, COLOR_PRIMARY_DIM);
        SkijaRenderer.textSpaced(canvas, FB, 9, 1.5f, cx + CARD_PAD + 7, ty, COLOR_PRIMARY, false, title);
        SkijaRenderer.roundedRect(canvas, cx + CARD_PAD, top + CARD_PAD + 20, PANEL_W - PAD * 2 - CARD_PAD * 2, 0.8f, 0.4f, COLOR_OUTLINE_VAR);
    }

    // ── Scrollbar ─────────────────────────────────────────────────────────────

    private void drawScrollbar(Canvas canvas) {
        float totalContent = ySliderCardStart[numGroups - 1] + sliderCardH(numGroups - 1) + CARD_GAP;
        if (totalContent <= CONTENT_H) return;
        float frac = Math.min(1, scrollY / (totalContent - CONTENT_H));
        float barH = Math.max(28, CONTENT_H * (CONTENT_H / totalContent));
        float barY = panelY + HDR_H + frac * (CONTENT_H - barH);
        SkijaRenderer.roundedRect(canvas, panelX + PANEL_W - 5, barY + 2, 3, barH - 4, 1.5f, 0x50FFFFFF);
    }

    // ── Bottom bar ────────────────────────────────────────────────────────────

    private void drawBottomBar(Canvas canvas, int mx, int my) {
        float by = panelY + PANEL_H - BTN_H;
        SkijaRenderer.roundedRect(canvas, panelX + PAD, by, PANEL_W - PAD * 2, 0.8f, 0.4f, COLOR_OUTLINE_VAR);

        // Reset — ghost button
        float rw = 90, rh = 26, rx = panelX + PAD, ry = by + (BTN_H - rh) / 2f;
        boolean hRst = inBounds(mx, my, rx, ry, rw, rh);
        if (hRst) SkijaRenderer.roundedRect(canvas, rx, ry, rw, rh, 13, 0x20FFFFFF);
        SkijaRenderer.roundedRectStroke(canvas, rx + 0.5f, ry + 0.5f, rw - 1, rh - 1, 13, 0.8f, COLOR_OUTLINE_VAR);
        SkijaRenderer.textSpaced(canvas, F, 10, 0.5f, rx + rw / 2, ry + rh / 2, COLOR_ON_SURF_VAR, true, "RESET");

        // Save — filled pill
        float sw = 120, sh = 26, sx = panelX + PANEL_W - PAD - sw, sy = by + (BTN_H - sh) / 2f;
        boolean hSav = inBounds(mx, my, sx, sy, sw, sh);
        SkijaRenderer.roundedRect(canvas, sx, sy, sw, sh, 13, hSav ? 0xFFBBAEEE : COLOR_PRIMARY);
        SkijaRenderer.textSpaced(canvas, FB, 10, 0.5f, sx + sw / 2, sy + sh / 2, 0xFF1C1B1F, true, "SAVE & CLOSE");
    }

    // ── Color swatch ──────────────────────────────────────────────────────────

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        float scx = this.width / 2f, scy = this.height / 2f;
        float fx = scx + (float) (mx - scx) / UI_SCALE;
        float fy = scy + (float) (my - scy) / UI_SCALE;

        // Reset focus unless API box is clicked later
        apiFieldFocused = false;

        // 1. Only handle clicks inside the panel
        if (!inBounds(fx, fy, panelX, panelY, PANEL_W, PANEL_H)) {
            return false;
        }

        // 2. Title Bar (Header) Area
        if (fy < panelY + HDR_H) {
            // Close button check
            float cx = panelX + PANEL_W - PAD - 18, cy = panelY + HDR_H / 2f - 9;
            if (inBounds(fx, fy, cx, cy, 18, 18)) {
                saveAndClose();
                return true;
            }

            // Start dragging the whole panel
            dragging = true;
            dragOffsetX = fx - panelX;
            dragOffsetY = fy - panelY;
            return true;
        }

        // 3. Bottom Bar Area
        float by = panelY + PANEL_H - BTN_H;
        if (fy >= by) {
            float sh = 26, sy = by + (BTN_H - sh) / 2f;
            float sw = 120, ssx = panelX + PANEL_W - PAD - sw;
            if (inBounds(fx, fy, ssx, sy, sw, sh)) { saveAndClose(); return true; }
            float rw = 90, ry = sy, rx = panelX + PAD;
            if (inBounds(fx, fy, rx, ry, rw, sh)) { resetToDefaults(); return true; }
            return true;
        }

        // 4. Content Area
        float contentTop = panelY + HDR_H;
        // Scrollbar drag
        float totalContent = ySliderCardStart[numGroups - 1] + sliderCardH(numGroups - 1) + CARD_GAP;
        if (totalContent > CONTENT_H) {
            float barAreaX = panelX + PANEL_W - 15, barAreaW = 15;
            if (inBounds(fx, fy, barAreaX, contentTop, barAreaW, CONTENT_H)) {
                draggingScroll = true;
                updateScrollFromMouse(fy);
                return true;
            }
        }

        float oy = contentTop - scrollY;
        float cx2 = panelX + PAD, cw = PANEL_W - PAD * 2;

        // Toggles
        float tby = oy + yToggleCard + CARD_PAD + 22 + CARD_PAD;
        for (int i = 0; i < TOGGLES.length; i++) {
            float rrx = cx2 + CARD_PAD, rrw = cw - CARD_PAD * 2;
            if (inBounds(fx, fy, rrx, tby + i * TOGGLE_H, rrw, TOGGLE_H)) {
                TOGGLES[i].set().accept(!TOGGLES[i].get().get()); return true;
            }
        }

        // Font style
        float fsY = oy + yFsCard + CARD_PAD + 22 + CARD_PAD;
        float bW = (cw - CARD_PAD * 2 - 8 * 2) / 3f, bH = 32, bX = cx2 + CARD_PAD, bY = fsY + 8;
        for (int i = 0; i < FS_LABELS.length; i++) {
            float x = bX + i * (bW + 8);
            if (inBounds(fx, fy, x, bY, bW, bH)) { ModConfig.get().fontStyle = i; return true; }
        }

        // Sliders
        for (int g = 0; g < numGroups; g++) {
            float sly = oy + ySliderCardStart[g] + CARD_PAD + 22 + CARD_PAD;
            float rrx = cx2 + CARD_PAD, rrw = cw - CARD_PAD * 2;
            float ttx = rrx + 8, ttw = rrw - 16;
            for (int k = 0; k < groupSliderCount[g]; k++) {
                float rry = sly + k * ROW_H;
                if (inBounds(fx, fy, ttx - 8, rry, ttw + 16, ROW_H)) {
                    activeSlider = groupSliderStart[g] + k;
                    applySlider(activeSlider, fx, ttx, ttw); return true;
                }
            }
            if (groupNames[g].equals(G_SMTC)) {
                float apiBoxY = oy + ySliderCardStart[g] + CARD_PAD + 22 + CARD_PAD + groupSliderCount[g] * ROW_H;
                if (inBounds(fx, fy, cx2 + CARD_PAD + 8, apiBoxY + 16, cw - CARD_PAD * 2 - 16, 26)) {
                    apiFieldFocused = true; return true;
                }
            }
        }

        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        float scx = this.width / 2f, scy = this.height / 2f;
        float fx = scx + (float) (mx - scx) / UI_SCALE;
        float fy = scy + (float) (my - scy) / UI_SCALE;

        if (dragging) {
            panelX = fx - dragOffsetX;
            panelY = fy - dragOffsetY;
            return true;
        }
        if (draggingScroll) {
            updateScrollFromMouse(fy);
            return true;
        }
        if (activeSlider >= 0) {
            float cx = panelX + PAD, cw = PANEL_W - PAD * 2;
            float rrx = cx + CARD_PAD, rrw = cw - CARD_PAD * 2;
            float ttx = rrx + 8, ttw = rrw - 16;
            applySlider(activeSlider, fx, ttx, ttw); return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false; draggingScroll = false; activeSlider = -1;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hScroll, double vScroll) {
        float total = ySliderCardStart[numGroups - 1] + sliderCardH(numGroups - 1) + CARD_GAP;
        scrollY = Math.max(0, Math.min(Math.max(0, total - CONTENT_H), scrollY - (float)(vScroll * 16)));
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (apiFieldFocused) {
            ModConfig.get().smtcCustomApiUrl += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (apiFieldFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !ModConfig.get().smtcCustomApiUrl.isEmpty()) {
                ModConfig.get().smtcCustomApiUrl = ModConfig.get().smtcCustomApiUrl.substring(0, ModConfig.get().smtcCustomApiUrl.length() - 1);
            } else if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                String clipboard = net.minecraft.client.MinecraftClient.getInstance().keyboard.getClipboard();
                if (clipboard != null) ModConfig.get().smtcCustomApiUrl += clipboard;
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                apiFieldFocused = false;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { saveAndClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean inBounds(float px, float py, float x, float y, float w, float h) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }

    private void updateScrollFromMouse(float fy) {
        float totalContent = ySliderCardStart[numGroups - 1] + sliderCardH(numGroups - 1) + CARD_GAP;
        float barH = Math.max(28, CONTENT_H * (CONTENT_H / totalContent));
        float trackTop = panelY + HDR_H;
        float relY = fy - trackTop - barH / 2f;
        float frac = Math.max(0, Math.min(1, relY / (CONTENT_H - barH)));
        scrollY = frac * (totalContent - CONTENT_H);
    }

    private void applySlider(int idx, float mx, float tx, float tw) {
        SliderDef sd = SLIDERS[idx];
        float pct = Math.max(0, Math.min(1, (mx - tx) / tw));
        sd.set().accept(sd.min() + pct * (sd.max() - sd.min()));
    }

    private void saveAndClose() { ModConfig.save(); this.close(); }

    private void resetToDefaults() {
        ModConfig c = ModConfig.get();
        c.scale = 1.0; c.enabled = true; c.customTablistEnabled = true; c.killNotificationEnabled = true;
        c.showServerAddress = true; c.showLatency = true; c.showRoleId = true;
        c.showPlayTime = true; c.showCooldownBar = true;
        c.relativeX = -1.0; c.relativeY = 0.05;
        c.killNotifX = 0.98f; c.killNotifY = 0.08f; c.killNotifFontSize = 12.0f; c.killNotifDuration = 2500;
        c.backgroundHue = 233f; c.backgroundSaturation = 0f; c.backgroundBrightness = 0.69f;
        c.fontHue = 210f; c.fontSaturation = 0.02f; c.fontBrightness = 1f; c.fontScale = 1f;
        c.tabBackgroundHue = 0f; c.tabBackgroundSaturation = 0f; c.tabBackgroundBrightness = 1f;
        c.tabFontHue = 255f; c.tabFontSaturation = 0.13f; c.tabFontBrightness = 0.12f;
        c.fontStyle = 0; c.acrylicOpacity = 195;
        c.smtcCustomApiEnabled = false;
        c.smtcLocalApiEnabled = false;
        c.smtcBuiltinApiEnabled = true;
        c.smtcCustomApiUrl = "https://api.lrc.cx/lyrics?title=%title%&artist=%artist%";
        c.smtcLyricOffset = 0;
        c.smtcLyricAdvance = 0;
        c.smtcLyricLines = 3;
        c.smtcMaxLyricWidth = 200;
        c.smtcMinLyricWidth = 100;
    }
}
