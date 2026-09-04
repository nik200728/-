package dev.nikita.tgvoice.client;

import dev.nikita.tgvoice.network.VideoNotePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import su.plo.voice.api.client.PlasmoVoiceClient;

/** Fabric client bootstrap for the Telegram voice/video-message addon. */
public final class TelegramVoiceClient implements ClientModInitializer {
    public static final String MOD_ID = "tgvoice";
    private static long lastTickNanos;

    @Override
    public void onInitializeClient() {
        PlasmoVoiceClient.getAddonsLoader().load(new PlasmoVoiceClientAddon());
        VoiceMessageInput.register();
        VoiceMessagePlaybackManager.register();
        VideoNoteUiController.register();
        VideoNoteCaptureController.register();

        ClientPlayNetworking.registerGlobalReceiver(VideoNotePayload.TYPE, (payload, context) ->
                context.client().execute(() -> VideoNoteManager.getInstance().accept(payload)));

        lastTickNanos = System.nanoTime();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            VoiceMessageClient.getInstance().enforceMaximumDuration();

            long now = System.nanoTime();
            long elapsedMillis = Math.max(0L, Math.min(250L, (now - lastTickNanos) / 1_000_000L));
            lastTickNanos = now;
            VideoNotePlaybackManager.getInstance().tick(elapsedMillis);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            VideoNoteCaptureController.shutdown();
            VideoNotePlaybackManager.getInstance().clear();
            VideoNoteManager.getInstance().clear();
            lastTickNanos = System.nanoTime();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            VideoNoteCaptureController.shutdown();
            VideoNotePlaybackManager.getInstance().clear();
            VideoNoteManager.getInstance().clear();
        });
    }
}
