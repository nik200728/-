package dev.nikita.tgvoice.network;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Prevents replay/duplicate delivery of the same logical message. */
public final class VoiceMessageDeduplication {
    private final ConcurrentHashMap<UUID, Long> seen = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public VoiceMessageDeduplication(long ttlMillis) {
        if (ttlMillis <= 0) throw new IllegalArgumentException("ttlMillis must be positive");
        this.ttlMillis = ttlMillis;
    }

    public boolean accept(UUID messageId, long nowMillis) {
        cleanup(nowMillis);
        return seen.putIfAbsent(messageId, nowMillis) == null;
    }

    private void cleanup(long nowMillis) {
        seen.entrySet().removeIf(e -> nowMillis - e.getValue() > ttlMillis);
    }
}
