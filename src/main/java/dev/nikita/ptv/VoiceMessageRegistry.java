package dev.nikita.ptv;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe in-memory registry used by the MVP until persistent networking/storage is added. */
public final class VoiceMessageRegistry {
    private static final Map<UUID, VoiceMessage> MESSAGES = new ConcurrentHashMap<>();

    private VoiceMessageRegistry() {}

    public static void init() {
        MESSAGES.clear();
    }

    public static void put(VoiceMessage message) {
        MESSAGES.put(message.messageId(), message);
    }

    public static VoiceMessage get(UUID messageId) {
        return MESSAGES.get(messageId);
    }

    public static int size() {
        return MESSAGES.size();
    }
}
