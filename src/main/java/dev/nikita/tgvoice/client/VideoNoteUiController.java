package dev.nikita.tgvoice.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Opens the local video-note browser independently from Plasmo Voice UI. */
public final class VideoNoteUiController {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("tgvoice", "video_notes")
    );

    private static final KeyMapping OPEN_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.tgvoice.open_video_notes",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            CATEGORY
    ));

    private static final long MAX_TICK_ELAPSED_NANOS = 250_000_000L;
    private static boolean registered;
    private static long lastTickNanos;

    private VideoNoteUiController() {}

    public static void register() {
        if (registered) return;
        registered = true;
        lastTickNanos = System.nanoTime();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long now = System.nanoTime();
            long elapsedNanos = Math.max(0L, now - lastTickNanos);
            lastTickNanos = now;
            long elapsedMillis = Math.min(MAX_TICK_ELAPSED_NANOS, elapsedNanos) / 1_000_000L;
            VideoNotePlaybackManager.getInstance().tick(elapsedMillis);

            while (OPEN_KEY.consumeClick()) open(client);
        });
    }

    private static void open(Minecraft client) {
        if (client.screen != null) return;
        if (VideoNoteManager.getInstance().messages().isEmpty()) return;
        client.setScreen(new VideoNoteScreen());
    }
}
