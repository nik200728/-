package dev.nikita.tgvoice.client;

import dev.nikita.tgvoice.network.VideoNoteContainer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small bounded cache of decoded video-note frames. */
public final class VideoNoteFrameCache {
    private static final int MAX_ENTRIES = 96;
    private final Map<FrameKey, BufferedImage> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<FrameKey, BufferedImage> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public synchronized BufferedImage get(VideoNoteContainer.Frame frame, int expectedWidth, int expectedHeight) {
        if (frame == null) return null;
        FrameKey key = new FrameKey(frame.timestampMillis(), java.util.Arrays.hashCode(frame.encodedImage()));
        BufferedImage cached = cache.get(key);
        if (cached != null) return cached;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(frame.encodedImage()));
            if (image == null || image.getWidth() != expectedWidth || image.getHeight() != expectedHeight) {
                throw new IllegalArgumentException("video frame dimensions do not match container");
            }
            cache.put(key, image);
            return image;
        } catch (IOException e) {
            throw new IllegalArgumentException("invalid encoded video frame", e);
        }
    }

    public synchronized void clear() { cache.clear(); }

    private record FrameKey(long timestampMillis, int contentHash) {}
}
