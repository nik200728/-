package dev.nikita.ptv.client;

import net.minecraft.client.Minecraft;

/**
 * State machine for Telegram-style push-to-talk. Audio capture is intentionally
 * isolated behind this controller so the Plasmo Voice compatibility layer can
 * supply the real microphone frames without opening a second permanent stream.
 */
public final class RecordingController {
    private static final long MAX_DURATION_MS = 60_000L;
    private boolean recording;
    private long startedAt;
    private boolean rightClickWasDown;

    public void start(Minecraft client) {
        if (recording || client.player == null) {
            return;
        }
        recording = true;
        startedAt = System.currentTimeMillis();
    }

    public void stop() {
        if (!recording) {
            return;
        }
        recording = false;
        // TODO: finalize the captured PCM/Opus frames through PlasmoVoiceCompat.
    }

    public void cancel() {
        recording = false;
        startedAt = 0L;
        // TODO: discard the captured frames.
    }

    public void tick() {
        if (!recording) {
            rightClickWasDown = false;
            return;
        }
        if (elapsedMs() >= MAX_DURATION_MS) {
            stop();
        }
    }

    public boolean isRecording() {
        return recording;
    }

    public long elapsedMs() {
        return recording ? Math.max(0L, System.currentTimeMillis() - startedAt) : 0L;
    }
}
