package dev.nikita.tgvoice.client;

/** Client-side state machine. It never intercepts normal Plasmo Voice proximity traffic. */
public final class VoiceMessageClient {
    private static final VoiceMessageClient INSTANCE = new VoiceMessageClient();
    private static final long MAX_DURATION_MS = 120_000L;

    private RecordingSession session;

    private VoiceMessageClient() {}

    public static VoiceMessageClient getInstance() { return INSTANCE; }

    public synchronized boolean startRecording() {
        if (session != null && session.isActive()) return false;
        session = new RecordingSession();
        return true;
    }

    public synchronized void appendSamples(short[] samples, int offset, int length) {
        if (session == null || !session.isActive()) return;
        if (session.durationMillis() >= MAX_DURATION_MS) {
            session.finish();
            return;
        }
        session.appendPcm(samples, offset, length);
    }

    public synchronized RecordingSession finishRecording() {
        if (session == null) return null;
        session.finish();
        RecordingSession result = session;
        session = null;
        return result;
    }

    public synchronized void cancelRecording() {
        if (session != null) session.cancel();
        session = null;
    }

    public synchronized boolean isRecording() {
        return session != null && session.isActive();
    }

    public synchronized long durationMillis() {
        return session == null ? 0L : session.durationMillis();
    }
}
