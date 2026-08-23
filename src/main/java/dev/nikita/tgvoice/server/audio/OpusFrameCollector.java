package dev.nikita.tgvoice.server.audio;

import java.io.ByteArrayOutputStream;
import java.util.List;

/** Collects independently encoded Opus packets while preserving frame order. */
public final class OpusFrameCollector {
    private final ByteArrayOutputStream data = new ByteArrayOutputStream();
    private int frameCount;

    public void append(List<byte[]> frames) {
        if (frames == null) return;
        for (byte[] frame : frames) {
            if (frame == null || frame.length == 0) continue;
            data.writeBytes(frame);
            frameCount++;
        }
    }

    public int frameCount() {
        return frameCount;
    }

    public byte[] toByteArray() {
        return data.toByteArray();
    }

    public void reset() {
        data.reset();
        frameCount = 0;
    }
}
