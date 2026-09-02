package dev.nikita.tgvoice.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nikita.tgvoice.network.VideoNotePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Hold V to capture a webcam video note; release V to send it. */
public final class VideoNoteCaptureController {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("tgvoice", "video_notes")
    );
    private static final KeyMapping CAPTURE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.tgvoice.capture_video_note",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
    ));

    private static final VideoNoteCaptureController INSTANCE = new VideoNoteCaptureController();

    private WebcamCaptureService camera;
    private VideoNoteRecorder recorder;
    private boolean recording;
    private boolean keyWasDown;
    private boolean registered;

    private VideoNoteCaptureController() {}

    public static void register() {
        if (INSTANCE.registered) return;
        INSTANCE.registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(INSTANCE::tick);
    }

    private void tick(Minecraft client) {
        boolean keyDown = CAPTURE_KEY.isDown();

        if (client.screen != null || client.player == null) {
            if (recording) cancel();
            keyWasDown = keyDown;
            return;
        }

        if (keyDown) {
            // Do not start another recording automatically after the 60-second limit.
            // The user must release V and press it again.
            if (!keyWasDown && !recording) start(client);
        } else if (recording) {
            stopAndSend(client);
        }

        keyWasDown = keyDown;
    }

    private void start(Minecraft client) {
        try {
            camera = new WebcamCaptureService();
            recorder = new VideoNoteRecorder(camera);
            recorder.start();
            recording = true;
            client.player.displayClientMessage(Component.literal("Video note recording… release V to send"), true);
        } catch (Exception exception) {
            closeRecorder();
            client.player.displayClientMessage(Component.literal("Webcam unavailable: " + safeMessage(exception)), true);
        }
    }

    private void stopAndSend(Minecraft client) {
        VideoNoteRecorder current = recorder;
        recording = false;
        try {
            if (current == null) return;
            VideoNotePayload payload = current.stop(client.player.getUUID(), client.player.getGameProfile().name());
            ClientPlayNetworking.send(payload);

            String failure = current.failure();
            if (failure == null || failure.isBlank()) {
                client.player.displayClientMessage(Component.literal("Video note sent"), true);
            } else {
                client.player.displayClientMessage(
                        Component.literal("Video note sent (capture stopped: " + failure + ")"), true
                );
            }
        } catch (Exception exception) {
            client.player.displayClientMessage(Component.literal("Video note failed: " + safeMessage(exception)), true);
        } finally {
            closeRecorder();
        }
    }

    private void cancel() {
        recording = false;
        if (recorder != null) recorder.cancel();
        closeRecorder();
    }

    private void closeRecorder() {
        if (recorder != null) {
            recorder.close();
            recorder = null;
        } else if (camera != null) {
            camera.close();
        }
        camera = null;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
