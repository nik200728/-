package dev.nikita.tgvoice.client;

import dev.nikita.tgvoice.network.VideoNoteContainer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-side decoded-frame cache. It intentionally uses standard ImageIO so
 * the media layer has no native video dependency. Minecraft texture upload is
 * kept in the renderer layer and never runs from the network callback.
 */
public final class VideoNoteFrameCache {
    private static final int MAX_ENTRIES = 96;
    private static final VideoNoteFrameCache INSTANCE = new VideoNoteFrameCache();

    private final Map<String, BufferedImage> frames = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private VideoNoteFrameCache() {}

    public static VideoNoteFrameCache getInstance() {
        return INSTANCE;
    }

    public synchronized BufferedImage get(String cacheKey) {
        return frames.get(cacheKey);
    }

    public synchronized BufferedImage getOrDecode(String messageId, VideoNoteContainer.Frame frame,
                                                    int expectedWidth, int expectedHeight) {
        if (messageId == null || frame == null) throw new IllegalArgumentException("frame is required");
        String key = messageId + ':' + frame.timestampMillis();
        BufferedImage cached = frames.get(key);
        if (cached != null) return cached;

        final BufferedImage decoded;
        try {
            decoded = ImageIO.read(new ByteArrayInputStream(frame.encodedImage()));
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to decode video frame", e);
        }
        if (decoded == null) throw new IllegalArgumentException("unsupported video frame image");
        if (decoded.getWidth() != expectedWidth || decoded.getHeight() != expectedHeight) {
            throw new IllegalArgumentException("video frame dimensions do not match container");
        }

        synchronized (this) {
            frames.put(key, decoded);
        }
        return decoded;
    }

    public synchronized void removeMessage(String messageId) {
        if (messageId == null) return;
        frames.keySet().removeIf(key -> key.startsWith(messageId + ':'));
    }

    public synchronized void clear() {
        frames.clear();
    }
}
