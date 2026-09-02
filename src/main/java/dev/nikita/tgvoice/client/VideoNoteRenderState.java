package dev.nikita.tgvoice.client;

import dev.nikita.tgvoice.network.VideoNoteContainer;

/** Immutable render snapshot produced on the client thread. */
public record VideoNoteRenderState(
        String messageId,
        String senderName,
        int width,
        int height,
        long durationMillis,
        long positionMillis,
        boolean playing,
        VideoNoteContainer.Frame frame
) {
    public float progress() {
        if (durationMillis <= 0) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, positionMillis / (float) durationMillis));
    }
}
