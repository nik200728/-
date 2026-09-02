package dev.nikita.tgvoice.client;

import dev.nikita.tgvoice.network.VideoNoteContainer;
import dev.nikita.tgvoice.network.VideoNotePayload;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Records bounded webcam JPEG frames into the TGV1 transport container.
 * Capture runs on a dedicated thread so the Minecraft render/tick thread is
 * never blocked by a camera driver.
 */
public final class VideoNoteRecorder implements AutoCloseable {
    public static final int FRAME_RATE = 15;
    public static final long FRAME_INTERVAL_MILLIS = 1000L / FRAME_RATE;
    public static final long MAX_DURATION_MILLIS = VideoNotePayload.MAX_DURATION_MILLIS;

    private final WebcamCaptureService camera;
    private final Object lock = new Object();
    private final List<VideoNoteContainer.Frame> frames = new ArrayList<>();

    private Thread worker;
    private long startedAtNanos;
    private volatile boolean recording;
    private volatile boolean cancelled;

    public VideoNoteRecorder(WebcamCaptureService camera) {
        this.camera = camera;
    }

    public synchronized void start() throws Exception {
        if (recording) throw new IllegalStateException("video recording is already active");
        camera.open();
        synchronized (lock) {
            frames.clear();
        }
        cancelled = false;
        recording = true;
        startedAtNanos = System.nanoTime();
        worker = new Thread(this::captureLoop, "tgvoice-video-capture");
        worker.setDaemon(true);
        worker.start();
    }

    public boolean isRecording() {
        return recording;
    }

    /** Stops recording and returns a validated network payload. */
    public VideoNotePayload stop(UUID senderUuid, String senderName) throws IOException {
        finish(false);
        List<VideoNoteContainer.Frame> snapshot;
        synchronized (lock) {
            snapshot = List.copyOf(frames);
        }
        if (snapshot.isEmpty()) throw new IllegalStateException("No webcam frames captured");

        long duration = Math.max(1L, elapsedMillis());
        duration = Math.min(MAX_DURATION_MILLIS, duration);
        // The container requires every frame timestamp to be strictly inside duration.
        long lastTimestamp = snapshot.get(snapshot.size() - 1).timestampMillis();
        if (lastTimestamp >= duration) duration = Math.min(MAX_DURATION_MILLIS, lastTimestamp + 1);

        int size = WebcamCaptureService.TARGET_SIZE;
        VideoNoteContainer.Video video = new VideoNoteContainer.Video(size, size, FRAME_RATE, duration, snapshot);
        byte[] data = VideoNoteContainer.encode(video);
        return new VideoNotePayload(UUID.randomUUID().toString(), senderUuid, senderName,
                duration, size, size, FRAME_RATE, data);
    }

    public void cancel() {
        finish(true);
        synchronized (lock) {
            frames.clear();
        }
    }

    @Override
    public void close() {
        cancel();
        camera.close();
    }

    private void captureLoop() {
        long nextFrameAt = System.nanoTime();
        try {
            while (recording && !cancelled) {
                long elapsed = elapsedMillis();
                if (elapsed >= MAX_DURATION_MILLIS) break;

                byte[] jpeg = camera.captureJpeg();
                if (jpeg != null && jpeg.length > 0 && jpeg.length <= 512 * 1024) {
                    synchronized (lock) {
                        frames.add(new VideoNoteContainer.Frame(Math.max(1L, elapsed), jpeg));
                    }
                }

                nextFrameAt += FRAME_INTERVAL_MILLIS * 1_000_000L;
                long sleepNanos = nextFrameAt - System.nanoTime();
                if (sleepNanos > 0) {
                    try {
                        Thread.sleep(sleepNanos / 1_000_000L, (int) (sleepNanos % 1_000_000L));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    nextFrameAt = System.nanoTime();
                }
            }
        } catch (Exception ignored) {
            // stop() reports the lack of captured frames; the UI can surface a
            // concise camera error without crashing the client.
        } finally {
            recording = false;
        }
    }

    private void finish(boolean cancel) {
        Thread thread;
        synchronized (this) {
            cancelled = cancel;
            recording = false;
            thread = worker;
            worker = null;
        }
        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
            try {
                thread.join(1500L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private long elapsedMillis() {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
}
