package nik200728.tgvoice.common.network;

import java.util.UUID;

/** Immutable metadata for one explicitly recorded Voice Message. */
public record VoiceMessagePacket(UUID messageId, UUID senderUuid, int durationMs, int sampleRate, int channels, byte[] opusOgg) {
    public VoiceMessagePacket {
        if (messageId == null || senderUuid == null) throw new IllegalArgumentException("message ids are required");
        if (durationMs < 0 || durationMs > 60_000) throw new IllegalArgumentException("invalid duration");
        if (sampleRate != 48_000 || channels != 1) throw new IllegalArgumentException("unsupported audio format");
        if (opusOgg == null || opusOgg.length == 0 || opusOgg.length > 2 * 1024 * 1024) throw new IllegalArgumentException("invalid audio payload");
        opusOgg = opusOgg.clone();
    }

    @Override public byte[] opusOgg() { return opusOgg.clone(); }
}
