package dev.nikita.tgvoice.client;

import java.util.LinkedHashMap;
import java.util.Map;

/** Small bounded in-memory registry keyed by the unified messageId. */
public final class VoiceMessageRegistry {
    private final int maxEntries;
    private final Map<String, VoiceMessageChat> messages;

    public VoiceMessageRegistry(int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
        this.messages = new LinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, VoiceMessageChat> eldest) {
                return size() > VoiceMessageRegistry.this.maxEntries;
            }
        };
    }

    public synchronized boolean putIfAbsent(VoiceMessageChat message) {
        if (messages.containsKey(message.messageId())) return false;
        messages.put(message.messageId(), message);
        return true;
    }

    public synchronized VoiceMessageChat get(String messageId) {
        return messages.get(messageId);
    }

    public synchronized int size() { return messages.size(); }

    public synchronized void clear() { messages.clear(); }
}
