package dev.nikita.tgvoice.client;

import dev.nikita.tgvoice.network.VoiceMessagePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Small, non-invasive Telegram-style voice message player screen. */
public final class VoiceMessageScreen extends Screen {
    private static final int CARD_WIDTH = 430;
    private static final int CARD_HEIGHT = 150;
    private static final int WAVE_HEIGHT = 44;

    private final String messageId;
    private Button playButton;
    private int cardLeft;
    private int cardTop;

    public VoiceMessageScreen(String messageId) {
        super(Component.translatable("screen.tgvoice.voice_messages"));
        this.messageId = messageId;
    }

    @Override
    protected void init() {
        cardLeft = (width - CARD_WIDTH) / 2;
        cardTop = (height - CARD_HEIGHT) / 2;
        playButton = Button.builder(buttonText(), button -> togglePlayback())
                .bounds(cardLeft + 18, cardTop + 92, 92, 28).build();
        addRenderableWidget(playButton);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(cardLeft + CARD_WIDTH - 112, cardTop + 92, 94, 28).build());
    }

    @Override
    public void tick() {
        if (playButton != null) playButton.setMessage(buttonText());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.fill(0, 0, width, height, 0x99000000);
        graphics.fill(cardLeft, cardTop, cardLeft + CARD_WIDTH, cardTop + CARD_HEIGHT, 0xFF202124);
        graphics.fill(cardLeft, cardTop, cardLeft + CARD_WIDTH, cardTop + 2, 0xFF5B9BF5);

        VoiceMessagePayload payload = VoiceMessagePlaybackManager.payload(messageId);
        VoiceMessagePlayback playback = VoiceMessagePlaybackManager.get(messageId);
        if (payload != null && playback != null) {
            graphics.text(font, Component.literal(payload.senderName()), cardLeft + 18, cardTop + 16, 0xFFFFFFFF, true);
            graphics.text(font,
                    formatDuration(playback.positionMillis()) + " / " + formatDuration(payload.durationMillis()),
                    cardLeft + CARD_WIDTH - 112, cardTop + 16, 0xFFB8B8B8, false);
            drawWaveform(graphics, payload.waveform(), playback.progress(), cardLeft + 18, cardTop + 38, CARD_WIDTH - 36, WAVE_HEIGHT);
        } else {
            graphics.text(font, Component.translatable("screen.tgvoice.message_unavailable"),
                    cardLeft + 18, cardTop + 24, 0xFFFFFFFF, true);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();
            int x = cardLeft + 18, y = cardTop + 38, w = CARD_WIDTH - 36;
            if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + WAVE_HEIGHT) {
                VoiceMessagePayload payload = VoiceMessagePlaybackManager.payload(messageId);
                if (payload != null) {
                    float progress = (float) ((mouseX - x) / w);
                    VoiceMessagePlaybackManager.seek(messageId, (long) (payload.durationMillis() * progress));
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        VoiceMessagePlaybackManager.stop(messageId);
        super.onClose();
    }

    private void togglePlayback() {
        VoiceMessagePlayback playback = VoiceMessagePlaybackManager.get(messageId);
        if (playback == null) return;
        switch (playback.state()) {
            case PLAYING -> VoiceMessagePlaybackManager.pause(messageId);
            case PAUSED -> VoiceMessagePlaybackManager.resume(messageId);
            case STOPPED -> VoiceMessagePlaybackManager.play(messageId);
        }
    }

    private Component buttonText() {
        VoiceMessagePlayback playback = VoiceMessagePlaybackManager.get(messageId);
        if (playback == null) return Component.translatable("screen.tgvoice.play");
        return playback.state() == VoiceMessagePlayback.State.PLAYING
                ? Component.translatable("screen.tgvoice.pause")
                : Component.translatable("screen.tgvoice.play");
    }

    private static void drawWaveform(GuiGraphicsExtractor graphics, byte[] waveform, float progress,
                                     int x, int y, int width, int height) {
        if (waveform == null || waveform.length == 0) return;
        int bars = Math.min(waveform.length, width);
        for (int i = 0; i < bars; i++) {
            int index = (int) ((long) i * waveform.length / bars);
            int amplitude = waveform[index] & 0xFF;
            int barHeight = Math.max(2, amplitude * height / 255);
            int barTop = y + (height - barHeight) / 2;
            int color = i / (float) bars <= progress ? 0xFF66B3FF : 0xFF70757A;
            graphics.fill(x + i, barTop, x + i + 1, barTop + barHeight, color);
        }
    }

    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(0, millis) / 1000;
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}
