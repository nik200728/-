package dev.nikita.ptv;

import java.util.UUID;

public record VoiceMessage(
        UUID messageId,
        UUID audioId,
        UUID senderUuid,
        String senderName,
        int durationMs,
        byte[] waveform
) {
    public VoiceMessage {
        if (messageId == null || audioId == null || senderUuid == null) {
            throw new IllegalArgumentException("Message identifiers must not be null");
        }
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must be >= 0");
        }
        waveform = waveform == null ? new byte[0] : waveform.clone();
    }

    @Override
    public byte[] waveform() {
        return waveform.clone();
    }
}
