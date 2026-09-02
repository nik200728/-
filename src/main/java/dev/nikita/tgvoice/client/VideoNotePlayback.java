package dev.nikita.tgvoice.client;

import dev.nikita.tgvoice.network.VideoNoteContainer;

/** Client-side timeline state for one video note. It does not decode or render frames itself. */
public final class VideoNotePlayback {
    private final VideoNoteContainer.Video video;
    private long positionMillis;
    private boolean playing;
    private long lastTickNanos;

    public VideoNotePlayback(VideoNoteContainer.Video video) {
        this.video = video;
    }

    public synchronized void play() {
        if (positionMillis >= video.durationMillis()) positionMillis = 0;
        playing = true;
        lastTickNanos = System.nanoTime();
    }

    public synchronized void pause() {
        tickLocked(System.nanoTime());
        playing = false;
    }

    public synchronized void stop() {
        playing = false;
        positionMillis = 0;
        lastTickNanos = 0;
    }

    public synchronized void seek(long millis) {
        positionMillis = Math.max(0, Math.min(video.durationMillis(), millis));
        if (playing) lastTickNanos = System.nanoTime();
    }

    public synchronized void tick() {
        tickLocked(System.nanoTime());
    }

    public synchronized long positionMillis() {
        tickLocked(System.nanoTime());
        return positionMillis;
    }

    public synchronized boolean isPlaying() {
        tickLocked(System.nanoTime());
        return playing;
    }

    public synchronized VideoNoteContainer.Frame currentFrame() {
        tickLocked(System.nanoTime());
        VideoNoteContainer.Frame current = video.frames().getFirst();
        for (VideoNoteContainer.Frame frame : video.frames()) {
            if (frame.timestampMillis() > positionMillis) break;
            current = frame;
        }
        return current;
    }

    private void tickLocked(long nowNanos) {
        if (!playing) return;
        if (lastTickNanos == 0) {
            lastTickNanos = nowNanos;
            return;
        }
        long elapsed = Math.max(0, (nowNanos - lastTickNanos) / 1_000_000L);
        lastTickNanos = nowNanos;
        positionMillis += elapsed;
        if (positionMillis >= video.durationMillis()) {
            positionMillis = video.durationMillis();
            playing = false;
        }
    }
}
