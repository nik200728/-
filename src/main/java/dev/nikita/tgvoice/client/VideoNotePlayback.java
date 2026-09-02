package dev.nikita.tgvoice.client;

import dev.nikita.tgvoice.network.VideoNoteContainer;

import java.util.List;

/**
 * Timeline state for a video note. Rendering is deliberately kept separate so
 * playback never owns the camera or a second media device.
 */
public final class VideoNotePlayback {
    private final VideoNoteContainer.Video video;
    private long positionMillis;
    private boolean playing;

    public VideoNotePlayback(VideoNoteContainer.Video video) {
        if (video == null) throw new IllegalArgumentException("video is required");
        this.video = video;
    }

    public synchronized void play() {
        if (positionMillis >= video.durationMillis()) positionMillis = 0;
        playing = true;
    }

    public synchronized void pause() {
        playing = false;
    }

    public synchronized void stop() {
        playing = false;
        positionMillis = 0;
    }

    public synchronized void seek(long millis) {
        positionMillis = Math.max(0, Math.min(video.durationMillis(), millis));
        if (positionMillis >= video.durationMillis()) playing = false;
    }

    public synchronized void tick(long elapsedMillis) {
        if (!playing || elapsedMillis <= 0) return;
        positionMillis += elapsedMillis;
        if (positionMillis >= video.durationMillis()) {
            positionMillis = video.durationMillis();
            playing = false;
        }
    }

    public synchronized long positionMillis() { return positionMillis; }
    public synchronized boolean isPlaying() { return playing; }
    public VideoNoteContainer.Video video() { return video; }

    /** Returns the last frame whose timestamp is at or before the current position. */
    public synchronized VideoNoteContainer.Frame currentFrame() {
        List<VideoNoteContainer.Frame> frames = video.frames();
        VideoNoteContainer.Frame selected = frames.getFirst();
        int low = 0;
        int high = frames.size() - 1;

        while (low <= high) {
            int middle = (low + high) >>> 1;
            VideoNoteContainer.Frame frame = frames.get(middle);
            if (frame.timestampMillis() <= positionMillis) {
                selected = frame;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return selected;
    }
}
