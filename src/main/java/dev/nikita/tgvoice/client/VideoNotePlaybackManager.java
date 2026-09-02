package dev.nikita.tgvoice.client;

import dev.nikita.tgvoice.network.VideoNoteContainer;
import dev.nikita.tgvoice.network.VideoNotePayload;

import java.util.LinkedHashMap;
import java.util.Map;

/** Owns local video-note playback state; it never touches Plasmo Voice. */
public final class VideoNotePlaybackManager {
    private static final int MAX_PLAYBACKS = 32;
    private static final VideoNotePlaybackManager INSTANCE = new VideoNotePlaybackManager();

    private final Map<String, VideoNotePlayback> playbacks = new LinkedHashMap<>();

    private VideoNotePlaybackManager() {}

    public static VideoNotePlaybackManager getInstance() {
        return INSTANCE;
    }

    public synchronized VideoNotePlayback getOrCreate(VideoNotePayload payload) {
        if (payload == null) throw new IllegalArgumentException("payload is required");
        VideoNotePlayback playback = playbacks.get(payload.messageId());
        if (playback != null) return playback;

        VideoNoteContainer.Video video = VideoNoteContainer.decode(payload.videoData());
        if (video.width() != payload.width() || video.height() != payload.height()
                || video.frameRate() != payload.frameRate() || video.durationMillis() != payload.durationMillis()) {
            throw new IllegalArgumentException("video metadata mismatch");
        }
        playback = new VideoNotePlayback(video);
        playbacks.put(payload.messageId(), playback);
        while (playbacks.size() > MAX_PLAYBACKS) {
            playbacks.remove(playbacks.keySet().iterator().next());
        }
        return playback;
    }

    public synchronized void play(VideoNotePayload payload) { getOrCreate(payload).play(); }
    public synchronized void pause(VideoNotePayload payload) { getOrCreate(payload).pause(); }
    public synchronized void stop(VideoNotePayload payload) { getOrCreate(payload).stop(); }
    public synchronized void seek(VideoNotePayload payload, long positionMillis) { getOrCreate(payload).seek(positionMillis); }

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
