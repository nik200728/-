package dev.nikita.ptv.client;

import dev.nikita.ptv.PlasmoTelegramVoice;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

public final class PlasmoTelegramVoiceClient implements ClientModInitializer {
    private static final String CATEGORY = "key.categories.plasmo-telegram-voice";
    private static KeyMapping recordKey;
    private static RecordingController recording;

    @Override
    public void onInitializeClient() {
        recordKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.plasmo-telegram-voice.record",
                GLFW.GLFW_KEY_V,
                CATEGORY
        ));
        recording = new RecordingController();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (recordKey.consumeClick()) {
                if (recording.isRecording()) {
                    recording.stop();
                } else {
                    recording.start(client);
                }
            }
            recording.tick();
        });

        // Rendering is intentionally kept independent from Plasmo Voice's UI.
        HudOverlay.register((graphics, tickDelta) -> renderOverlay(graphics));
        PlasmoTelegramVoice.LOGGER.info("Client voice-message controls initialized");
    }

    private static void renderOverlay(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen != null || recording == null || !recording.isRecording()) {
            return;
        }
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        int boxW = 220;
        int boxH = 64;
        int x = (width - boxW) / 2;
        int y = height - 110;

        graphics.fill(x, y, x + boxW, y + boxH, 0xDD151515);
        graphics.drawString(client.font, "Recording", x + 10, y + 8, 0xFFFFFF, false);
        graphics.drawString(client.font, format(recording.elapsedMs()), x + 10, y + 24, 0xFFFFFF, false);
        graphics.drawString(client.font, "Release/press V to finish", x + 10, y + 42, 0xAAAAAA, false);
    }

    private static String format(long ms) {
        long seconds = ms / 1000L;
        return String.format("%02d:%02d", seconds / 60L, seconds % 60L);
    }
}
