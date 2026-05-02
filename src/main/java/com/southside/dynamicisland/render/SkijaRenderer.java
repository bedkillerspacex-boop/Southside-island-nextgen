package com.southside.dynamicisland.render;

import net.minecraft.client.MinecraftClient;
import org.jetbrains.skia.*;
import org.jetbrains.skia.Canvas;
import org.jetbrains.skia.Font;
import org.jetbrains.skia.Paint;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 Skija (Skia for Java) 的渲染器实现
 * 适配 Skiko 0.8.12，使用 FontMgr 进行字体管理
 */
public final class SkijaRenderer {

    private static DirectContext context;
    private static Surface surface;
    private static Canvas canvas;
    
    private static final Map<String, Typeface> typefaces = new HashMap<>();
    private static final Map<Integer, Image> imageCache = new HashMap<>();

    private static int lastWidth, lastHeight;
    private static boolean initialized = false;

    private static final String REGULAR_FONT_RESOURCE = "/assets/dynamic-island/fonts/GoogleSans-Regular.ttf";
    private static final String BOLD_FONT_RESOURCE = "/assets/dynamic-island/fonts/GoogleSans-Bold.ttf";

    private SkijaRenderer() {}

    public static Canvas beginFrame() {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getFramebufferWidth();
        int height = client.getWindow().getFramebufferHeight();
        float scaleFactor = (float) client.getWindow().getScaleFactor();

        ensureInitialized();

        if (width != lastWidth || height != lastHeight || surface == null) {
            if (surface != null) surface.close();
            
            int fbId = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            BackendRenderTarget renderTarget = BackendRenderTarget.Companion.makeGL(
                    width, height, 0, 8, fbId, FramebufferFormat.GR_GL_RGBA8
            );
            
            surface = Surface.Companion.makeFromBackendRenderTarget(
                    context, renderTarget, SurfaceOrigin.BOTTOM_LEFT,
                    SurfaceColorFormat.RGBA_8888, ColorSpace.Companion.getSRGB(),
                    new SurfaceProps()
            );
            
            if (surface == null) {
                throw new IllegalStateException("无法创建 Skija Surface");
            }
            
            canvas = surface.getCanvas();
            lastWidth = width;
            lastHeight = height;
        }

        canvas.save();
        canvas.scale(scaleFactor, scaleFactor);
        return canvas;
    }

    public static void endFrame() {
        if (canvas != null) {
            canvas.restore();
            context.flush();
        }
        restoreOpenGLState();
    }

    private static void ensureInitialized() {
        if (initialized) return;

        if (context == null) {
            context = DirectContext.Companion.makeGL();
        }

        loadFonts();
        initialized = true;
    }

    private static void loadFonts() {
        Typeface regular = loadTypeface(REGULAR_FONT_RESOURCE, "GoogleSans-Regular");
        Typeface bold = loadTypeface(BOLD_FONT_RESOURCE, "GoogleSans-Bold");

        if (regular != null) typefaces.put("ui", regular);
        if (bold != null) typefaces.put("ui-bold", bold);

        Path cjkPath = findFirstExistingFont(List.of(
                "C:\\Windows\\Fonts\\msyh.ttc",
                "C:\\Windows\\Fonts\\msyh.ttf",
                "C:\\Windows\\Fonts\\simhei.ttf",
                "C:\\Windows\\Fonts\\STXIHEI.TTF"
        ));
        
        if (cjkPath != null) {
            Typeface cjk = FontMgr.Companion.getDefault().makeFromFile(cjkPath.toString(), 0);
            typefaces.put("ui-cjk", cjk);
        }
    }

