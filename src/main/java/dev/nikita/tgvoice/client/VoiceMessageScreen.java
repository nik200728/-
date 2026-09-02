package dev.nikita.tgvoice.client;

import dev.nikita.tgvoice.network.VoiceMessagePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Compact Telegram-style inbox that stays completely separate from Plasmo Voice UI. */
public final class VoiceMessageScreen extends Screen {
    private static final int CARD_WIDTH = 520;
    private static final int ROW_HEIGHT = 86;
    private static final int MAX_VISIBLE = 5;
    private static final int WAVE_HEIGHT = 28;

    private List<String> messageIds = List.of();
    private int listLeft;
    private int listTop;

    public VoiceMessageScreen() {
        super(Component.translatable("screen.tgvoice.voice_messages"));
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    private void rebuildWidgets() {
        clearWidgets();
        messageIds = VoiceMessagePlaybackManager.messageIds();
        listLeft = (width - CARD_WIDTH) / 2;
        listTop = Math.max(34, (height - Math.min(MAX_VISIBLE, messageIds.size()) * ROW_HEIGHT) / 2);

        int visible = Math.min(MAX_VISIBLE, messageIds.size());
        for (int i = 0; i < visible; i++) {
            String id = messageIds.get(messageIds.size() - 1 - i);
            VoiceMessagePlayback playback = VoiceMessagePlaybackManager.get(id);
            if (playback == null) continue;
            final String messageId = id;
            addRenderableWidget(Button.builder(buttonText(playback), button -> togglePlayback(messageId))
                    .bounds(listLeft + 16, listTop + i * ROW_HEIGHT + 48, 92, 26).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.tgvoice.stop"), button -> VoiceMessagePlaybackManager.stop(messageId))
                    .bounds(listLeft + 116, listTop + i * ROW_HEIGHT + 48, 74, 26).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(listLeft + CARD_WIDTH - 100, Math.min(height - 38, listTop + visible * ROW_HEIGHT + 8), 84, 26).build());
    }

    @Override
    public void tick() {
        List<String> current = VoiceMessagePlaybackManager.messageIds();
        if (!current.equals(messageIds)) {
            rebuildWidgets();
            return;
        }
        // Keep play/pause labels in sync with the playback state.
        for (int i = 0; i < Math.min(MAX_VISIBLE, messageIds.size()); i++) {
            String id = messageIds.get(messageIds.size() - 1 - i);
            VoiceMessagePlayback playback = VoiceMessagePlaybackManager.get(id);
            if (playback != null) {
                // Widget ordering is stable: play, stop for each visible message.
                int index = i * 2;
                if (index < children().size() && children().get(index) instanceof Button button) {
                    button.setMessage(buttonText(playback));
                }
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.fill(0, 0, width, height, 0x99000000);

        int visible = Math.min(MAX_VISIBLE, messageIds.size());
        int panelTop = listTop - 34;
        int panelBottom = Math.min(height - 4, listTop + visible * ROW_HEIGHT + 44);
        graphics.fill(listLeft, panelTop, listLeft + CARD_WIDTH, panelBottom, 0xFF202124);
        graphics.fill(listLeft, panelTop, listLeft + CARD_WIDTH, panelTop + 2, 0xFF5B9BF5);
        graphics.text(font, Component.translatable("screen.tgvoice.voice_messages"), listLeft + 16, panelTop + 12, 0xFFFFFFFF, true);

        for (int i = 0; i < visible; i++) {
            String id = messageIds.get(messageIds.size() - 1 - i);
            VoiceMessagePayload payload = VoiceMessagePlaybackManager.payload(id);
            VoiceMessagePlayback playback = VoiceMessagePlaybackManager.get(id);
            if (payload == null || playback == null) continue;

            int top = listTop + i * ROW_HEIGHT;
            graphics.fill(listLeft + 10, top, listLeft + CARD_WIDTH - 10, top + ROW_HEIGHT - 4, 0xFF292A2D);
            graphics.text(font, Component.literal(payload.senderName()), listLeft + 16, top + 8, 0xFFFFFFFF, true);
            graphics.text(font,
                    formatDuration(playback.positionMillis()) + " / " + formatDuration(payload.durationMillis()),
                    listLeft + CARD_WIDTH - 112, top + 8, 0xFFB8B8B8, false);
            drawWaveform(graphics, payload.waveform(), playback.progress(), listLeft + 204, top + 10, CARD_WIDTH - 222, WAVE_HEIGHT);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double x = event.x();
            double y = event.y();
            int visible = Math.min(MAX_VISIBLE, messageIds.size());
            for (int i = 0; i < visible; i++) {
                String id = messageIds.get(messageIds.size() - 1 - i);
                VoiceMessagePayload payload = VoiceMessagePlaybackManager.payload(id);
                if (payload == null) continue;
                int top = listTop + i * ROW_HEIGHT;
                int waveX = listLeft + 204;
                int waveW = CARD_WIDTH - 222;
                if (x >= waveX && x <= waveX + waveW && y >= top + 10 && y <= top + 10 + WAVE_HEIGHT) {
                    float progress = (float) ((x - waveX) / waveW);
                    VoiceMessagePlaybackManager.seek(id, (long) (payload.durationMillis() * progress));
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        for (String id : messageIds) VoiceMessagePlaybackManager.stop(id);
        super.onClose();
    }

    private static void togglePlayback(String messageId) {
        VoiceMessagePlayback playback = VoiceMessagePlaybackManager.get(messageId);
        if (playback == null) return;
        switch (playback.state()) {
            case PLAYING -> VoiceMessagePlaybackManager.pause(messageId);
            case PAUSED -> VoiceMessagePlaybackManager.resume(messageId);
            case STOPPED -> VoiceMessagePlaybackManager.play(messageId);
        }
    }

    private static Component buttonText(VoiceMessagePlayback playback) {
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
