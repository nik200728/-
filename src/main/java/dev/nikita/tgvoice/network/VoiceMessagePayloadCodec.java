package dev.nikita.tgvoice.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Bounded wire codec for VoiceMessagePayload. */
public final class VoiceMessagePayloadCodec {
    private static final int MAX_MESSAGE_ID_BYTES = 64;
    private static final int MAX_SENDER_NAME_BYTES = 64;

    private VoiceMessagePayloadCodec() {}

    public static byte[] encode(VoiceMessagePayload p) throws IOException {
        byte[] messageId = p.messageId().getBytes(StandardCharsets.UTF_8);
        byte[] senderName = p.senderName().getBytes(StandardCharsets.UTF_8);
        if (messageId.length > MAX_MESSAGE_ID_BYTES || senderName.length > MAX_SENDER_NAME_BYTES) {
            throw new IOException("metadata exceeds limit");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);
        writeBytes(data, messageId);
        writeUuid(data, p.senderUuid());
        writeBytes(data, senderName);
        data.writeLong(p.durationMillis());
        writeBytes(data, p.opusData());
        writeBytes(data, p.waveform());
        data.flush();
        return out.toByteArray();
    }

    public static VoiceMessagePayload decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > VoiceMessagePayload.MAX_AUDIO_BYTES + VoiceMessagePayload.MAX_WAVEFORM_BYTES + 256) {
            throw new IOException("payload exceeds limit");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            String messageId = new String(readBytes(in, MAX_MESSAGE_ID_BYTES), StandardCharsets.UTF_8);
            UUID senderUuid = readUuid(in);
            String senderName = new String(readBytes(in, MAX_SENDER_NAME_BYTES), StandardCharsets.UTF_8);
            long duration = in.readLong();
            byte[] audio = readBytes(in, VoiceMessagePayload.MAX_AUDIO_BYTES);
            byte[] waveform = readBytes(in, VoiceMessagePayload.MAX_WAVEFORM_BYTES);
            if (in.available() != 0) throw new IOException("trailing bytes");
            return new VoiceMessagePayload(messageId, senderUuid, senderName, duration, audio, waveform);
        } catch (EOFException e) {
            throw new IOException("truncated payload", e);
        }
    }

    private static void writeBytes(DataOutputStream out, byte[] value) throws IOException {
        if (value.length > Integer.MAX_VALUE) throw new IOException("value too large");
        out.writeInt(value.length);
        out.write(value);
    }

    private static byte[] readBytes(DataInputStream in, int max) throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > max) throw new IOException("field exceeds limit");
        byte[] value = in.readNBytes(length);
        if (value.length != length) throw new EOFException();
        return value;
    }

    private static void writeUuid(DataOutputStream out, UUID uuid) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }
}
