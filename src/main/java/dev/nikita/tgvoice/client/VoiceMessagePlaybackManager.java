package dev.nikita.tgvoice.client;

import dev.nikita.tgvoice.network.VoiceMessagePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import su.plo.voice.api.client.PlasmoVoiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/** Owns received Voice Message playback instances without touching proximity voice. */
public final class VoiceMessagePlaybackManager {
    private static final int MAX_MESSAGES = 64;
    private static final Map<String, VoiceMessagePlayback> PLAYBACKS = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, VoiceMessagePlayback> eldest) {
            return size() > MAX_MESSAGES;
        }
    };

    private static volatile boolean registered;
    private static volatile String lastReceivedMessageId;

    private VoiceMessagePlaybackManager() {}

    public static void register() {
        if (registered) return;
        registered = true;

        ClientPlayNetworking.registerGlobalReceiver(VoiceMessagePayload.TYPE, (payload, context) ->
                context.client().execute(() -> receive(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    public static void receive(VoiceMessagePayload payload) {
        Minecraft client = Minecraft.getInstance();
        PlasmoVoiceClient.getAddonsLoader();

        PlasmoVoiceClientAddon addon = PlasmoVoiceClientAddon.getInstance();
        if (addon == null) return;

        // The addon owns the injected PV client; expose it only through a small helper.
        VoiceMessagePlayback playback = new VoiceMessagePlayback(addon.voiceClientForPlayback());
        playback.load(payload.durationMillis(), payload.opusData());

        synchronized (PLAYBACKS) {
            VoiceMessagePlayback previous = PLAYBACKS.put(payload.messageId(), playback);
            if (previous != null) previous.stop();
            lastReceivedMessageId = payload.messageId();
        }
    }

    public static VoiceMessagePlayback get(String messageId) {
        synchronized (PLAYBACKS) {
            return PLAYBACKS.get(messageId);
        }
    }

    public static String lastReceivedMessageId() { return lastReceivedMessageId; }

    public static void play(String messageId) {
        VoiceMessagePlayback playback = get(messageId);
        if (playback != null) playback.play();
    }

    public static void pause(String messageId) {
        VoiceMessagePlayback playback = get(messageId);
        if (playback != null) playback.pause();
    }

    public static void resume(String messageId) {
        VoiceMessagePlayback playback = get(messageId);
        if (playback != null) playback.resume();
    }

    public static void stop(String messageId) {
        VoiceMessagePlayback playback = get(messageId);
        if (playback != null) playback.stop();
    }

    public static void seek(String messageId, long millis) {
        VoiceMessagePlayback playback = get(messageId);
        if (playback != null) playback.seek(millis);
    }

    private static void tick() {
        synchronized (PLAYBACKS) {
            PLAYBACKS.values().forEach(VoiceMessagePlayback::tick);
        }
    }
}
