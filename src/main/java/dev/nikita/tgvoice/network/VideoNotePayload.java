package dev.nikita.tgvoice.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.UUID;

/** Wire payload for a short circular video message. */
public record VideoNotePayload(
        String messageId,
        UUID senderUuid,
        String senderName,
        long durationMillis,
        int width,
        int height,
        int frameRate,
        byte[] videoData
) implements CustomPacketPayload {
    public static final int MAX_VIDEO_BYTES = 8 * 1024 * 1024;
    public static final long MAX_DURATION_MILLIS = 60_000;
    public static final int MAX_DIMENSION = 512;
    public static final int MAX_FRAME_RATE = 30;

    public static final Type<VideoNotePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("tgvoice", "video_note"));

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC = StreamCodec.of(
            (buf, uuid) -> {
                buf.writeLong(uuid.getMostSignificantBits());
                buf.writeLong(uuid.getLeastSignificantBits());
            },
            buf -> new UUID(buf.readLong(), buf.readLong())
    );

    private static final StreamCodec<RegistryFriendlyByteBuf, byte[]> VIDEO_CODEC = StreamCodec.of(
            (buf, value) -> buf.writeByteArray(value),
            buf -> buf.readByteArray(MAX_VIDEO_BYTES)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, VideoNotePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            VideoNotePayload::messageId,
            UUID_CODEC,
            VideoNotePayload::senderUuid,
            ByteBufCodecs.STRING_UTF8,
            VideoNotePayload::senderName,
            ByteBufCodecs.VAR_LONG,
            VideoNotePayload::durationMillis,
            ByteBufCodecs.VAR_INT,
            VideoNotePayload::width,
            ByteBufCodecs.VAR_INT,
            VideoNotePayload::height,
            ByteBufCodecs.VAR_INT,
            VideoNotePayload::frameRate,
            VIDEO_CODEC,
            VideoNotePayload::videoData,
            VideoNotePayload::new
    );

    public VideoNotePayload {
        if (messageId == null || messageId.isBlank() || messageId.length() > 64) {
            throw new IllegalArgumentException("invalid messageId");
        }
        if (senderUuid == null) throw new IllegalArgumentException("senderUuid is required");
        if (senderName == null || senderName.isBlank() || senderName.length() > 64) {
            throw new IllegalArgumentException("invalid senderName");
        }
        if (durationMillis < 1 || durationMillis > MAX_DURATION_MILLIS) {
            throw new IllegalArgumentException("invalid duration");
        }
        if (width < 1 || width > MAX_DIMENSION || height < 1 || height > MAX_DIMENSION) {
            throw new IllegalArgumentException("invalid dimensions");
        }
        if (frameRate < 1 || frameRate > MAX_FRAME_RATE) {
            throw new IllegalArgumentException("invalid frame rate");
        }
        if (videoData == null || videoData.length == 0 || videoData.length > MAX_VIDEO_BYTES) {
            throw new IllegalArgumentException("invalid video payload");
        }
        videoData = Arrays.copyOf(videoData, videoData.length);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public byte[] videoData() {
        return Arrays.copyOf(videoData, videoData.length);
    }
}
