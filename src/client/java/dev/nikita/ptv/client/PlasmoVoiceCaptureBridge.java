package dev.nikita.ptv.client;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.plo.voice.api.audio.codec.AudioEncoder;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.audio.capture.ClientActivation;
import su.plo.voice.api.client.config.hotkey.Hotkey;
import su.plo.voice.proto.data.audio.capture.VoiceActivation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Hooks into Plasmo Voice's existing microphone capture pipeline without activating proximity voice. */
public final class PlasmoVoiceCaptureBridge implements AutoCloseable {
    private final PlasmoVoiceClient voiceClient;
    private final CaptureActivation activation;
    private final List<short[]> frames = new ArrayList<>();
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private volatile long startedAt;

    public PlasmoVoiceCaptureBridge(@NotNull PlasmoVoiceClient voiceClient) {
        this.voiceClient = voiceClient;
        this.activation = new CaptureActivation();
        voiceClient.getActivationManager().register(activation);
    }

    public void start() {
        synchronized (frames) { frames.clear(); }
        startedAt = System.currentTimeMillis();
        recording.set(true);
    }

    public CapturedAudio stop() {
        recording.set(false);
        List<short[]> snapshot;
        synchronized (frames) {
            snapshot = new ArrayList<>(frames.size());
            for (short[] frame : frames) snapshot.add(frame.clone());
            frames.clear();
        }
        return new CapturedAudio(snapshot, Math.max(0L, System.currentTimeMillis() - startedAt));
    }

    public void cancel() {
        recording.set(false);
        synchronized (frames) { frames.clear(); }
    }

    public boolean isRecording() { return recording.get(); }

    public int getSampleRate() {
        return voiceClient.getServerInfo()
                .map(info -> info.getVoiceInfo().getCaptureInfo().getSampleRate())
                .orElse(48_000);
    }

    public Executor getBackgroundExecutor() {
        return voiceClient.getBackgroundExecutor();
    }

    /** Uses Plasmo Voice's own Opus encoder; this method runs on PV's background executor. */
    public CompletableFuture<EncodedVoiceMessage> encodeAsync(CapturedAudio captured) {
        return CompletableFuture.supplyAsync(() -> {
            if (captured.frames().isEmpty()) {
                return new EncodedVoiceMessage(List.of(), captured.durationMs(), getSampleRate());
            }
            var serverInfo = voiceClient.getServerInfo()
                    .orElseThrow(() -> new IllegalStateException("Plasmo Voice server connection is not ready"));
            AudioEncoder encoder = serverInfo.createOpusEncoder(false);
            List<byte[]> opusFrames = new ArrayList<>(captured.frames().size());
            try {
                encoder.open();
                for (short[] pcm : captured.frames()) {
                    byte[] encoded = encoder.encode(pcm);
                    if (encoded != null && encoded.length > 0) opusFrames.add(encoded);
                }
            } finally {
                encoder.close();
            }
            return new EncodedVoiceMessage(
                    opusFrames,
                    captured.durationMs(),
                    serverInfo.getVoiceInfo().getCaptureInfo().getSampleRate()
            );
        }, getBackgroundExecutor());
    }

    public record CapturedAudio(List<short[]> frames, long durationMs) {
        public int sampleCount() {
            int count = 0;
            for (short[] frame : frames) count += frame.length;
            return count;
        }
    }

    public record EncodedVoiceMessage(List<byte[]> opusFrames, long durationMs, int sampleRate) {
        public int encodedBytes() {
            int total = 0;
            for (byte[] frame : opusFrames) total += frame.length;
            return total;
        }
    }

    private final class CaptureActivation extends VoiceActivation implements ClientActivation {
        private final Hotkey internalHotkey;

        private CaptureActivation() {
            super("plasmo-telegram-voice-capture", "key.plasmo-telegram-voice.record",
                    "plasmovoice:textures/icons/microphone.png", List.of(), 0,
                    false, false, false, null, Integer.MAX_VALUE);
            internalHotkey = voiceClient.getHotkeys().register(
                    "key.plasmo-telegram-voice.internal", List.of(), "hidden", true);
        }

        @Override public @NotNull Type getType() { return Type.PUSH_TO_TALK; }
        @Override public @NotNull Hotkey getPttKey() { return internalHotkey; }
        @Override public @NotNull Hotkey getToggleKey() { return internalHotkey; }
        @Override public @NotNull Hotkey getDistanceIncreaseKey() { return internalHotkey; }
        @Override public @NotNull Hotkey getDistanceDecreaseKey() { return internalHotkey; }
        @Override public Optional<AudioEncoder> getMonoEncoder() { return Optional.empty(); }
        @Override public Optional<AudioEncoder> getStereoEncoder() { return Optional.empty(); }
        @Override public void setDisabled(boolean disabled) { }
        @Override public boolean isDisabled() { return false; }
        @Override public boolean isActive() { return recording.get(); }
        @Override public long getLastActivation() { return startedAt; }
        @Override public int getDistance() { return 0; }

        @Override
        public @NotNull Result process(short[] samples, @Nullable Result result) {
            if (recording.get() && samples != null && samples.length > 0) {
                synchronized (frames) { frames.add(samples.clone()); }
            }
            return Result.NOT_ACTIVATED;
        }

        @Override public void reset() { }
        @Override public void cleanup() { }
    }

    @Override
    public void close() {
        voiceClient.getActivationManager().unregister(activation);
    }
}
