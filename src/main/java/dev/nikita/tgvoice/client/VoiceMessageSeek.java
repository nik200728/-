package dev.nikita.tgvoice.client;

/** Converts mouse position inside a waveform into a playback position. */
public final class VoiceMessageSeek {
    private VoiceMessageSeek() {}

    public static long fromMouse(double mouseX, double left, double width, long durationMillis) {
        if (durationMillis <= 0 || width <= 0) return 0;
        double normalized = (mouseX - left) / width;
        normalized = Math.max(0.0, Math.min(1.0, normalized));
        return Math.round(normalized * durationMillis);
    }
}