    private static Typeface loadTypeface(String resourcePath, String name) {
        try {
            Path gameDir = MinecraftClient.getInstance().runDirectory.toPath();
            Path cacheDir = gameDir.resolve("dynamic-island").resolve("assets");
            if (!Files.exists(cacheDir)) Files.createDirectories(cacheDir);

            String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            Path cachedFile = cacheDir.resolve(fileName);

            if (!Files.exists(cachedFile)) {
                try (var input = SkijaRenderer.class.getResourceAsStream(resourcePath)) {
                    if (input != null) Files.copy(input, cachedFile, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            
            return FontMgr.Companion.getDefault().makeFromFile(cachedFile.toString(), 0);
        } catch (Exception e) {
            return null;
        }
    }

    private static Path findFirstExistingFont(List<String> candidates) {
        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (Files.exists(path)) return path;
        }
        return null;
    }

    public static void roundedRect(Canvas canvas, float x, float y, float w, float h, float r, int color) {
        try (Paint paint = new Paint()) {
            paint.setColor(color);
            paint.setAntiAlias(true);
            canvas.drawRRect(RRect.Companion.makeXYWH(x, y, w, h, r), paint);
        }
    }

    public static void roundedRectGradient(Canvas canvas, float x, float y, float w, float h, float r, int color1, int color2) {
        try (Paint paint = new Paint()) {
            paint.setAntiAlias(true);
            paint.setShader(Shader.Companion.makeLinearGradient(
                    new Point(x, y), new Point(x, y + h),
                    new int[]{color1, color2}
            ));
            canvas.drawRRect(RRect.Companion.makeXYWH(x, y, w, h, r), paint);
        }
    }

    public static void roundedRectStroke(Canvas canvas, float x, float y, float w, float h, float r, float strokeWidth, int color) {
        try (Paint paint = new Paint()) {
            paint.setColor(color);
            paint.setAntiAlias(true);
            paint.setStroke(true);
            paint.setStrokeWidth(strokeWidth);
            canvas.drawRRect(RRect.Companion.makeXYWH(x, y, w, h, r), paint);
        }
    }

    public static void line(Canvas canvas, float x1, float y1, float x2, float y2, float width, int color) {
        try (Paint paint = new Paint()) {
            paint.setColor(color);
            paint.setAntiAlias(true);
            paint.setStrokeWidth(width);
            canvas.drawLine(x1, y1, x2, y2, paint);
        }
    }

    private static Typeface getDefaultTypeface() {
        return FontMgr.Companion.getDefault().matchFamilyStyle(null, FontStyle.Companion.getNORMAL());
    }

    public static void text(Canvas canvas, String fontName, float size, float x, float y, int color, boolean centered, String text) {
        Typeface tf = typefaces.get(fontName);
        if (tf == null) tf = getDefaultTypeface();
        
        try (Font font = new Font(tf, size);
             Paint paint = new Paint()) {
            paint.setColor(color);
            paint.setAntiAlias(true);
            
            float width = font.measureTextWidth(text);
            float renderX = centered ? x - width / 2f : x;
            FontMetrics metrics = font.getMetrics();
            float renderY = y - (metrics.getAscent() + metrics.getDescent()) / 2f;
            
            canvas.drawString(text, renderX, renderY, font, paint);
        }
    }

    public static void textSpaced(Canvas canvas, String fontName, float size, float spacing, float x, float y, int color, boolean centered, String text) {
        Typeface tf = typefaces.get(fontName);
        if (tf == null) tf = getDefaultTypeface();

        try (Font font = new Font(tf, size);
             Paint paint = new Paint()) {
            paint.setColor(color);
            paint.setAntiAlias(true);
            
            float currentX = x;
            if (centered) {
                float totalW = font.measureTextWidth(text) + (text.length() - 1) * spacing;
                currentX -= totalW / 2f;
            }
            
            FontMetrics metrics = font.getMetrics();
            float renderY = y - (metrics.getAscent() + metrics.getDescent()) / 2f;
            
            for (int i = 0; i < text.length(); i++) {
                String c = String.valueOf(text.charAt(i));
                canvas.drawString(c, currentX, renderY, font, paint);
                currentX += font.measureTextWidth(c) + spacing;
            }
        }
    }

    public static float textWidth(String fontName, float size, String text) {
        Typeface tf = typefaces.get(fontName);
        if (tf == null) tf = getDefaultTypeface();
        try (Font font = new Font(tf, size)) {
            return font.measureTextWidth(text);
        }
    }

    public static int createImage(byte[] data) {
        Image image = Image.Companion.makeFromEncoded(data);
        if (image == null) return -1;
        int id = image.hashCode();
        imageCache.put(id, image);
        return id;
    }

    public static void deleteImage(int id) {
        Image image = imageCache.remove(id);
        if (image != null) image.close();
    }

    public static void imageRect(Canvas canvas, float x, float y, float w, float h, float r, int imageId, float alpha) {
        Image image = imageCache.get(imageId);
        if (image == null) return;

        canvas.save();
        canvas.clipRRect(RRect.Companion.makeXYWH(x, y, w, h, r), true);
        try (Paint paint = new Paint()) {
            paint.setAlphaf(alpha);
            canvas.drawImageRect(image, Rect.makeXYWH(x, y, w, h), paint);
        }
        canvas.restore();
    }

    private static void restoreOpenGLState() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
    }
}
