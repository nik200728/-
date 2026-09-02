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
        if (video.width() != payload.width()
                || video.height() != payload.height()
                || video.frameRate() != payload.frameRate()
                || video.durationMillis() != payload.durationMillis()) {
            throw new IllegalArgumentException("video metadata mismatch");
        }

        VideoNotePlayback playback = new VideoNotePlayback(video);
        playbacks.put(payload.messageId(), playback);
        return playback;
    }

    public synchronized void play(VideoNotePayload payload) { load(payload).play(); }
    public synchronized void pause(VideoNotePayload payload) { load(payload).pause(); }
    public synchronized void stop(VideoNotePayload payload) { load(payload).stop(); }
    public synchronized void seek(VideoNotePayload payload, long positionMillis) {
        load(payload).seek(positionMillis);
    }

    public synchronized void tick(long elapsedMillis) {
        if (elapsedMillis <= 0) return;
        for (VideoNotePlayback playback : playbacks.values()) playback.tick(elapsedMillis);
    }

    public synchronized void remove(String messageId) {
        if (messageId != null) playbacks.remove(messageId);
    }

    public synchronized void clear() {
        playbacks.clear();
    }
}
