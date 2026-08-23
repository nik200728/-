package dev.nikita.tgvoice.network;

import java.util.Arrays;
import java.util.UUID;

/** Wire payload for an explicitly recorded Voice Message. */
public record VoiceMessagePayload(
        String messageId,
        UUID senderUuid,
        String senderName,
        long durationMillis,
        byte[] opusData,
        byte[] waveform
) {
    public static final int MAX_AUDIO_BYTES = 2 * 1024 * 1024;
    public static final int MAX_WAVEFORM_BYTES = 2048;
    public static final long MAX_DURATION_MILLIS = 60_000;

    public VoiceMessagePayload {
        if (messageId == null || messageId.isBlank() || messageId.length() > 64) throw new IllegalArgumentException("invalid messageId");
        if (senderUuid == null) throw new IllegalArgumentException("senderUuid is required");
        if (senderName == null || senderName.isBlank() || senderName.length() > 64) throw new IllegalArgumentException("invalid senderName");
        if (durationMillis < 1 || durationMillis > MAX_DURATION_MILLIS) throw new IllegalArgumentException("invalid duration");
        if (opusData == null || opusData.length == 0 || opusData.length > MAX_AUDIO_BYTES) throw new IllegalArgumentException("invalid audio payload");
        if (waveform == null || waveform.length == 0 || waveform.length > MAX_WAVEFORM_BYTES) throw new IllegalArgumentException("invalid waveform");
        opusData = Arrays.copyOf(opusData, opusData.length);
        waveform = Arrays.copyOf(waveform, waveform.length);
    }

    @Override public byte[] opusData() { return Arrays.copyOf(opusData, opusData.length); }
    @Override public byte[] waveform() { return Arrays.copyOf(waveform, waveform.length); }
}
