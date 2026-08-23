package nik200728.tgvoice.server;

import nik200728.tgvoice.common.network.VoiceMessagePacket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory MVP store; persistent storage belongs to the later Bridge/database layer. */
public final class VoiceMessageStore {
    private final Map<UUID, VoiceMessagePacket> messages = new ConcurrentHashMap<>();

    public void put(VoiceMessagePacket message) { messages.put(message.messageId(), message); }
    public VoiceMessagePacket get(UUID id) { return messages.get(id); }
    public boolean contains(UUID id) { return messages.containsKey(id); }
    public int size() { return messages.size(); }
}
