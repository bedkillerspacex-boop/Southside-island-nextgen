package com.southside.dynamicisland.input;

import com.southside.dynamicisland.screen.IslandSettingsScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class KeyInputHandler {

    private static KeyBinding openSettingsKey;

    public static void register() {
        openSettingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.dynamic-island.settings",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_GRAVE_ACCENT,
            "category.dynamic-island"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSettingsKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new IslandSettingsScreen());
                }
            }
        });
    }
}
