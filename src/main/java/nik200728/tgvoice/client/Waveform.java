package nik200728.tgvoice.client;

/** Compact amplitude representation used by the Voice Message UI. */
public final class Waveform {
    private Waveform() {}

    public static byte[] fromPcm16(short[] samples, int bins) {
        if (samples == null || samples.length == 0 || bins <= 0) return new byte[0];
        byte[] result = new byte[bins];
        int samplesPerBin = Math.max(1, samples.length / bins);
        for (int i = 0; i < bins; i++) {
            int start = i * samplesPerBin;
            int end = Math.min(samples.length, i == bins - 1 ? samples.length : start + samplesPerBin);
            long sum = 0;
            for (int p = start; p < end; p++) sum += Math.abs((int) samples[p]);
            long average = (end > start) ? sum / (end - start) : 0;
            result[i] = (byte) Math.min(127, (average * 127L) / 32768L);
        }
        return result;
    }
}
