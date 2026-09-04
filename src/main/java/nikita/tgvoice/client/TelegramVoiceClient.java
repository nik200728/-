package nik200728.tgvoice.client;

import dev.nikita.tgvoice.client.PlasmoVoiceClientAddon;
import dev.nikita.tgvoice.client.VideoNoteCaptureController;
import dev.nikita.tgvoice.client.VideoNoteManager;
import dev.nikita.tgvoice.client.VideoNotePlaybackManager;
import dev.nikita.tgvoice.client.VideoNoteUiController;
import dev.nikita.tgvoice.client.VoiceMessageClient;
import dev.nikita.tgvoice.client.VoiceMessageInput;
import dev.nikita.tgvoice.client.VoiceMessagePlaybackManager;
import dev.nikita.tgvoice.network.VideoNotePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import su.plo.voice.api.client.PlasmoVoiceClient;

/** Fabric client bootstrap for the Voice Messages addon. */
public final class TelegramVoiceClient implements ClientModInitializer {
    public static final String MOD_ID = "tgvoice";

    @Override
    public void onInitializeClient() {
        PlasmoVoiceClient.getAddonsLoader().load(new PlasmoVoiceClientAddon());
        VoiceMessageInput.register();
        VoiceMessagePlaybackManager.register();
        VideoNoteUiController.register();
        VideoNoteCaptureController.register();

        ClientPlayNetworking.registerGlobalReceiver(VideoNotePayload.TYPE, (payload, context) ->
                context.client().execute(() -> VideoNoteManager.getInstance().accept(payload)));

        ClientTickEvents.END_CLIENT_TICK.register(client ->
                VoiceMessageClient.getInstance().enforceMaximumDuration());

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            VideoNoteCaptureController.shutdown();
            VideoNotePlaybackManager.getInstance().clear();
            VideoNoteManager.getInstance().clear();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            VideoNoteCaptureController.shutdown();
            VideoNotePlaybackManager.getInstance().clear();
            VideoNoteManager.getInstance().clear();
        });
    }
}
