package dev.nikita.tgvoice.client;

import dev.nikita.tgvoice.network.VoiceMessagePayload;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Owns one explicitly requested Voice Message recording. */
public final class RecordingSession {
    private static final int SAMPLE_RATE = 48_000;
    private static final int CHANNELS = 1;
    private static final int BYTES_PER_SAMPLE = 2;
    private static final int WAVEFORM_BUCKET_SAMPLES = 960;
    private static final int MAX_SAMPLES = (int) ((long) SAMPLE_RATE * VoiceMessagePayload.MAX_DURATION_MILLIS / 1000L);

    private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
    private final List<Short> waveform = new ArrayList<>();
    private int bucketPeak;
    private int bucketSamples;
    private int sampleCount;
    private boolean active = true;

    public void appendPcm(short[] samples, int offset, int length) {
        if (!active || samples == null || length <= 0 || sampleCount >= MAX_SAMPLES) return;
        if (offset < 0 || offset >= samples.length) return;

        int end = Math.min(samples.length, offset + length);
        int accepted = Math.min(end - offset, MAX_SAMPLES - sampleCount);
        for (int i = offset; i < offset + accepted; i++) {
            short sample = samples[i];
            pcm.write(sample & 0xff);
            pcm.write((sample >>> 8) & 0xff);
            bucketPeak = Math.max(bucketPeak, Math.abs((int) sample));
            bucketSamples++;
            sampleCount++;
            if (bucketSamples >= WAVEFORM_BUCKET_SAMPLES) {
                waveform.add((short) bucketPeak);
                bucketPeak = 0;
                bucketSamples = 0;
            }
        }
    }

    public void finish() {
        if (!active) return;
        active = false;
        if (bucketSamples > 0) waveform.add((short) bucketPeak);
    }

    public void cancel() {
        active = false;
        pcm.reset();
        waveform.clear();
        sampleCount = 0;
        bucketPeak = 0;
        bucketSamples = 0;
    }

    public boolean isActive() { return active; }
    public long durationMillis() { return sampleCount * 1000L / SAMPLE_RATE; }
    public int sampleCount() { return sampleCount; }
    public byte[] pcm16le() { return pcm.toByteArray(); }
    public List<Short> waveform() { return List.copyOf(waveform); }
    public int sampleRate() { return SAMPLE_RATE; }
    public int channels() { return CHANNELS; }
    public int bytesPerSample() { return BYTES_PER_SAMPLE; }
}
