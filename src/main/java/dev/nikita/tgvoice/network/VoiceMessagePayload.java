package dev.nikita.tgvoice.network;

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

    public VoiceMessagePayload {
        if (messageId == null || messageId.length() > 64) throw new IllegalArgumentException("invalid messageId");
        if (senderUuid == null) throw new IllegalArgumentException("senderUuid is required");
        if (senderName == null || senderName.length() > 64) throw new IllegalArgumentException("invalid senderName");
        if (durationMillis < 1 || durationMillis > 120_000) throw new IllegalArgumentException("invalid duration");
        if (opusData == null || opusData.length > MAX_AUDIO_BYTES) throw new IllegalArgumentException("audio exceeds limit");
        if (waveform == null || waveform.length > MAX_WAVEFORM_BYTES) throw new IllegalArgumentException("waveform exceeds limit");
        opusData = opusData.clone();
        waveform = waveform.clone();
    }
}
