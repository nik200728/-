package dev.nikita.tgvoice.client;

import java.util.Objects;
import java.util.UUID;

/** Render-ready metadata for a synchronized voice message. */
public record VoiceMessageChat(
        String messageId,
        String senderName,
        UUID senderUuid,
        long durationMillis,
        Waveform waveform,
        long createdAtMillis,
        String source,
        String audioId,
        String telegramMessageId
) {
    public VoiceMessageChat {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(senderName, "senderName");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(audioId, "audioId");
        waveform = waveform == null ? new Waveform(new byte[0]) : waveform;
    }
}
