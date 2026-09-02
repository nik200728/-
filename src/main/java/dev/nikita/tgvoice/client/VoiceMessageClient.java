package dev.nikita.tgvoice.client;

import dev.nikita.tgvoice.network.VoiceMessagePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Client-side state machine for explicit Voice Messages only. */
public final class VoiceMessageClient {
    private static final VoiceMessageClient INSTANCE = new VoiceMessageClient();
    private static final long MIN_DURATION_MS = 200L;
    private static final long MAX_DURATION_MS = VoiceMessagePayload.MAX_DURATION_MILLIS;

    private RecordingSession session;

    private VoiceMessageClient() {}

    public static VoiceMessageClient getInstance() { return INSTANCE; }

    public synchronized boolean startRecording() {
        if (session != null && session.isActive()) return false;

        PlasmoVoiceClientAddon addon = PlasmoVoiceClientAddon.getInstance();
        if (addon == null || !addon.isAvailable()) return false;

        RecordingSession next = new RecordingSession();
        addon.startRecording(next);
        session = next;
        return true;
    }

    /** Stops capture immediately and queues encoding/network work away from the render tick. */
    public synchronized RecordingSession finishRecording() {
        if (session == null) return null;

        PlasmoVoiceClientAddon addon = PlasmoVoiceClientAddon.getInstance();
        if (addon != null) addon.stopRecording();

        session.finish();
        RecordingSession result = session;
        session = null;

        // Do not create tiny/empty Telegram-style messages from accidental clicks.
        if (result.durationMillis() < MIN_DURATION_MS || result.sampleCount() == 0) return result;

        publish(result);
        return result;
    }

    public synchronized void cancelRecording() {
        PlasmoVoiceClientAddon addon = PlasmoVoiceClientAddon.getInstance();
        if (addon != null) addon.stopRecording();
        if (session != null) session.cancel();
        session = null;
    }

    public synchronized void enforceMaximumDuration() {
        if (session != null && session.isActive() && session.durationMillis() >= MAX_DURATION_MS) {
            finishRecording();
        }
    }

    public synchronized boolean isRecording() { return session != null && session.isActive(); }
    public synchronized long durationMillis() { return session == null ? 0L : session.durationMillis(); }

    private void publish(RecordingSession recording) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        UUID senderUuid = minecraft.player.getUUID();
        String senderName = minecraft.player.getGameProfile().name();
        long duration = Math.min(MAX_DURATION_MS, recording.durationMillis());
        byte[] waveform = encodeWaveform(recording.waveform());

        CompletableFuture
                .supplyAsync(() -> PlasmoVoiceClientAddon.getInstance().encodeAsOggOpus(recording))
                .thenAccept(ogg -> minecraft.execute(() -> {
                    if (minecraft.getConnection() == null) return;
                    try {
                        ClientPlayNetworking.send(new VoiceMessagePayload(
                                UUID.randomUUID().toString(),
                                senderUuid,
                                senderName,
                                duration,
                                ogg,
                                waveform
                        ));
                    } catch (RuntimeException exception) {
                        System.err.println("[tgvoice] Failed to send Voice Message: " + exception.getMessage());
                    }
                }))
                .exceptionally(error -> {
                    System.err.println("[tgvoice] Failed to encode Voice Message: " + error);
                    return null;
                });
    }

    private static byte[] encodeWaveform(java.util.List<Short> peaks) {
        byte[] result = new byte[Math.min(VoiceMessagePayload.MAX_WAVEFORM_BYTES, peaks.size())];
        for (int i = 0; i < result.length; i++) {
            int peak = Short.toUnsignedInt(peaks.get(i));
            result[i] = (byte) Math.min(255, (peak * 255L) / 32767L);
        }
        return result.length == 0 ? new byte[] {0} : result;
    }
}
