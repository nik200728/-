package dev.nikita.tgvoice.network;

/**
 * Packet contract shared by the client and server sides of the addon.
 * Actual Fabric payload registration is kept in the platform layer.
 */
public final class VoiceMessagePackets {
    public static final String SEND = "send";
    public static final String DELIVER = "deliver";
    public static final String ACK = "ack";
    public static final String ERROR = "error";

    private VoiceMessagePackets() {}

    public static String validateDirection(String type) {
        return switch (type) {
            case SEND, DELIVER, ACK, ERROR -> type;
            default -> throw new IllegalArgumentException("Unknown voice message packet: " + type);
        };
    }
}
