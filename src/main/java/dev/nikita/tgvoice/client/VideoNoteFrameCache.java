package dev.nikita.tgvoice.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.nikita.tgvoice.network.VideoNoteContainer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small bounded cache of decoded native Minecraft images. */
public final class VideoNoteFrameCache implements AutoCloseable {
    private static final int MAX_ENTRIES = 96;
    private final Map<FrameKey, NativeImage> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<FrameKey, NativeImage> eldest) {
            if (size() <= MAX_ENTRIES) return false;
            eldest.getValue().close();
            return true;
        }
    };

    public synchronized NativeImage get(VideoNoteContainer.Frame frame, int expectedWidth, int expectedHeight) {
        if (frame == null) return null;
        FrameKey key = new FrameKey(frame.timestampMillis(), java.util.Arrays.hashCode(frame.encodedImage()));
        NativeImage cached = cache.get(key);
        if (cached != null) return cached;
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(frame.encodedImage()));
            if (image.getWidth() != expectedWidth || image.getHeight() != expectedHeight) {
                image.close();
                throw new IllegalArgumentException("video frame dimensions do not match container");
            }
            cache.put(key, image);
            return image;
        } catch (IOException e) {
            throw new IllegalArgumentException("invalid encoded video frame", e);
        }
    }

    public synchronized void clear() {
        for (NativeImage image : cache.values()) image.close();
        cache.clear();
    }

    @Override
    public void close() { clear(); }

    private record FrameKey(long timestampMillis, int contentHash) {}
}
