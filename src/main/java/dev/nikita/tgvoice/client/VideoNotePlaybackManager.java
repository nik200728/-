package dev.nikita.tgvoice.client;

import dev.nikita.tgvoice.network.VideoNoteContainer;
import dev.nikita.tgvoice.network.VideoNotePayload;

import java.util.LinkedHashMap;
import java.util.Map;

/** Owns video-note timeline state independently from Plasmo Voice playback. */
public final class VideoNotePlaybackManager {
    private static final int MAX_PLAYBACKS = 32;
    private static final VideoNotePlaybackManager INSTANCE = new VideoNotePlaybackManager();

    private final Map<String, VideoNotePlayback> playbacks = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, VideoNotePlayback> eldest) {
            return size() > MAX_PLAYBACKS;
        }
    };

    private VideoNotePlaybackManager() {}

    public static VideoNotePlaybackManager getInstance() { return INSTANCE; }

    public synchronized VideoNotePlayback get(String messageId) {
        return playbacks.get(messageId);
    }

    public synchronized VideoNotePlayback load(VideoNotePayload payload) {
        if (payload == null) throw new IllegalArgumentException("payload is required");
        VideoNotePlayback existing = playbacks.get(payload.messageId());
        if (existing != null) return existing;
        VideoNoteContainer.Video video = VideoNoteContainer.decode(payload.videoData());
        VideoNotePlayback playback = new VideoNotePlayback(video);
        playbacks.put(payload.messageId(), playback);
        return playback;
    }

    public synchronized void tick(long elapsedMillis) {
        for (VideoNotePlayback playback : playbacks.values()) playback.tick(elapsedMillis);
    }

    public synchronized void clear() {
        playbacks.clear();
    }
}
