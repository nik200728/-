package dev.nikita.tgvoice.client;

import dev.nikita.tgvoice.network.VideoNoteContainer;
import dev.nikita.tgvoice.network.VideoNotePayload;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Records bounded webcam JPEG frames into the TGV1 transport container. */
public final class VideoNoteRecorder implements AutoCloseable {
    public static final int FRAME_RATE = 15;
    public static final long FRAME_INTERVAL_MILLIS = 1000L / FRAME_RATE;
    public static final long MAX_DURATION_MILLIS = VideoNotePayload.MAX_DURATION_MILLIS;
    private static final int MAX_FRAME_BYTES = 512 * 1024;
    private static final int MAX_FRAMES = 900;
    private static final int MAX_VIDEO_BYTES = VideoNotePayload.MAX_VIDEO_BYTES;
    private static final long WORKER_JOIN_TIMEOUT_MILLIS = 3000L;

    private final WebcamCaptureService camera;
    private final Object lock = new Object();
    private final List<VideoNoteContainer.Frame> frames = new ArrayList<>();

    /** Exact encoded container size of the frames currently held in memory. */
    private int encodedBytes = VideoNoteContainer.HEADER_BYTES;

    private Thread worker;
    private long startedAtNanos;
    private volatile boolean recording;
    private volatile boolean cancelled;
    private volatile String failure;

    public VideoNoteRecorder(WebcamCaptureService camera) {
        this.camera = camera;
    }

    public synchronized void start() throws Exception {
        if (recording) throw new IllegalStateException("video recording is already active");
        if (worker != null && worker.isAlive()) throw new IllegalStateException("previous video capture worker is still stopping");
        camera.open();
        synchronized (lock) {
            frames.clear();
            encodedBytes = VideoNoteContainer.HEADER_BYTES;
        }
        cancelled = false;
        failure = null;
        recording = true;
        startedAtNanos = System.nanoTime();
        worker = new Thread(this::captureLoop, "tgvoice-video-capture");
        worker.setDaemon(true);
        worker.start();
    }

    public boolean isRecording() {
        return recording;
    }

    public String failure() {
        return failure;
    }

    public VideoNotePayload stop(UUID senderUuid, String senderName) throws IOException {
        finish(false);
        List<VideoNoteContainer.Frame> snapshot;
        synchronized (lock) {
            snapshot = List.copyOf(frames);
        }
        if (snapshot.isEmpty()) {
            String reason = failure;
            throw new IllegalStateException(reason == null ? "No webcam frames captured" : reason);
        }

        long duration = Math.max(1L, elapsedMillis());
        duration = Math.min(MAX_DURATION_MILLIS, duration);
        long lastTimestamp = snapshot.get(snapshot.size() - 1).timestampMillis();
        if (lastTimestamp >= duration) duration = Math.min(MAX_DURATION_MILLIS, lastTimestamp + 1);

        int size = WebcamCaptureService.TARGET_SIZE;
        VideoNoteContainer.Video video = new VideoNoteContainer.Video(size, size, FRAME_RATE, duration, snapshot);
        byte[] data = VideoNoteContainer.encode(video);
        if (data.length > MAX_VIDEO_BYTES) {
            throw new IllegalStateException("Video note exceeds the 8 MiB transport limit");
        }
        return new VideoNotePayload(UUID.randomUUID().toString(), senderUuid, senderName,
                duration, size, size, FRAME_RATE, data);
    }

    public void cancel() {
        finish(true);
        synchronized (lock) {
            frames.clear();
            encodedBytes = VideoNoteContainer.HEADER_BYTES;
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
                if (elapsed >= MAX_DURATION_MILLIS || frameCount() >= MAX_FRAMES) break;

                byte[] jpeg = camera.captureJpeg();
                if (jpeg != null && jpeg.length > 0 && jpeg.length <= MAX_FRAME_BYTES) {
                    synchronized (lock) {
                        int frameCost = VideoNoteContainer.FRAME_OVERHEAD_BYTES + jpeg.length;
                        if (encodedBytes + frameCost <= MAX_VIDEO_BYTES) {
                            frames.add(new VideoNoteContainer.Frame(Math.max(1L, elapsed), jpeg));
                            encodedBytes += frameCost;
                        } else {
                            failure = "Video size limit reached";
                            recording = false;
                            break;
                        }
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
        } catch (Exception exception) {
            failure = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        } finally {
            recording = false;
        }
    }

    private int frameCount() {
        synchronized (lock) {
            return frames.size();
        }
    }

    private void finish(boolean cancel) {
        Thread thread;
        synchronized (this) {
            cancelled = cancel;
            recording = false;
            thread = worker;
        }
        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
            try {
                thread.join(WORKER_JOIN_TIMEOUT_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            synchronized (this) {
                if (worker == thread && !thread.isAlive()) worker = null;
                else if (worker == thread && failure == null) failure = "Webcam capture worker did not stop cleanly";
            }
        }
    }

    private long elapsedMillis() {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
}
