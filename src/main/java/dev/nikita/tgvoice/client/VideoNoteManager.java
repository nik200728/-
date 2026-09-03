package dev.nikita.tgvoice.client;

import dev.nikita.tgvoice.network.VideoNotePayload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client inbox for local Minecraft video notes. Telegram linking is not involved here. */
public final class VideoNoteManager {
    private static final int MAX_MESSAGES = 32;
    private static final VideoNoteManager INSTANCE = new VideoNoteManager();

    private final Map<String, VideoNotePayload> messages = new LinkedHashMap<>();

    private VideoNoteManager() {}

    public static VideoNoteManager getInstance() {
        return INSTANCE;
    }

    public synchronized void accept(VideoNotePayload payload) {
        if (payload == null) return;
        String messageId = payload.messageId();
        if (messages.containsKey(messageId)) {
            // A replacement payload with the same ID must not keep stale timeline state.
            VideoNotePlaybackManager.getInstance().remove(messageId);
            messages.remove(messageId);
        }
        messages.put(messageId, payload);
        while (messages.size() > MAX_MESSAGES) {
            String oldest = messages.keySet().iterator().next();
            messages.remove(oldest);
            VideoNotePlaybackManager.getInstance().remove(oldest);
        }
    }

    public synchronized VideoNotePayload get(String messageId) {
        return messages.get(messageId);
    }

    public synchronized List<VideoNotePayload> messages() {
        return List.copyOf(new ArrayList<>(messages.values()));
    }

    public synchronized List<String> messageIds() {
        return List.copyOf(messages.keySet());
    }

    public synchronized void clear() {
        messages.clear();
        VideoNotePlaybackManager.getInstance().clear();
    }
}
