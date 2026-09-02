package dev.nikita.tgvoice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import su.plo.voice.api.audio.codec.AudioDecoder;
import su.plo.voice.api.audio.codec.CodecException;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.audio.device.DeviceException;
import su.plo.voice.api.client.audio.source.LoopbackSource;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Real Voice Message playback through Plasmo Voice's client audio engine. */
public final class VoiceMessagePlayback {
    private static final Logger LOGGER = LoggerFactory.getLogger("tgvoice/playback");
    private static final int SAMPLE_RATE = 48_000;
    private static final int FRAME_MILLIS = 20;
    private static final long FRAME_NANOS = FRAME_MILLIS * 1_000_000L;
    private static final int MAX_OGG_BYTES = 2 * 1024 * 1024;

    public enum State { STOPPED, PLAYING, PAUSED }

    private final PlasmoVoiceClient voiceClient;
    private final Object lock = new Object();

    private State state = State.STOPPED;
    private long positionMillis;
    private long durationMillis;
    private long lastPumpNanos;
    private boolean pendingPlay;
    private LoopbackSource source;
    private List<short[]> decodedFrames = List.of();
    private int frameIndex;
    private int frameSampleOffset;
    private CompletableFuture<?> decodeTask;

    public VoiceMessagePlayback(PlasmoVoiceClient voiceClient) {
        this.voiceClient = voiceClient;
    }

    public void load(long durationMillis, byte[] oggOpus) {
        if (oggOpus == null || oggOpus.length == 0) throw new IllegalArgumentException("Voice message audio is empty");
        if (oggOpus.length > MAX_OGG_BYTES) throw new IllegalArgumentException("Voice message audio is too large");

        stop();
        synchronized (lock) {
            this.durationMillis = Math.max(0, durationMillis);
            this.positionMillis = 0;
            this.decodedFrames = List.of();
            this.frameIndex = 0;
            this.frameSampleOffset = 0;
            this.pendingPlay = false;
        }

        byte[] copy = Arrays.copyOf(oggOpus, oggOpus.length);
        decodeTask = CompletableFuture.runAsync(() -> decode(copy));
    }

    public void play() {
        synchronized (lock) {
            if (durationMillis <= 0 || positionMillis >= durationMillis) return;
            if (decodedFrames.isEmpty()) {
                pendingPlay = true;
                return;
            }
            pendingPlay = false;
            startPlaybackLocked();
        }
    }

    public void pause() {
        synchronized (lock) {
            if (state == State.PLAYING) state = State.PAUSED;
        }
    }

    public void resume() {
        synchronized (lock) {
            if (state == State.PAUSED) {
                startPlaybackLocked();
            }
        }
    }

    public void stop() {
        synchronized (lock) {
            state = State.STOPPED;
            positionMillis = 0;
            frameIndex = 0;
            frameSampleOffset = 0;
            pendingPlay = false;
            lastPumpNanos = 0;
            closeSourceLocked();
        }
    }

    public void seek(long millis) {
        synchronized (lock) {
            long target = Math.max(0, Math.min(durationMillis, millis));
            long targetSampleLong = target * SAMPLE_RATE / 1000L;
            long remaining = targetSampleLong;
            frameIndex = 0;
            frameSampleOffset = 0;
            while (frameIndex < decodedFrames.size()) {
                int size = decodedFrames.get(frameIndex).length;
                if (remaining < size) {
                    frameSampleOffset = (int) remaining;
                    break;
                }
                remaining -= size;
                frameIndex++;
            }
            positionMillis = target;
            if (positionMillis >= durationMillis && durationMillis > 0) {
                state = State.STOPPED;
                closeSourceLocked();
            } else if (state == State.PLAYING) {
                // Recreate the source so samples queued before the seek cannot leak
                // into the newly selected position.
                closeSourceLocked();
                startPlaybackLocked();
            }
        }
    }

    /** Called from the client tick to feed PCM according to elapsed wall-clock time. */
    public void tick() {
        synchronized (lock) {
            if (state != State.PLAYING) return;
            long now = System.nanoTime();
            if (lastPumpNanos == 0) lastPumpNanos = now;
            long elapsed = Math.max(0, now - lastPumpNanos);
            int framesDue = (int) Math.min(8, elapsed / FRAME_NANOS);
            if (framesDue > 0) {
                for (int i = 0; i < framesDue && state == State.PLAYING; i++) pumpLocked();
                lastPumpNanos += framesDue * FRAME_NANOS;
            }
        }
    }

    public State state() { synchronized (lock) { return state; } }
    public long positionMillis() { synchronized (lock) { return positionMillis; } }
    public long durationMillis() { synchronized (lock) { return durationMillis; } }
    public float progress() {
        synchronized (lock) {
            return durationMillis <= 0 ? 0f : Math.min(1f, Math.max(0f, (float) positionMillis / durationMillis));
        }
    }

