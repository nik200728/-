package dev.nikita.tgvoice.server.audio;

import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.codec.AudioEncoder;

import java.util.ArrayList;
import java.util.List;

/** Encodes Voice Message PCM with Plasmo Voice's public Opus encoder. */
public final class PlasmoVoiceOpusEncoderService implements AutoCloseable {
    public static final int SAMPLE_RATE = 48_000;
    public static final int CHANNELS = 1;
    public static final int FRAME_SAMPLES = 960;

    private final AudioEncoder encoder;

    public PlasmoVoiceOpusEncoderService(PlasmoVoiceServer voiceServer) {
        this.encoder = voiceServer.createOpusEncoder(false);
    }

    /** Encode 20 ms mono frames; the final frame is zero-padded. */
    public synchronized List<byte[]> encode(short[] pcm) {
        if (pcm == null || pcm.length == 0) return List.of();
        List<byte[]> frames = new ArrayList<>((pcm.length + FRAME_SAMPLES - 1) / FRAME_SAMPLES);
        for (int offset = 0; offset < pcm.length; offset += FRAME_SAMPLES) {
            int length = Math.min(FRAME_SAMPLES, pcm.length - offset);
            short[] frame = new short[FRAME_SAMPLES];
            System.arraycopy(pcm, offset, frame, 0, length);
            try {
                frames.add(encoder.encode(frame));
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to encode Voice Message Opus frame", exception);
            }
        }
        return List.copyOf(frames);
    }

    @Override
    public synchronized void close() {
        encoder.close();
    }
}
