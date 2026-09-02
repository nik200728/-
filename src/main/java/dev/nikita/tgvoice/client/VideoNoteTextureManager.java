package dev.nikita.tgvoice.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/** Owns one dynamic GPU texture for the currently rendered video-note frame. */
public final class VideoNoteTextureManager implements AutoCloseable {
    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath("tgvoice", "video_note_frame");

    private final TextureManager textureManager;
    private DynamicTexture texture;
    private NativeImage uploadedFrame;
    private int uploadedWidth;
    private int uploadedHeight;

    public VideoNoteTextureManager(Minecraft client) {
        this.textureManager = Objects.requireNonNull(client.getTextureManager(), "texture manager");
    }

    /** Copies and uploads an already-decoded frame; JPEG decoding is owned by VideoNoteFrameCache. */
    public Identifier upload(NativeImage frame) {
        if (frame == null) {
            throw new IllegalArgumentException("frame is required");
        }
        int width = frame.getWidth();
        int height = frame.getHeight();
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("invalid frame dimensions");
        }

        if (texture != null && uploadedFrame == frame
                && uploadedWidth == width && uploadedHeight == height) {
            return TEXTURE_ID;
        }

        closeTexture();
        NativeImage textureImage = new NativeImage(width, height, false);
        textureImage.copyFrom(frame);
        texture = new DynamicTexture(() -> "tgvoice-video-note", textureImage);
        textureManager.register(TEXTURE_ID, texture);
        uploadedFrame = frame;
        uploadedWidth = width;
        uploadedHeight = height;
        return TEXTURE_ID;
    }

    public void clear() { closeTexture(); }

    @Override
    public void close() { clear(); }

    private void closeTexture() {
        if (texture != null) {
            textureManager.release(TEXTURE_ID);
            texture = null;
        }
        uploadedFrame = null;
        uploadedWidth = 0;
        uploadedHeight = 0;
    }
}
