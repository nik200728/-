package dev.nikita.tgvoice.client;

import java.util.Objects;

/** Coordinates explicit Voice Message recording without touching proximity voice state. */
public final class RecordingController {
    private final PlasmoVoiceAdapter adapter;
    private RecordingSession session;

    public RecordingController(PlasmoVoiceAdapter adapter) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
    }

    public synchronized boolean start() {
        if (!adapter.isAvailable() || session != null) return false;
        session = new RecordingSession();
        adapter.startCapture(session);
        return true;
    }

    public synchronized RecordingSession finish() {
        if (session == null) return null;
        adapter.stopCapture();
        session.finish();
        RecordingSession result = session;
        session = null;
        return result;
    }

    public synchronized void cancel() {
        if (session == null) return;
        adapter.stopCapture();
        session.cancel();
        session = null;
    }

    public synchronized boolean isRecording() {
        return session != null && session.isActive();
    }
}
