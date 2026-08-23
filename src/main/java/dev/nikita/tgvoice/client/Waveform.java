package dev.nikita.tgvoice.client;

import java.util.Arrays;

/** Compact immutable waveform representation for rendering and seeking. */
public record Waveform(byte[] amplitudes) {
    public Waveform {
        amplitudes = amplitudes == null ? new byte[0] : amplitudes.clone();
    }

    public int size() { return amplitudes.length; }

    public int amplitude(int index) {
        if (index < 0 || index >= amplitudes.length) return 0;
        return amplitudes[index] & 0xff;
    }

    public byte[] copy() { return amplitudes.clone(); }

    public static Waveform fromPcm16(short[] samples, int buckets) {
        if (samples == null || samples.length == 0 || buckets <= 0) return new Waveform(new byte[0]);
        int count = Math.min(buckets, samples.length);
        byte[] out = new byte[count];
        int max = 1;
        int[] peaks = new int[count];
        for (int i = 0; i < samples.length; i++) {
            int bucket = Math.min(count - 1, (int) ((long) i * count / samples.length));
            int peak = Math.abs((int) samples[i]);
            peaks[bucket] = Math.max(peaks[bucket], peak);
            max = Math.max(max, peak);
        }
        for (int i = 0; i < count; i++) out[i] = (byte) Math.round(peaks[i] * 255.0 / max);
        return new Waveform(out);
    }

    @Override public String toString() { return Arrays.toString(amplitudes); }
}
