package dev.nikita.tgvoice.network;

import java.io.*;
import java.util.UUID;

/** Small, bounded binary codec used by the Fabric networking adapter. */
public final class VoiceMessagePayloadCodec {
    private VoiceMessagePayloadCodec() {}

    public static byte[] encode(VoiceMessagePayload p) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);
        data.writeLong(p.messageId().getMostSignificantBits());
        data.writeLong(p.messageId().getLeastSignificantBits());
        data.writeLong(p.senderUuid().getMostSignificantBits());
        data.writeLong(p.senderUuid().getLeastSignificantBits());
        data.writeUTF(p.senderName());
        data.writeLong(p.durationMillis());
        byte[] audio = p.opusData();
        byte[] waveform = p.waveform();
        data.writeInt(audio.length);
        data.write(audio);
        data.writeInt(waveform.length);
        data.write(waveform);
        data.flush();
        return out.toByteArray();
    }

    public static VoiceMessagePayload decode(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            UUID messageId = new UUID(in.readLong(), in.readLong());
            UUID senderUuid = new UUID(in.readLong(), in.readLong());
            String senderName = in.readUTF();
            long duration = in.readLong();
            int audioLength = in.readInt();
            if (audioLength <= 0 || audioLength > VoiceMessagePayload.MAX_AUDIO_BYTES) throw new IOException("audio payload exceeds limit");
            byte[] audio = in.readNBytes(audioLength);
            if (audio.length != audioLength) throw new EOFException("truncated audio");
            int waveformLength = in.readInt();
            if (waveformLength <= 0 || waveformLength > VoiceMessagePayload.MAX_WAVEFORM_BYTES) throw new IOException("waveform exceeds limit");
            byte[] waveform = in.readNBytes(waveformLength);
            if (waveform.length != waveformLength) throw new EOFException("truncated waveform");
            if (in.available() != 0) throw new IOException("trailing bytes");
            return new VoiceMessagePayload(messageId, senderUuid, senderName, duration, audio, waveform);
        }
    }
}
