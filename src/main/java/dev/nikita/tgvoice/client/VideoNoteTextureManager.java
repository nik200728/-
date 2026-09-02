package dev.nikita.tgvoice.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/** Owns one dynamic GPU texture for the currently rendered video-note frame. */
public final class VideoNoteTextureManager implements AutoCloseable {
    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath("tgvoice", "video_note_frame");

    private final TextureManager textureManager;
    private DynamicTexture texture;
    private byte[] uploadedImage;
    private int uploadedWidth;
    private int uploadedHeight;

    public VideoNoteTextureManager(Minecraft client) {
        this.textureManager = Objects.requireNonNull(client.getTextureManager(), "texture manager");
    }

    /** Decodes and uploads a frame owned by the GPU texture. */
    public Identifier upload(byte[] encodedImage, int expectedWidth, int expectedHeight) {
        if (encodedImage == null || encodedImage.length == 0) {
            throw new IllegalArgumentException("encoded image is required");
        }
        if (expectedWidth < 1 || expectedHeight < 1) {
            throw new IllegalArgumentException("invalid expected dimensions");
        }

        // Compare the actual bytes rather than relying on Arrays.hashCode alone: different
        // JPEG payloads can have the same 32-bit hash and would otherwise leave a stale frame.
        if (texture != null && uploadedWidth == expectedWidth && uploadedHeight == expectedHeight
                && Arrays.equals(uploadedImage, encodedImage)) {
            return TEXTURE_ID;
        }

        closeTexture();
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(encodedImage));
            if (image.getWidth() != expectedWidth || image.getHeight() != expectedHeight) {
                image.close();
                throw new IllegalArgumentException("video frame dimensions do not match video");
            }
            applyCircularAlphaMask(image);
            texture = new DynamicTexture(() -> "tgvoice-video-note", image);
            textureManager.register(TEXTURE_ID, texture);
            uploadedImage = Arrays.copyOf(encodedImage, encodedImage.length);
            uploadedWidth = expectedWidth;
            uploadedHeight = expectedHeight;
            return TEXTURE_ID;
        } catch (IOException exception) {
            throw new IllegalArgumentException("invalid encoded video frame", exception);
        }
    }

    public void clear() { closeTexture(); }

    @Override
    public void close() { clear(); }

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
                    int rgba = image.getPixel(x, y);
                    image.setPixel(x, y, rgba & 0x00FFFFFF);
                }
            }
        }
    }

    private void closeTexture() {
        if (texture != null) {
            textureManager.release(TEXTURE_ID);
            texture = null;
        }
        uploadedImage = null;
        uploadedWidth = 0;
        uploadedHeight = 0;
    }
}
