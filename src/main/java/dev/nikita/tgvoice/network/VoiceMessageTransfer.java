package dev.nikita.tgvoice.network;

import java.util.UUID;

/**
 * Transport-neutral transfer contract. The Fabric payload implementation can
 * serialize this object without coupling audio/domain code to a specific
 * Minecraft networking API.
 */
public record VoiceMessageTransfer(
        UUID messageId,
        UUID senderUuid,
        int durationMs,
        byte[] audio,
        byte[] waveform
) {
    public static final int MAX_AUDIO_BYTES = 2 * 1024 * 1024;
    public static final int MAX_WAVEFORM_BYTES = 1024;

    public VoiceMessageTransfer {
        if (messageId == null || senderUuid == null) throw new IllegalArgumentException("missing id");
        if (durationMs <= 0 || durationMs > 60_000) throw new IllegalArgumentException("invalid duration");
        if (audio == null || audio.length == 0 || audio.length > MAX_AUDIO_BYTES) throw new IllegalArgumentException("invalid audio");
        if (waveform == null || waveform.length == 0 || waveform.length > MAX_WAVEFORM_BYTES) throw new IllegalArgumentException("invalid waveform");
        audio = audio.clone();
        waveform = waveform.clone();
    }

    @Override public byte[] audio() { return audio.clone(); }
    @Override public byte[] waveform() { return waveform.clone(); }
}
