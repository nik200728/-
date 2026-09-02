package dev.nikita.tgvoice.client;

import dev.nikita.tgvoice.network.VideoNoteContainer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/** Decodes one independently encoded video-note frame for the client renderer. */
public final class VideoNoteFrameDecoder {
    private VideoNoteFrameDecoder() {}

    public static BufferedImage decode(VideoNoteContainer.Video video, VideoNoteContainer.Frame frame) {
        if (video == null || frame == null) throw new IllegalArgumentException("video and frame are required");
        try (ByteArrayInputStream input = new ByteArrayInputStream(frame.encodedImage())) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) throw new IllegalArgumentException("unsupported encoded video frame");
            if (image.getWidth() != video.width() || image.getHeight() != video.height()) {
                throw new IllegalArgumentException("video frame dimensions do not match container");
            }
            return image;
        } catch (IOException exception) {
            throw new IllegalArgumentException("failed to decode video frame", exception);
        }
    }
}
