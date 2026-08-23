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
 * Real Plasmo Voice client integration.
 * It consumes PCM from PV's existing capture pipeline and never creates a
 * second microphone stream or sends Voice Message audio through proximity voice.
 */
@Addon(
        id = "pv-addon-tgvoice",
        name = "Telegram Voice Messages",
        version = "0.1.0",
        authors = {"nik200728"}
)
public final class PlasmoVoiceClientAddon implements AddonInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("tgvoice/plasmovoice");
    private static volatile PlasmoVoiceClientAddon instance;

    @InjectPlasmoVoice
    private PlasmoVoiceClient voiceClient;

    private volatile RecordingSession recordingSession;

    public PlasmoVoiceClientAddon() {
        instance = this;
    }

    public static PlasmoVoiceClientAddon getInstance() {
        return instance;
    }

    @Override
    public void onAddonInitialize() {
        LOGGER.info("Plasmo Voice client API connected; using existing capture pipeline");
    }

    @Override
    public void onAddonShutdown() {
        recordingSession = null;
        voiceClient = null;
        instance = null;
        LOGGER.info("Plasmo Voice client addon stopped");
    }

    public void startRecording(RecordingSession session) {
        if (session == null || !session.isActive()) {
            throw new IllegalArgumentException("Recording session must be active");
        }
        if (voiceClient == null) {
            throw new IllegalStateException("Plasmo Voice client API is not initialized");
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

        short[] samples = event.getProcessed().getMono();
        if (samples != null && samples.length > 0) {
            session.appendPcm(samples, 0, samples.length);
        }
    }
}
