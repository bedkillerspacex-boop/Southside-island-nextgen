package com.southside.dynamicisland;

import com.southside.dynamicisland.config.ModConfig;
import com.southside.dynamicisland.input.KeyInputHandler;
import com.southside.dynamicisland.render.IslandRenderer;
import com.southside.dynamicisland.system.LocalLrcServer;
import com.southside.dynamicisland.system.ScoreboardCountdownTracker;
import com.southside.dynamicisland.system.LyricAutoOffset;
import dsj.smtc.SmtcLoader;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class DynamicIslandMod implements ClientModInitializer {

    public static final String MOD_ID = "dynamic-island";
    private static IslandRenderer islandRenderer;
    public static boolean ksApiHooked = false;
    private static java.lang.reflect.Method getCooldownMethod;
    private static java.lang.reflect.Method isGufaMethod;
    private static java.lang.reflect.Method isVictoryMethod;
    private static java.lang.reflect.Method isDeadMethod;

    private int smtcTick = 0;

    @Override
    public void onInitializeClient() {
        ModConfig.load();
        LocalLrcServer.start();
        KeyInputHandler.register();
        
        // Start periodic ping if auto offset is enabled
        if (ModConfig.get().smtcAutoOffset) {
            LyricAutoOffset.startAutoOffsetScheduler();
        }

        islandRenderer = new IslandRenderer();

        // Register KillsayEvents listeners using reflection to avoid compile-time dependency
        try {
            Class<?> eventsClass = Class.forName("mojang.minecraft.uuidget.KillsayEvents");
            
            // Cache query methods
            getCooldownMethod = eventsClass.getMethod("getCooldownRemainingMs");
            isGufaMethod = eventsClass.getMethod("isGufaPending");
            isVictoryMethod = eventsClass.getMethod("isVictory");
            isDeadMethod = eventsClass.getMethod("isDeadPause");

            // Hook UserKill (Legacy/Simple)
            try {
                Class<?> userKillInterface = Class.forName("mojang.minecraft.uuidget.KillsayEvents$UserKill");
                java.lang.reflect.Method registerUserKill = eventsClass.getMethod("registerUserKill", userKillInterface);
                Object userKillProxy = java.lang.reflect.Proxy.newProxyInstance(
                    userKillInterface.getClassLoader(),
                    new Class<?>[]{userKillInterface},
                    (proxy, method, args) -> {
                        if (method.getName().equals("onUserKill") && args.length > 0 && args[0] instanceof String victimName) {
                            if (islandRenderer != null) islandRenderer.onKill(victimName);
                        }
                        return null;
                    }
                );
                registerUserKill.invoke(null, userKillProxy);
            } catch (Exception ignored) {}

            // Hook KillStatusListener (New/Detailed)
            try {
                Class<?> statusListenerInterface = Class.forName("mojang.minecraft.uuidget.KillsayEvents$KillStatusListener");
                java.lang.reflect.Method registerStatus = eventsClass.getMethod("registerStatusListener", statusListenerInterface);
                Object statusProxy = java.lang.reflect.Proxy.newProxyInstance(
                    statusListenerInterface.getClassLoader(),
                    new Class<?>[]{statusListenerInterface},
                    (proxy, method, args) -> {
                        if (islandRenderer == null) return null;
                        switch (method.getName()) {
                            case "onCooldownStart" -> {
                                if (args.length > 0 && args[0] instanceof Long cd) islandRenderer.onCooldownStart(cd);
                            }
                            case "onDone" -> {
                                if (args.length > 0 && args[0] instanceof String name) islandRenderer.onKillDone(name);
                            }
                            case "onDeath" -> islandRenderer.onDeath();
                            case "onVictory" -> islandRenderer.onVictory();
                        }
                        return null;
                    }
                );
                registerStatus.invoke(null, statusProxy);
            } catch (Exception ignored) {}

            ksApiHooked = true;
            System.out.println("[Dynamic Island] Successfully hooked into latest KillsayEvents via reflection.");
        } catch (ClassNotFoundException e) {
            System.out.println("[Dynamic Island] Killsay-reborn not found, skipping kill event hook.");
        } catch (Exception e) {
            System.err.println("[Dynamic Island] Failed to hook KillsayEvents: " + e.getMessage());
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (islandRenderer != null) {
                islandRenderer.setCountdownSeconds(ScoreboardCountdownTracker.getCountdownSeconds(client));
            }
            if (ksApiHooked && islandRenderer != null) {
                try {
                    long cd = (long) getCooldownMethod.invoke(null);
                    boolean gufa = (boolean) isGufaMethod.invoke(null);
                    boolean victory = (boolean) isVictoryMethod.invoke(null);
                    boolean dead = (boolean) isDeadMethod.invoke(null);
                    islandRenderer.updateKillsayState(cd, gufa, victory, dead);
                } catch (Exception ignored) {}
            }

            // SMTC Polling
            if (ModConfig.get().smtcEnabled && SmtcLoader.isLoaded()) {
                smtcTick++;
                if (smtcTick >= 10) {
                    smtcTick = 0;
                    if (islandRenderer != null) {
                        islandRenderer.updateSmtc(SmtcLoader.getCachedInfo());
                    }
                }
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (islandRenderer != null) {
                islandRenderer.render(drawContext);
            }
        });
    }

    public static IslandRenderer getIslandRenderer() {
        return islandRenderer;
    }
}
