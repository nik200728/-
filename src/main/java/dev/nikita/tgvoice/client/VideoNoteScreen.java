package dev.nikita.tgvoice.client;

import dev.nikita.tgvoice.network.VideoNotePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Lightweight video-note browser. It deliberately uses Minecraft GUI primitives
 * only; actual frame textures are the next rendering layer.
 */
public final class VideoNoteScreen extends Screen {
    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 360;
    private final VideoNoteFrameCache frameCache = new VideoNoteFrameCache();
    private int selectedIndex;

    public VideoNoteScreen() {
        super(Component.translatable("screen.tgvoice.video_notes"));
    }

    @Override
    protected void init() {
        selectedIndex = Math.max(0, Math.min(selectedIndex, VideoNoteManager.getInstance().messages().size() - 1));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);

        List<VideoNotePayload> messages = VideoNoteManager.getInstance().messages();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xE9141821);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 3, 0xFF4EA1FF);
        graphics.drawString(font, Component.literal("Video notes"), left + 20, top + 18, 0xFFFFFFFF);

        if (messages.isEmpty()) {
            graphics.drawCenteredString(font, Component.literal("No video notes"), width / 2, top + 165, 0xFFB9C2CF);
            super.render(graphics, mouseX, mouseY, delta);
            return;
        }

        selectedIndex = Math.min(selectedIndex, messages.size() - 1);
        VideoNotePayload payload = messages.get(selectedIndex);
        VideoNotePlayback playback = VideoNotePlaybackManager.getInstance().load(payload);
        VideoNoteRenderState state;
        try {
            state = VideoNoteRenderState.from(payload, playback, frameCache);
        } catch (RuntimeException error) {
            graphics.drawCenteredString(font, Component.literal("Invalid video note"), width / 2, top + 165, 0xFFFF8A8A);
            super.render(graphics, mouseX, mouseY, delta);
            return;
        }

        int cx = width / 2;
        int cy = top + 150;
        int radius = 86;
        graphics.fill(cx - radius, cy - radius, cx + radius, cy + radius, 0xFF293443);
        graphics.fill(cx - radius + 4, cy - radius + 4, cx + radius - 4, cy + radius - 4, 0xFF151A22);
        graphics.drawCenteredString(font, Component.literal("VIDEO"), cx, cy - 4, 0xFF7F8DA0);

        graphics.drawCenteredString(font, Component.literal(state.senderName()), cx, top + 255, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.literal(formatTime(state.positionMillis()) + " / " + formatTime(state.durationMillis())), cx, top + 274, 0xFFB9C2CF);

        int barLeft = left + 40;
        int barRight = left + PANEL_WIDTH - 40;
        int barY = top + 300;
        graphics.fill(barLeft, barY, barRight, barY + 4, 0xFF384454);
        graphics.fill(barLeft, barY, barLeft + Math.round((barRight - barLeft) * state.progress()), barY + 4, 0xFF4EA1FF);

        graphics.drawCenteredString(font, Component.literal(state.playing() ? "Pause" : "Play"), cx, top + 320, 0xFFFFFFFF);
        graphics.drawString(font, Component.literal("J"), left + PANEL_WIDTH - 25, top + 18, 0xFF7F8DA0);

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<VideoNotePayload> messages = VideoNoteManager.getInstance().messages();
        if (messages.isEmpty()) return super.mouseClicked(mouseX, mouseY, button);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        int cx = width / 2;
        int cy = top + 150;

        if (button == 0) {
            double distance = Math.hypot(mouseX - cx, mouseY - cy);
            VideoNotePayload payload = messages.get(selectedIndex);
            VideoNotePlayback playback = VideoNotePlaybackManager.getInstance().load(payload);
            if (distance <= 86) {
                if (playback.isPlaying()) playback.pause(); else playback.play();
                return true;
            }

            int barLeft = left + 40;
            int barRight = left + PANEL_WIDTH - 40;
            int barY = top + 300;
            if (mouseX >= barLeft && mouseX <= barRight && mouseY >= barY - 8 && mouseY <= barY + 12) {
                float progress = (float) ((mouseX - barLeft) / (double) (barRight - barLeft));
                playback.seek(Math.round(progress * playback.video().durationMillis()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        List<VideoNotePayload> messages = VideoNoteManager.getInstance().messages();
        if (keyCode == 263 && !messages.isEmpty()) {
            selectedIndex = Math.max(0, selectedIndex - 1);
            return true;
        }
        if (keyCode == 262 && !messages.isEmpty()) {
            selectedIndex = Math.min(messages.size() - 1, selectedIndex + 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        frameCache.clear();
        super.removed();
    }

    private static String formatTime(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
