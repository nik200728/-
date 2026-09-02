package dev.nikita.tgvoice.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Owns the addon recording input without replacing or modifying Plasmo Voice input. */
public final class VoiceMessageInput {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("tgvoice", "voice_messages")
    );

    /** Hold left mouse button to record; release it to send. */
    private static final KeyMapping RECORD_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.tgvoice.record",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_LEFT,
            CATEGORY
    ));

    /** Right mouse button cancels the current recording. */
    private static final KeyMapping CANCEL_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.tgvoice.cancel",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT,
            CATEGORY
    ));

    private static final VoiceMessageInput INSTANCE = new VoiceMessageInput();
    private boolean registered;

    private final VoiceMessageKeyState ptt = new VoiceMessageKeyState();
    private boolean toggle;

    private VoiceMessageInput() {}

    public static void register() {
        if (INSTANCE.registered) return;
        INSTANCE.registered = true;
        INSTANCE.setToggle(VoiceMessageConfig.get().toggleMode);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Do not start/cancel recordings while a screen is open. This also prevents
            // the message controls from hijacking normal inventory/menu mouse input.
            if (client.screen != null) {
                if (INSTANCE.ptt.isHeld()) INSTANCE.cancel();
                return;
            }

            while (RECORD_KEY.consumeClick()) INSTANCE.press();
            if (!INSTANCE.toggle && !RECORD_KEY.isDown() && INSTANCE.ptt.isHeld()) INSTANCE.release();
            while (CANCEL_KEY.consumeClick()) INSTANCE.cancel();

            // The recording gesture deliberately uses the normal LMB binding, so keep
            // Minecraft's attack action from firing while a voice message is active.
            // Outside a recording the vanilla attack binding is untouched.
            if (VoiceMessageClient.getInstance().isRecording()) {
                client.options.keyAttack.setDown(false);
            }
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
        VoiceMessageConfig config = VoiceMessageConfig.get();
        if (config.toggleMode != toggle) {
            config.toggleMode = toggle;
            config.save();
        }
        if (toggle && ptt.isHeld()) ptt.cancel();
    }

    public boolean isToggle() {
        return toggle;
    }
}
