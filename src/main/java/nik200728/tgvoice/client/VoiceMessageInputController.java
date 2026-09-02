package nik200728.tgvoice.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nikita.tgvoice.client.VoiceMessageClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side Telegram-style push-to-talk input. It owns only its own key mapping
 * and never changes Plasmo Voice activation state.
 */
public final class VoiceMessageInputController {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(TelegramVoiceClient.MOD_ID, "voice_messages")
    );

    private static final KeyMapping RECORD_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.tgvoice.record",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_V,
                    CATEGORY
            )
    );

    private static boolean wasHeld;
    private static boolean rightWasDown;

    private VoiceMessageInputController() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean held = RECORD_KEY.isDown();
            if (held && !wasHeld) {
                VoiceMessageClient.getInstance().startRecording();
            } else if (!held && wasHeld) {
                VoiceMessageClient.getInstance().finishRecording();
            }
            wasHeld = held;

            if (client.getWindow() != null) {
                boolean rightDown = GLFW.glfwGetMouseButton(
                        client.getWindow().handle(),
                        GLFW.GLFW_MOUSE_BUTTON_RIGHT
                ) == GLFW.GLFW_PRESS;
                if (rightDown && !rightWasDown && VoiceMessageClient.getInstance().isRecording()) {
                    VoiceMessageClient.getInstance().cancelRecording();
                }
                rightWasDown = rightDown;
            }

            VoiceMessageClient.getInstance().enforceMaximumDuration();
        });
    }
}
