package dev.nikita.tgvoice.client;

import dev.nikita.tgvoice.network.VideoNotePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Telegram-like local video-note browser with real decoded-frame rendering. */
public final class VideoNoteScreen extends Screen {
    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 390;
    private static final int VIDEO_SIZE = 172;
    private static final int VIDEO_RADIUS = VIDEO_SIZE / 2;

    private final VideoNoteFrameCache frameCache = new VideoNoteFrameCache();
    private final VideoNoteTextureManager textureManager;
    private int selectedIndex;

    public VideoNoteScreen() {
        super(Component.translatable("screen.tgvoice.video_notes"));
        textureManager = new VideoNoteTextureManager(Minecraft.getInstance());
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
            graphics.drawCenteredString(font, Component.literal("No video notes"), width / 2, top + 180, 0xFFB9C2CF);
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
            graphics.drawCenteredString(font, Component.literal("Invalid video note"), width / 2, top + 180, 0xFFFF8A8A);
            super.render(graphics, mouseX, mouseY, delta);
            return;
        }

        int cx = width / 2;
        int cy = top + 148;
        int imageLeft = cx - VIDEO_RADIUS;
        int imageTop = cy - VIDEO_RADIUS;
        Identifier texture = textureManager.upload(state.frame());

        graphics.fill(imageLeft - 5, imageTop - 5, imageLeft + VIDEO_SIZE + 5, imageTop + VIDEO_SIZE + 5, 0xFF303A49);
        graphics.blit(texture, imageLeft, imageTop, VIDEO_SIZE, VIDEO_SIZE,
                0, 0, state.width(), state.height(), state.width(), state.height());
        drawProgressRing(graphics, cx, cy, VIDEO_RADIUS + 7, state.progress());

        graphics.drawCenteredString(font, Component.literal(state.senderName()), cx, top + 248, 0xFFFFFFFF);
        graphics.drawCenteredString(font,
                Component.literal(formatTime(state.positionMillis()) + " / " + formatTime(state.durationMillis())),
                cx, top + 267, 0xFFB9C2CF);

        int barLeft = left + 40;
        int barRight = left + PANEL_WIDTH - 40;
        int barY = top + 300;
        graphics.fill(barLeft, barY, barRight, barY + 4, 0xFF384454);
        graphics.fill(barLeft, barY, barLeft + Math.round((barRight - barLeft) * state.progress()), barY + 4, 0xFF4EA1FF);

        String action = state.playing() ? "Pause" : (state.positionMillis() >= state.durationMillis() ? "Replay" : "Play");
        graphics.drawCenteredString(font, Component.literal(action), cx, top + 323, 0xFFFFFFFF);
        graphics.drawString(font, Component.literal("←  →  select"), left + 20, top + 350, 0xFF7F8DA0);
        graphics.drawString(font, Component.literal("J"), left + PANEL_WIDTH - 25, top + 18, 0xFF7F8DA0);

        super.render(graphics, mouseX, mouseY, delta);
    }

    private void drawProgressRing(GuiGraphics graphics, int cx, int cy, int radius, float progress) {
        int segments = 64;
        int active = Math.round(segments * Math.max(0.0f, Math.min(1.0f, progress)));
        for (int i = 0; i < segments; i++) {
            double angle = -Math.PI / 2.0 + (Math.PI * 2.0 * i / segments);
            int x = cx + (int) Math.round(Math.cos(angle) * radius);
            int y = cy + (int) Math.round(Math.sin(angle) * radius);
            int alpha = i < active ? 0xFF : 0x55;
            graphics.fill(x - 1, y - 1, x + 2, y + 2, (alpha << 24) | 0x4EA1FF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<VideoNotePayload> messages = VideoNoteManager.getInstance().messages();
        if (messages.isEmpty()) return super.mouseClicked(mouseX, mouseY, button);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        int cx = width / 2;
        int cy = top + 148;
        VideoNotePayload payload = messages.get(selectedIndex);
        VideoNotePlayback playback = VideoNotePlaybackManager.getInstance().load(payload);

        if (button == 0) {
            if (Math.hypot(mouseX - cx, mouseY - cy) <= VIDEO_RADIUS + 7) {
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
        if (messages.isEmpty()) return super.keyPressed(keyCode, scanCode, modifiers);
        if (keyCode == 263) {
            selectedIndex = Math.max(0, selectedIndex - 1);
            return true;
        }
        if (keyCode == 262) {
            selectedIndex = Math.min(messages.size() - 1, selectedIndex + 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        frameCache.clear();
        textureManager.close();
        super.removed();
    }

    private static String formatTime(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
