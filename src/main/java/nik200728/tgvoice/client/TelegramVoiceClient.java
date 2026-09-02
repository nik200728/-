package nik200728.tgvoice.client;

import dev.nikita.tgvoice.client.PlasmoVoiceClientAddon;
import dev.nikita.tgvoice.client.VoiceMessageClient;
import dev.nikita.tgvoice.client.VoiceMessageInput;
import dev.nikita.tgvoice.client.VoiceMessagePlaybackManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
        dev.nikita.tgvoice.client.VoiceMessageUiController.register();

        // Hard-stop at the protocol limit even if the user keeps the record button held.
        ClientTickEvents.END_CLIENT_TICK.register(client -> VoiceMessageClient.getInstance().enforceMaximumDuration());
    }
}
