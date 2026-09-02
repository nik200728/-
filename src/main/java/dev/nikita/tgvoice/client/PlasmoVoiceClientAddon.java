package dev.nikita.tgvoice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.audio.codec.AudioEncoder;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.event.audio.capture.AudioCaptureProcessedEvent;
import su.plo.voice.api.event.EventSubscribe;

import java.util.ArrayList;
import java.util.List;

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
    private static final int FRAME_SAMPLES = 960;
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

    /**
     * Encodes the completed recording with a fresh encoder created by Plasmo Voice.
     * The normal proximity encoder is never reused or reset, so the addon cannot
     * corrupt the active proximity-voice encoder state.
     */
    public byte[] encodeAsOggOpus(RecordingSession session) {
        if (voiceClient == null) {
            throw new IllegalStateException("Plasmo Voice client API is not initialized");
        }
        if (session == null) {
            throw new IllegalArgumentException("Recording session is required");
        }

        byte[] pcm = session.pcm16le();
        if (pcm.length < 2) {
            throw new IllegalArgumentException("Recording contains no PCM audio");
        }

        AudioEncoder encoder = voiceClient.getServerInfo()
                .orElseThrow(() -> new IllegalStateException("Plasmo Voice server is not connected"))
                .createOpusEncoder(false);

        List<byte[]> packets = new ArrayList<>();
        int totalSamples = pcm.length / 2;
        try {
            encoder.open();
            for (int offset = 0; offset < totalSamples; offset += FRAME_SAMPLES) {
                int frameLength = Math.min(FRAME_SAMPLES, totalSamples - offset);
                short[] frame = new short[FRAME_SAMPLES];
                for (int i = 0; i < frameLength; i++) {
                    int pcmOffset = (offset + i) * 2;
                    frame[i] = (short) ((pcm[pcmOffset] & 0xFF) | (pcm[pcmOffset + 1] << 8));
                }
                // PV's Opus encoder expects 20 ms / 48 kHz frames.
                packets.add(encoder.encode(frame));
            }
            return OpusOggWriter.write(packets, totalSamples);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode voice message with Plasmo Voice", e);
        } finally {
            try {
                encoder.close();
            } catch (Exception e) {
                LOGGER.warn("Failed to close temporary Plasmo Voice encoder", e);
            }
        }
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
