package nik200728.tgvoice.client;

import java.util.concurrent.atomic.AtomicReference;

/** Client-side state for an explicit Voice Message recording session.
 * This state is deliberately separate from Plasmo Voice proximity activation.
 */
public final class VoiceMessageState {
    public enum Status { IDLE, RECORDING, CANCELLING, SENDING, FAILED }

    private final AtomicReference<Status> status = new AtomicReference<>(Status.IDLE);
    private volatile long startedAtNanos;
    private volatile long maxDurationMillis = 120_000L;

    public Status status() { return status.get(); }
    public boolean isRecording() { return status.get() == Status.RECORDING; }
    public long elapsedMillis() {
        if (!isRecording()) return 0L;
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
    public void setMaxDurationMillis(long value) { maxDurationMillis = Math.max(1_000L, value); }
    public boolean begin() {
        if (!status.compareAndSet(Status.IDLE, Status.RECORDING)) return false;
        startedAtNanos = System.nanoTime();
        return true;
    }
    public boolean shouldAutoStop() { return isRecording() && elapsedMillis() >= maxDurationMillis; }
    public boolean cancel() {
        return status.compareAndSet(Status.RECORDING, Status.CANCELLING);
    }
    public boolean finishForSend() {
        return status.compareAndSet(Status.RECORDING, Status.SENDING);
    }
    public void fail() { status.set(Status.FAILED); }
    public void reset() { status.set(Status.IDLE); startedAtNanos = 0L; }
}
