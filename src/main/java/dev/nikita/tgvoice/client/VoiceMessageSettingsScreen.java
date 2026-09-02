package dev.nikita.tgvoice.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Simple in-game settings for the addon-owned recording and playback behavior. */
public final class VoiceMessageSettingsScreen extends Screen {
    private static final int WIDTH = 420;
    private static final int HEIGHT = 230;

    public VoiceMessageSettingsScreen() {
        super(Component.literal("Telegram Voice Messages — Settings"));
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        VoiceMessageConfig config = VoiceMessageConfig.get();
        int left = (width - WIDTH) / 2;
        int top = Math.max(24, (height - HEIGHT) / 2);

        addRenderableWidget(Button.builder(modeText(config), button -> {
            config.toggleMode = !config.toggleMode;
            config.save();
            VoiceMessageInput.getInstance().setToggle(config.toggleMode);
            rebuildWidgets();
        }).bounds(left + 20, top + 55, WIDTH - 40, 26).build());

        addRenderableWidget(Button.builder(Component.literal("− 5 sec"), button -> changeDuration(-5))
                .bounds(left + 20, top + 92, 90, 26).build());
        addRenderableWidget(Button.builder(Component.literal("+ 5 sec"), button -> changeDuration(5))
                .bounds(left + WIDTH - 110, top + 92, 90, 26).build());

        addRenderableWidget(Button.builder(Component.literal("− volume"), button -> changeVolume(-0.1f))
                .bounds(left + 20, top + 160, 90, 26).build());
        addRenderableWidget(Button.builder(Component.literal("+ volume"), button -> changeVolume(0.1f))
                .bounds(left + WIDTH - 110, top + 160, 90, 26).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + WIDTH - 104, top + 198, 84, 26).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int left = (width - WIDTH) / 2;
        int top = Math.max(24, (height - HEIGHT) / 2);
        VoiceMessageConfig config = VoiceMessageConfig.get();

        graphics.fill(0, 0, width, height, 0x99000000);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xFF202124);
        graphics.fill(left, top, left + WIDTH, top + 2, 0xFF5B9BF5);
        graphics.text(font, title, left + 20, top + 15, 0xFFFFFFFF, true);
        graphics.text(font, Component.literal("Recording mode"), left + 20, top + 42, 0xFFB8B8B8, false);
        graphics.text(font, Component.literal("Maximum duration: " + config.maxDurationSeconds + " sec"),
                left + 20, top + 126, 0xFFB8B8B8, false);
        graphics.text(font, Component.literal("Playback volume: " + Math.round(config.playbackVolume() * 100) + "%"),
                left + 20, top + 144, 0xFFB8B8B8, false);
        graphics.text(font, Component.literal("Volume is applied only to Voice Messages, not Plasmo Voice proximity audio."),
                left + 20, top + 188, 0xFF888888, false);
    }

    private void changeDuration(int delta) {
        VoiceMessageConfig config = VoiceMessageConfig.get();
        config.maxDurationSeconds = Math.max(1, Math.min(120, config.maxDurationSeconds + delta));
        config.save();
        rebuildWidgets();
    }

    private void changeVolume(float delta) {
        VoiceMessageConfig config = VoiceMessageConfig.get();
        config.playbackVolume = Math.max(0.0f, Math.min(2.0f, config.playbackVolume() + delta));
        config.save();
        rebuildWidgets();
    }

    private static Component modeText(VoiceMessageConfig config) {
        return Component.literal("Mode: " + (config.toggleMode ? "Toggle (press to start/stop)" : "PTT (hold to record)"));
    }
}
