package dsj.smtc;

import java.io.*;
import java.util.concurrent.atomic.AtomicReference;

public class SmtcLoader {
    private static boolean loaded = false;
    private static final AtomicReference<String> cachedInfo = new AtomicReference<>("");

    static {
        try {
            String dll = "/natives/smtc.dll";
            InputStream in = SmtcLoader.class.getResourceAsStream(dll);

            if (in == null) {
                System.err.println("[Dynamic Island] SMTC DLL not found: " + dll);
            } else {
                // Use .minecraft/dynamic-island/natives as a persistent cache
                File gameDir = net.minecraft.client.MinecraftClient.getInstance().runDirectory;
                File nativesDir = new File(gameDir, "dynamic-island/natives");
                if (!nativesDir.exists()) {
                    nativesDir.mkdirs();
                }
                
                File cachedDll = new File(nativesDir, "smtc.dll");
                
                // Only extract if it doesn't exist to prevent leakage
                if (!cachedDll.exists()) {
                    try (OutputStream out = new FileOutputStream(cachedDll)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                }
                
                System.load(cachedDll.getAbsolutePath());
                loaded = true;
                System.out.println("[Dynamic Island] SMTC native library loaded successfully.");

                // Dedicated STA-compatible polling thread
                Thread poller = new Thread(() -> {
                    while (true) {
                        try {
                            String info = getSmtcInfo();
                            cachedInfo.set(info != null ? info : "");
                        } catch (Exception e) {
                            cachedInfo.set("");
                        }
                        try { Thread.sleep(500); } catch (InterruptedException e) { break; }
                    }
                }, "smtc-poller");
                poller.setDaemon(true);
                poller.start();
            }
        } catch (Throwable e) {
            System.err.println("[Dynamic Island] Failed to load SMTC DLL: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static boolean isLoaded() { return loaded; }

    public static String getCachedInfo() { return cachedInfo.get(); }

    public static native String getSmtcInfo();
}
