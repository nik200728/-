package nik200728.tgvoice.common;

import java.util.UUID;

public record VoiceMessage(
        UUID messageId,
        UUID senderUuid,
        String senderName,
        long durationMillis,
        byte[] waveform,
        String audioId,
        String telegramMessageId,
        long createdAtEpochMillis
) {
    public VoiceMessage {
        if (messageId == null) throw new IllegalArgumentException("messageId");
        if (senderUuid == null) throw new IllegalArgumentException("senderUuid");
        if (durationMillis < 0) throw new IllegalArgumentException("durationMillis");
        waveform = waveform == null ? new byte[0] : waveform.clone();
    }
}
