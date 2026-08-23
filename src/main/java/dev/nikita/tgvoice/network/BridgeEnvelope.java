package dev.nikita.tgvoice.network;

import java.util.Objects;

public record BridgeEnvelope(String messageId, String audioId, String type, long createdAtMillis) {
    public BridgeEnvelope {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(audioId, "audioId");
        Objects.requireNonNull(type, "type");
        if (messageId.length() > 64 || audioId.length() > 128 || type.length() > 32) {
            throw new IllegalArgumentException("bridge metadata too long");
        }
    }
}
