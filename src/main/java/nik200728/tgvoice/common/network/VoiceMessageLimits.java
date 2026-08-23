package nik200728.tgvoice.common.network;

public final class VoiceMessageLimits {
    public static final int MAX_DURATION_MS = 60_000;
    public static final int MAX_PAYLOAD_BYTES = 2 * 1024 * 1024;
    public static final int SAMPLE_RATE = 48_000;
    public static final int CHANNELS = 1;

    private VoiceMessageLimits() {}
}
