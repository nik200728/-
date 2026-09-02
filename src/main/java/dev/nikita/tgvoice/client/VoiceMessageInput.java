package dev.nikita.tgvoice.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Owns the addon recording input without replacing or modifying Plasmo Voice input. */
public final class VoiceMessageInput {
    private static final KeyMapping RECORD_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.tgvoice.record",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("tgvoice", "voice_messages"))
    ));

    private static final KeyMapping CANCEL_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.tgvoice.cancel",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_ESCAPE,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("tgvoice", "voice_messages_cancel"))
    ));

    private static final VoiceMessageInput INSTANCE = new VoiceMessageInput();
    private boolean registered;

    private final VoiceMessageKeyState ptt = new VoiceMessageKeyState();
    private boolean toggle;

    private VoiceMessageInput() {}

    public static void register() {
        if (INSTANCE.registered) return;
        INSTANCE.registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (RECORD_KEY.consumeClick()) INSTANCE.press();
            if (!INSTANCE.toggle && !RECORD_KEY.isDown() && INSTANCE.ptt.isHeld()) INSTANCE.release();
            while (CANCEL_KEY.consumeClick()) INSTANCE.cancel();
        });
    }

    public void press() {
        if (toggle) {
            if (VoiceMessageClient.getInstance().isRecording()) {
                VoiceMessageClient.getInstance().finishRecording();
            } else {
                VoiceMessageClient.getInstance().startRecording();
            }
            return;
        }
        ptt.press();
    }

    public void release() {
        if (!toggle) ptt.release();
    }

    public void cancel() {
        ptt.cancel();
        VoiceMessageClient.getInstance().cancelRecording();
    }

    public void setToggle(boolean toggle) {
        this.toggle = toggle;
        if (toggle && ptt.isHeld()) ptt.cancel();
    }

    public boolean isToggle() { return toggle; }
}
