package nik200728.tgvoice.client;

import dev.nikita.tgvoice.client.PlasmoVoiceClientAddon;
import dev.nikita.tgvoice.client.VideoNoteManager;
import dev.nikita.tgvoice.client.VoiceMessageClient;
import dev.nikita.tgvoice.client.VoiceMessageInput;
import dev.nikita.tgvoice.client.VoiceMessagePlaybackManager;
import dev.nikita.tgvoice.network.VideoNotePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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

        // Local video notes are a separate media channel. They do not require a
        // Telegram binding and do not touch Plasmo Voice's proximity packets.
        ClientPlayNetworking.registerGlobalReceiver(VideoNotePayload.TYPE, (payload, context) ->
                context.client().execute(() -> VideoNoteManager.getInstance().accept(payload)));

        // Hard-stop at the protocol limit even if the user keeps the record button held.
        ClientTickEvents.END_CLIENT_TICK.register(client -> VoiceMessageClient.getInstance().enforceMaximumDuration());
    }
}
