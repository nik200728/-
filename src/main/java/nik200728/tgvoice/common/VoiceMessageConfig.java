package nik200728.tgvoice.common;

public record VoiceMessageConfig(
        long maxDurationMillis,
        int bitrateKbps,
        boolean useLeftMouse,
        boolean pushToTalk,
        boolean toggleMode,
        float playbackVolume,
        boolean autoDownload,
        int cacheSizeMb,
        String bridgeUrl,
        long reconnectDelayMillis,
        long requestTimeoutMillis
) {
    public static VoiceMessageConfig defaults() {
        return new VoiceMessageConfig(
                120_000L, 24, true, true, false, 1.0f,
                true, 256, "http://127.0.0.1:8787", 2_000L, 10_000L
        );
    }
}
