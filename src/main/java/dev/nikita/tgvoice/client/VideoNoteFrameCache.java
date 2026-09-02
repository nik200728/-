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
            applyCircularAlphaMask(image);
            cache.put(key, image);
            return image;
        } catch (IOException e) {
            throw new IllegalArgumentException("invalid encoded video frame", e);
        }
    }

    /**
     * Makes the decoded frame transparent outside a centered circle. The GUI
     * can therefore use ordinary alpha blending instead of a second render pass.
     */
    private static void applyCircularAlphaMask(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        float radius = Math.min(width, height) * 0.5f;
        float centerX = (width - 1) * 0.5f;
        float centerY = (height - 1) * 0.5f;
        float radiusSquared = radius * radius;

        for (int y = 0; y < height; y++) {
            float dy = y - centerY;
            for (int x = 0; x < width; x++) {
                float dx = x - centerX;
                if (dx * dx + dy * dy > radiusSquared) {
                    int rgba = image.getPixelRGBA(x, y);
                    image.setPixelRGBA(x, y, rgba & 0x00FFFFFF);
                }
            }
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
