package nik200728.tgvoice.client;

import dev.nikita.tgvoice.client.PlasmoVoiceClientAddon;
import net.fabricmc.api.ClientModInitializer;
import su.plo.voice.api.client.PlasmoVoiceClient;

/** Fabric client bootstrap for the Voice Messages addon. */
public final class TelegramVoiceClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Plasmo Voice owns the microphone lifecycle. We only register an addon
        // that observes its processed capture event while a Voice Message is active.
        PlasmoVoiceClient.getAddonsLoader().load(new PlasmoVoiceClientAddon());
    }
}
