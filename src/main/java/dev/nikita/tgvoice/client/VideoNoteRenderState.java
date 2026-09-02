package dev.nikita.tgvoice.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.nikita.tgvoice.network.VideoNoteContainer;
import dev.nikita.tgvoice.network.VideoNotePayload;

/** Immutable render snapshot for one video note. */
public record VideoNoteRenderState(
        String messageId,
        String senderName,
        int width,
        int height,
        long positionMillis,
        long durationMillis,
        float progress,
        boolean playing,
        NativeImage frame
) {
    public VideoNoteRenderState {
        if (messageId == null || messageId.isBlank()) throw new IllegalArgumentException("messageId is required");
        if (senderName == null || senderName.isBlank()) throw new IllegalArgumentException("senderName is required");
        if (width < 1 || height < 1) throw new IllegalArgumentException("invalid dimensions");
        if (durationMillis < 1) throw new IllegalArgumentException("invalid duration");
        if (positionMillis < 0 || positionMillis > durationMillis) throw new IllegalArgumentException("invalid position");
        if (!Float.isFinite(progress) || progress < 0.0f || progress > 1.0f) throw new IllegalArgumentException("invalid progress");
        if (frame == null) throw new IllegalArgumentException("frame is required");
        if (frame.getWidth() != width || frame.getHeight() != height) {
            throw new IllegalArgumentException("frame dimensions do not match video");
        }
    }

    public static VideoNoteRenderState from(VideoNotePayload payload, VideoNotePlayback playback,
                                            VideoNoteFrameCache cache) {
        if (payload == null) throw new IllegalArgumentException("payload is required");
        if (playback == null) throw new IllegalArgumentException("playback is required");
        if (cache == null) throw new IllegalArgumentException("cache is required");

        VideoNoteContainer.Video video = playback.video();
        long position = playback.positionMillis();
        long duration = video.durationMillis();
        float progress = duration <= 0 ? 0.0f : Math.min(1.0f, Math.max(0.0f, (float) position / (float) duration));
        NativeImage frame = cache.get(playback.currentFrame(), video.width(), video.height());

        return new VideoNoteRenderState(
                payload.messageId(), payload.senderName(), video.width(), video.height(),
                position, duration, progress, playback.isPlaying(), frame
        );
    }
}
