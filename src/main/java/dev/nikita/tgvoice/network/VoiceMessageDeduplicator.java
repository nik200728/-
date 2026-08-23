package dev.nikita.tgvoice.network;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Prevents replay/duplicate delivery of a Voice Message for a bounded time. */
public final class VoiceMessageDeduplicator {
    private final long ttlNanos;
    private final Map<String, Long> seen = new ConcurrentHashMap<>();

    public VoiceMessageDeduplicator(Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("ttl must be positive");
        this.ttlNanos = ttl.toNanos();
    }

    public boolean firstSeen(String messageId) {
        long now = System.nanoTime();
        purge(now);
        return seen.putIfAbsent(messageId, now) == null;
    }

    private void purge(long now) {
        seen.entrySet().removeIf(e -> now - e.getValue() > ttlNanos);
    }
}
