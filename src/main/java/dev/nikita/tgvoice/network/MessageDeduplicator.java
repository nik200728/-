package dev.nikita.tgvoice.network;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MessageDeduplicator {
    private final int maxEntries;
    private final Map<String, Boolean> ids;

    public MessageDeduplicator(int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
        this.ids = new LinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > MessageDeduplicator.this.maxEntries;
            }
        };
    }

    public synchronized boolean accept(String messageId) {
        if (messageId == null || messageId.isBlank() || ids.containsKey(messageId)) return false;
        ids.put(messageId, Boolean.TRUE);
        return true;
    }
}