    private void startPlaybackLocked() {
        if (decodedFrames.isEmpty() || durationMillis <= 0 || positionMillis >= durationMillis) return;
        ensureSource();
        state = State.PLAYING;
        lastPumpNanos = System.nanoTime();
        pumpLocked();
    }

    private void decode(byte[] ogg) {
        AudioDecoder decoder = null;
        List<short[]> frames = new ArrayList<>();
        try {
            decoder = voiceClient.getServerInfo()
                    .orElseThrow(() -> new IllegalStateException("Plasmo Voice server is not connected"))
                    .createOpusDecoder(false);
            decoder.open();
            for (byte[] packet : extractOpusPackets(ogg)) {
                short[] pcm = decoder.decode(packet);
                if (pcm.length > 0) frames.add(pcm);
            }
            synchronized (lock) {
                decodedFrames = List.copyOf(frames);
                if (durationMillis <= 0) {
                    long samples = decodedFrames.stream().mapToLong(a -> a.length).sum();
                    durationMillis = samples * 1000L / SAMPLE_RATE;
                }
                if (pendingPlay && !decodedFrames.isEmpty()) {
                    pendingPlay = false;
                    startPlaybackLocked();
                }
            }
        } catch (CodecException | RuntimeException e) {
            LOGGER.warn("Failed to decode Voice Message", e);
            synchronized (lock) {
                decodedFrames = List.of();
                pendingPlay = false;
                state = State.STOPPED;
            }
        } finally {
            if (decoder != null) {
                try {
                    decoder.close();
                } catch (RuntimeException e) {
                    LOGGER.debug("Failed to close Voice Message decoder", e);
                }
            }
        }
    }

    private void ensureSource() {
        if (source != null && !source.isClosed()) return;
        try {
            source = voiceClient.getSourceManager().createLoopbackSource(true);
            source.initialize(false);
        } catch (DeviceException e) {
            source = null;
            throw new IllegalStateException("Failed to initialize Plasmo Voice playback source", e);
        }
    }

    private void closeSourceLocked() {
        if (source != null) {
            source.close();
            source = null;
        }
    }

    private void pumpLocked() {
        if (source == null || source.isClosed() || state != State.PLAYING) return;
        if (frameIndex >= decodedFrames.size()) {
            state = State.STOPPED;
            positionMillis = durationMillis;
            closeSourceLocked();
            return;
        }

        short[] frame = decodedFrames.get(frameIndex);
        if (frameSampleOffset == 0) {
            source.write(frame);
        } else {
            source.write(Arrays.copyOfRange(frame, frameSampleOffset, frame.length));
            frameSampleOffset = 0;
        }

        int writtenSamples = frame.length;
        frameIndex++;
        positionMillis = Math.min(durationMillis, positionMillis + writtenSamples * 1000L / SAMPLE_RATE);
        if (positionMillis >= durationMillis || frameIndex >= decodedFrames.size()) {
            state = State.STOPPED;
            positionMillis = durationMillis;
            closeSourceLocked();
        }
    }

    /** Parses Ogg pages into complete Opus packets and removes OpusHead/OpusTags. */
    private static List<byte[]> extractOpusPackets(byte[] data) {
        List<byte[]> packets = new ArrayList<>();
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        int offset = 0;

        while (offset < data.length) {
            if (data.length - offset < 27) throw new IllegalArgumentException("Truncated Ogg page");
            if (data[offset] != 'O' || data[offset + 1] != 'g' || data[offset + 2] != 'g' || data[offset + 3] != 'S') {
                throw new IllegalArgumentException("Invalid Ogg capture pattern");
            }

            int pageSegments = data[offset + 26] & 0xFF;
            int headerLength = 27 + pageSegments;
            if (data.length - offset < headerLength) throw new IllegalArgumentException("Truncated Ogg segment table");

            int bodyLength = 0;
            for (int i = 0; i < pageSegments; i++) bodyLength += data[offset + 27 + i] & 0xFF;
            if (data.length - offset - headerLength < bodyLength) throw new IllegalArgumentException("Truncated Ogg page body");

            int body = offset + headerLength;
            int bodyOffset = 0;
            for (int i = 0; i < pageSegments; i++) {
                int segment = data[offset + 27 + i] & 0xFF;
                packet.write(data, body + bodyOffset, segment);
                bodyOffset += segment;
                if (segment < 255) {
                    byte[] complete = packet.toByteArray();
                    packet.reset();
                    if (!isOpusHeader(complete)) packets.add(complete);
                }
            }
            offset = body + bodyLength;
        }
        return packets;
    }

    private static boolean isOpusHeader(byte[] packet) {
        return startsWith(packet, "OpusHead") || startsWith(packet, "OpusTags");
    }

    private static boolean startsWith(byte[] data, String prefix) {
        if (data.length < prefix.length()) return false;
        for (int i = 0; i < prefix.length(); i++) if (data[i] != (byte) prefix.charAt(i)) return false;
        return true;
    }
}
