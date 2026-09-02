package dev.nikita.tgvoice.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Opens the standalone voice-message UI without replacing or modifying Plasmo Voice screens. */
public final class VoiceMessageUiController {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("tgvoice", "voice_messages")
    );

    private static final KeyMapping OPEN_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.tgvoice.open_messages",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    ));

    private static final KeyMapping SETTINGS_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.tgvoice.settings",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY
    ));

    private static boolean registered;

    private VoiceMessageUiController() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_KEY.consumeClick()) openMessages(client);
            while (SETTINGS_KEY.consumeClick()) openSettings(client);
        });
    }

    private static void openMessages(Minecraft client) {
        if (client.screen != null || VoiceMessagePlaybackManager.messageIds().isEmpty()) return;
        client.setScreen(new VoiceMessageScreen());
    }

    private static void openSettings(Minecraft client) {
        if (client.screen != null) return;
        client.setScreen(new VoiceMessageSettingsScreen());
    }
}
