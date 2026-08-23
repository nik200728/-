package dev.nikita.tgvoice.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Platform-neutral binary codec used by the Fabric networking adapter.
 * Lengths are validated before allocation to avoid oversized network payloads.
 */
public final class VoiceMessageCodec {
    private static final int MAX_STRING_BYTES = 256;

    private VoiceMessageCodec() {}

    public static byte[] encode(VoiceMessagePayload payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(4096);
        DataOutputStream out = new DataOutputStream(bytes);
        writeString(out, payload.messageId());
        out.writeLong(payload.senderUuid().getMostSignificantBits());
        out.writeLong(payload.senderUuid().getLeastSignificantBits());
        writeString(out, payload.senderName());
        out.writeLong(payload.durationMillis());
        writeBytes(out, payload.opusData(), VoiceMessagePayload.MAX_AUDIO_BYTES);
        writeBytes(out, payload.waveform(), VoiceMessagePayload.MAX_WAVEFORM_BYTES);
        out.flush();
        return bytes.toByteArray();
    }

    public static VoiceMessagePayload decode(byte[] encoded) throws IOException {
        if (encoded == null || encoded.length > VoiceMessagePayload.MAX_AUDIO_BYTES + VoiceMessagePayload.MAX_WAVEFORM_BYTES + 1024) {
            throw new IOException("voice message packet is too large");
        }
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded));
        String messageId = readString(in);
        UUID senderUuid = new UUID(in.readLong(), in.readLong());
        String senderName = readString(in);
        long duration = in.readLong();
        byte[] audio = readBytes(in, VoiceMessagePayload.MAX_AUDIO_BYTES);
        byte[] waveform = readBytes(in, VoiceMessagePayload.MAX_WAVEFORM_BYTES);
        if (in.available() != 0) throw new IOException("trailing bytes in voice message packet");
        try {
            return new VoiceMessagePayload(messageId, senderUuid, senderName, duration, audio, waveform);
        } catch (IllegalArgumentException e) {
            throw new IOException("invalid voice message payload", e);
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_STRING_BYTES) throw new IOException("string too large");
        out.writeShort(data.length);
        out.write(data);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        if (length > MAX_STRING_BYTES) throw new IOException("string too large");
        return new String(in.readNBytes(length), StandardCharsets.UTF_8);
    }

    private static void writeBytes(DataOutputStream out, byte[] data, int max) throws IOException {
        if (data.length > max) throw new IOException("payload too large");
        out.writeInt(data.length);
        out.write(data);
    }

    private static byte[] readBytes(DataInputStream in, int max) throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > max) throw new IOException("invalid payload length");
        return in.readNBytes(length);
    }
}
