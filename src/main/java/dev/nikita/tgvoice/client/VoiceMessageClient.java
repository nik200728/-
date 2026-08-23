package dev.nikita.tgvoice.client;

/** Client-side state machine for explicit Voice Messages only. */
public final class VoiceMessageClient {
    private static final VoiceMessageClient INSTANCE = new VoiceMessageClient();
    private static final long MAX_DURATION_MS = 120_000L;

    private RecordingSession session;

    private VoiceMessageClient() {}

    public static VoiceMessageClient getInstance() {
        return INSTANCE;
    }

    public synchronized boolean startRecording() {
        if (session != null && session.isActive()) return false;

        PlasmoVoiceClientAddon addon = PlasmoVoiceClientAddon.getInstance();
        if (addon == null || !addon.isAvailable()) return false;

        RecordingSession next = new RecordingSession();
        addon.startRecording(next);
        session = next;
        return true;
    }

    public synchronized RecordingSession finishRecording() {
        if (session == null) return null;

        PlasmoVoiceClientAddon addon = PlasmoVoiceClientAddon.getInstance();
        if (addon != null) addon.stopRecording();

        session.finish();
        RecordingSession result = session;
        session = null;
        return result;
    }

    public synchronized void cancelRecording() {
        PlasmoVoiceClientAddon addon = PlasmoVoiceClientAddon.getInstance();
        if (addon != null) addon.stopRecording();
        if (session != null) session.cancel();
        session = null;
    }

    public synchronized void enforceMaximumDuration() {
        if (session != null && session.isActive() && session.durationMillis() >= MAX_DURATION_MS) {
            finishRecording();
        }
    }

    public synchronized boolean isRecording() {
        return session != null && session.isActive();
    }

    public synchronized long durationMillis() {
        return session == null ? 0L : session.durationMillis();
    }
}
