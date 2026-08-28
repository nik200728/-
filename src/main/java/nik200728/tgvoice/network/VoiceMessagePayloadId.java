package nik200728.tgvoice.network;

/** Stable identifier used by the Minecraft custom-payload layer. */
public final class VoiceMessagePayloadId {
    public static final String NAMESPACE = "tgvoice";
    public static final String SEND = NAMESPACE + ":send_voice_message";
    public static final String DELIVER = NAMESPACE + ":deliver_voice_message";

    private VoiceMessagePayloadId() {}
}
