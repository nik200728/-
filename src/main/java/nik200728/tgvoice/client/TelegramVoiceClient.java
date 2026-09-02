package nik200728.tgvoice.client;

import dev.nikita.tgvoice.client.PlasmoVoiceClientAddon;
import dev.nikita.tgvoice.client.VoiceMessageInput;
import dev.nikita.tgvoice.client.VoiceMessagePlaybackManager;
import dev.nikita.tgvoice.client.VoiceMessageUiController;
import net.fabricmc.api.ClientModInitializer;
import su.plo.voice.api.client.PlasmoVoiceClient;

/** Fabric client bootstrap for the Voice Messages addon. */
public final class TelegramVoiceClient implements ClientModInitializer {
    public static final String MOD_ID = "tgvoice";

    @Override
    public void onInitializeClient() {
        // Plasmo Voice owns the microphone lifecycle. We only observe its processed
        // capture event while a Voice Message is being recorded.
        PlasmoVoiceClient.getAddonsLoader().load(new PlasmoVoiceClientAddon());
        VoiceMessageInput.register();
        VoiceMessagePlaybackManager.register();
        VoiceMessageUiController.register();
    }
}
