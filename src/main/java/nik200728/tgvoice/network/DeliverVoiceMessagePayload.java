package nik200728.tgvoice.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Server -> client payload containing a validated Voice Message. */
public record DeliverVoiceMessagePayload(
        UUID messageId,
        UUID senderUuid,
        String senderName,
        int durationMillis,
        byte[] opusOgg,
        short[] waveform
) implements CustomPacketPayload {
    public static final int MAX_DURATION_MS = 60_000;
    public static final int MAX_AUDIO_BYTES = 2 * 1024 * 1024;
    public static final int MAX_WAVEFORM_POINTS = 256;
    public static final int MAX_NAME_CHARS = 32;
    public static final Type<DeliverVoiceMessagePayload> TYPE = new Type<>(Identifier.parse("tgvoice:deliver_voice_message"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeliverVoiceMessagePayload> CODEC =
            StreamCodec.of(DeliverVoiceMessagePayload::write, DeliverVoiceMessagePayload::read);

    public DeliverVoiceMessagePayload {
        if (messageId == null || senderUuid == null) throw new IllegalArgumentException("IDs are required");
        if (senderName == null || senderName.length() > MAX_NAME_CHARS) throw new IllegalArgumentException("invalid sender name");
        if (durationMillis <= 0 || durationMillis > MAX_DURATION_MS) throw new IllegalArgumentException("invalid duration");
        if (opusOgg == null || opusOgg.length == 0 || opusOgg.length > MAX_AUDIO_BYTES) throw new IllegalArgumentException("invalid audio");
        if (waveform == null || waveform.length == 0 || waveform.length > MAX_WAVEFORM_POINTS) throw new IllegalArgumentException("invalid waveform");
        opusOgg = opusOgg.clone();
        waveform = waveform.clone();
    }

    private static void write(RegistryFriendlyByteBuf buf, DeliverVoiceMessagePayload p) {
        buf.writeUUID(p.messageId());
        buf.writeUUID(p.senderUuid());
        buf.writeUtf(p.senderName(), MAX_NAME_CHARS);
        buf.writeVarInt(p.durationMillis());
        buf.writeByteArray(p.opusOgg());
        buf.writeVarInt(p.waveform().length);
        for (short value : p.waveform()) buf.writeShort(value);
    }

    private static DeliverVoiceMessagePayload read(RegistryFriendlyByteBuf buf) {
        UUID messageId = buf.readUUID();
        UUID senderUuid = buf.readUUID();
        String senderName = buf.readUtf(MAX_NAME_CHARS);
        int duration = buf.readVarInt();
        byte[] audio = buf.readByteArray(MAX_AUDIO_BYTES);
        int points = buf.readVarInt();
        if (points <= 0 || points > MAX_WAVEFORM_POINTS) throw new IllegalArgumentException("invalid waveform size");
        short[] waveform = new short[points];
        for (int i = 0; i < points; i++) waveform[i] = buf.readShort();
        return new DeliverVoiceMessagePayload(messageId, senderUuid, senderName, duration, audio, waveform);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @Override
    public byte[] opusOgg() { return opusOgg.clone(); }

    @Override
    public short[] waveform() { return waveform.clone(); }
}
