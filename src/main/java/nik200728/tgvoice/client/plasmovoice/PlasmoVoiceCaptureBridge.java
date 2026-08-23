package nik200728.tgvoice.client.plasmovoice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Compatibility boundary for Plasmo Voice capture.
 *
 * The bridge deliberately exposes no proximity-voice packet path.  A future
 * Plasmo Voice adapter feeds PCM samples into {@link #acceptPcm(short[])} only
 * while a Voice Message recording is active.
 */
public final class PlasmoVoiceCaptureBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("tgvoice/plasmovoice");

    private final AtomicBoolean recording = new AtomicBoolean();
    private volatile Consumer<short[]> pcmConsumer;

    public void start(Consumer<short[]> consumer) {
        this.pcmConsumer = Objects.requireNonNull(consumer, "consumer");
        recording.set(true);
        LOGGER.debug("Voice Message capture activated");
    }

    public void stop() {
        recording.set(false);
        pcmConsumer = null;
        LOGGER.debug("Voice Message capture deactivated");
    }

    /** Called by the version-specific Plasmo Voice adapter. */
    public void acceptPcm(short[] samples) {
        if (!recording.get()) {
            return;
        }
        Consumer<short[]> consumer = pcmConsumer;
        if (consumer != null && samples.length > 0) {
            consumer.accept(samples.clone());
        }
    }

    public boolean isRecording() {
        return recording.get();
    }
}
