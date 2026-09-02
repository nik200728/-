package dev.nikita.tgvoice.client;

import dev.nikita.tgvoice.network.VideoNoteContainer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/** Decodes one independently encoded video-note frame without a native video library. */
public final class VideoNoteFrameDecoder {
    private VideoNoteFrameDecoder() {}

    public static BufferedImage decode(VideoNoteContainer.Frame frame, int expectedWidth, int expectedHeight) {
        if (frame == null) throw new IllegalArgumentException("frame is required");
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(frame.encodedImage()));
            if (image == null) throw new IllegalArgumentException("unsupported encoded video frame");
            if (image.getWidth() != expectedWidth || image.getHeight() != expectedHeight) {
                throw new IllegalArgumentException("video frame dimensions do not match container");
            }
            return image;
        } catch (IOException exception) {
            throw new IllegalArgumentException("failed to decode video frame", exception);
        }
    }
}
