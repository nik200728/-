package dev.nikita.tgvoice.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;

/** Owns one dynamic GPU texture for the currently rendered video-note frame. */
public final class VideoNoteTextureManager implements AutoCloseable {
    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath("tgvoice", "video_note_frame");

    private final TextureManager textureManager;
    private DynamicTexture texture;

    public VideoNoteTextureManager(Minecraft client) {
        this.textureManager = Objects.requireNonNull(client.getTextureManager(), "texture manager");
    }

    /**
     * Uploads an encoded frame into an addon-owned DynamicTexture.
     *
     * The texture manager deliberately decodes its own NativeImage instead of
     * taking ownership of the frame-cache image. This keeps the bounded CPU
     * cache and the GPU texture lifetime independent and avoids relying on a
     * version-specific NativeImage copy API.
     */
    public Identifier upload(byte[] encodedImage, int expectedWidth, int expectedHeight) {
        if (encodedImage == null || encodedImage.length == 0) {
            throw new IllegalArgumentException("encoded image is required");
        }
        if (expectedWidth < 1 || expectedHeight < 1) {
            throw new IllegalArgumentException("invalid expected dimensions");
        }

        closeTexture();
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(encodedImage));
            if (image.getWidth() != expectedWidth || image.getHeight() != expectedHeight) {
                image.close();
                throw new IllegalArgumentException("video frame dimensions do not match video");
            }
            texture = new DynamicTexture(image);
            textureManager.register(TEXTURE_ID, texture);
            return TEXTURE_ID;
        } catch (IOException exception) {
            throw new IllegalArgumentException("invalid encoded video frame", exception);
        }
    }

    public void clear() {
        closeTexture();
    }

    @Override
    public void close() {
        clear();
    }

    private void closeTexture() {
        if (texture != null) {
            texture.close();
            textureManager.release(TEXTURE_ID);
            texture = null;
        }
    }
}
