package dev.nikita.tgvoice.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Owns one dynamic GPU texture for the currently rendered video-note frame.
 * The texture receives its own NativeImage copy so the bounded frame cache
 * keeps ownership of its decoded image safely across frame changes.
 */
public final class VideoNoteTextureManager implements AutoCloseable {
    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath("tgvoice", "video_note_frame");

    private final TextureManager textureManager;
    private DynamicTexture texture;
    private NativeImage uploadedImage;

    public VideoNoteTextureManager(Minecraft client) {
        this.textureManager = Objects.requireNonNull(client.getTextureManager(), "texture manager");
    }

    public Identifier upload(NativeImage image) {
        if (image == null) throw new IllegalArgumentException("image is required");
        if (uploadedImage == image && texture != null) return TEXTURE_ID;

        closeTexture();
        texture = new DynamicTexture(image.copy());
        uploadedImage = image;
        textureManager.register(TEXTURE_ID, texture);
        return TEXTURE_ID;
    }

    public void clear() {
        closeTexture();
        uploadedImage = null;
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
