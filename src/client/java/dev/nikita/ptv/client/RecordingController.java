package dev.nikita.ptv.client;

import dev.nikita.ptv.PlasmoTelegramVoice;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Telegram-style hold-to-record state machine backed by Plasmo Voice capture. */
public final class RecordingController {
    private static final long MAX_DURATION_MS = 60_000L;

    private boolean recording;
    private long startedAt;
    private PlasmoVoiceCaptureBridge captureBridge;

    public void setCaptureBridge(PlasmoVoiceCaptureBridge bridge) {
        this.captureBridge = bridge;
    }

    public void start(Minecraft client) {
        if (recording || client.player == null || client.screen != null) return;
        if (captureBridge == null) {
            PlasmoTelegramVoice.LOGGER.warn("Cannot start voice message: Plasmo Voice is not ready");
            return;
        }
        recording = true;
        startedAt = System.currentTimeMillis();
        captureBridge.start();
        PlasmoTelegramVoice.LOGGER.info("Voice message recording started");
    }

    public void stop() {
        if (!recording) return;
        recording = false;

        if (captureBridge != null) {
            PlasmoVoiceCaptureBridge.CapturedAudio captured = captureBridge.stop();
            if (captured.sampleCount() == 0) {
                PlasmoTelegramVoice.LOGGER.warn("Voice message contained no captured microphone samples");
                return;
            }

            captureBridge.encodeAsync(captured, Runnable::run)
                    .whenComplete((message, error) -> {
                        if (error != null) {
                            PlasmoTelegramVoice.LOGGER.error("Voice message Opus encoding failed", error);
                        } else {
                            PlasmoTelegramVoice.LOGGER.info(
                                    "Voice message captured: {} ms, {} Opus bytes, {} packets",
                                    message.durationMs(), message.encodedBytes(), message.opusFrames().size()
                            );
                            // The encoded message will be handed to the server/Telegram queue in the next layer.
                        }
                    });
        }
    }

    public void cancel() {
        if (!recording) return;
        recording = false;
        if (captureBridge != null) captureBridge.cancel();
        startedAt = 0L;
        PlasmoTelegramVoice.LOGGER.info("Voice message recording cancelled");
    }

    public void tick() {
        if (!recording) return;
        if (Minecraft.getInstance().getWindow().getWindow() != 0
                && GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS) {
            cancel();
            return;
        }
        if (elapsedMs() >= MAX_DURATION_MS) stop();
    }

    public boolean isRecording() { return recording; }

    public long elapsedMs() {
        return recording ? Math.max(0L, System.currentTimeMillis() - startedAt) : 0L;
    }
}
