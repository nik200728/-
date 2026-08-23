package dev.nikita.tgvoice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.event.audio.capture.AudioCaptureProcessedEvent;
import su.plo.voice.api.event.EventSubscribe;

/**
 * Plasmo Voice client addon integration.
 *
 * This listens to PV's already-open microphone pipeline. It does not create a
 * second input device and it never sends these samples through PV's proximity
 * voice activation/protocol.
 */
@Addon(
        id = "pv-addon-tgvoice",
        name = "Telegram Voice Messages",
        version = "0.1.0",
        authors = {"nik200728"}
)
public final class PlasmoVoiceClientAddon implements AddonInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("tgvoice/plasmovoice");

    @InjectPlasmoVoice
    private PlasmoVoiceClient voiceClient;

    private volatile RecordingSession recordingSession;

    @Override
    public void onAddonInitialize() {
        LOGGER.info("Plasmo Voice client API connected");
    }

    @Override
    public void onAddonShutdown() {
        recordingSession = null;
        LOGGER.info("Plasmo Voice client addon stopped");
    }

    /** Starts collecting processed mono PCM from PV's existing capture pipeline. */
    public void startRecording(RecordingSession session) {
        if (session == null || !session.isActive()) {
            throw new IllegalArgumentException("Recording session must be active");
        }
        recordingSession = session;
    }

    public void stopRecording() {
        recordingSession = null;
    }

    public boolean isAvailable() {
        return voiceClient != null;
    }

    @EventSubscribe
    public void onAudioCaptureProcessed(AudioCaptureProcessedEvent event) {
        RecordingSession session = recordingSession;
        if (session == null || !session.isActive()) {
            return;
        }

        // Use PV's processed mono samples. This keeps the selected PV input
        // device and its processing chain while avoiding a second microphone.
        short[] samples = event.getProcessed().getMono();
        if (samples != null && samples.length > 0) {
            session.appendPcm(samples, 0, samples.length);
        }
    }
}
