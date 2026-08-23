package dev.nikita.ptv.client;

import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.client.PlasmoVoiceClient;

/** Plasmo Voice addon entrypoint used only for the public compatibility API. */
@Addon(
        id = "plasmo-telegram-voice",
        name = "Plasmo Telegram Voice Messages",
        version = "0.1.0",
        authors = {"Nikita"}
)
public final class PlasmoVoiceAddon implements AddonInitializer {
    @InjectPlasmoVoice
    private PlasmoVoiceClient voiceClient;

    private PlasmoVoiceCaptureBridge captureBridge;

    @Override
    public void onAddonInitialize() {
        if (voiceClient == null) {
            throw new IllegalStateException("Plasmo Voice did not inject PlasmoVoiceClient");
        }
        captureBridge = new PlasmoVoiceCaptureBridge(voiceClient);
        PlasmoTelegramVoiceClient.setCaptureBridge(captureBridge);
    }

    @Override
    public void onAddonShutdown() {
        if (captureBridge != null) {
            captureBridge.close();
            captureBridge = null;
        }
        PlasmoTelegramVoiceClient.setCaptureBridge(null);
    }
}
