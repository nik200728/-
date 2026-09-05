package dev.nikita.tgvoice.client;

import dev.nikita.tgvoice.network.VideoNotePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
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
    private volatile VideoNoteRenderState preparedState;
    private volatile Identifier preparedTexture;
    private volatile boolean preparedStateInvalid;

    public VideoNoteScreen() {
        super(Component.translatable("screen.tgvoice.video_notes"));
        textureManager = new VideoNoteTextureManager(Minecraft.getInstance());
    }

    @Override
    protected void init() {
        clampSelectedIndex(VideoNoteManager.getInstance().messages().size());
        preparedState = null;
        preparedTexture = null;
        preparedStateInvalid = false;
    }

    private void clampSelectedIndex(int messageCount) {
        selectedIndex = messageCount <= 0 ? 0 : Math.min(Math.max(0, selectedIndex), messageCount - 1);
    }

    /**
     * Prepare decoded frames and GPU textures on the client tick, outside the
     * deferred GUI extraction pass used by Minecraft 26.1.
     */
    @Override
    public void tick() {
        super.tick();
        List<VideoNotePayload> messages = VideoNoteManager.getInstance().messages();
        if (messages.isEmpty()) {
            selectedIndex = 0;
            preparedState = null;
            preparedTexture = null;
            preparedStateInvalid = false;
            return;
        }

        clampSelectedIndex(messages.size());
        VideoNotePayload payload = messages.get(selectedIndex);
        try {
            VideoNotePlayback playback = VideoNotePlaybackManager.getInstance().load(payload);
            VideoNoteRenderState state = VideoNoteRenderState.from(payload, playback, frameCache);
            Identifier texture = textureManager.upload(state.frame());
            preparedState = state;
            preparedTexture = texture;
            preparedStateInvalid = false;
        } catch (RuntimeException error) {
            preparedState = null;
            preparedTexture = null;
            preparedStateInvalid = true;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        List<VideoNotePayload> messages = VideoNoteManager.getInstance().messages();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xE9141821);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 3, 0xFF4EA1FF);
        graphics.text(font, Component.literal("Video notes"), left + 20, top + 18, 0xFFFFFFFF, false);
        graphics.text(font, Component.literal("ESC"), left + PANEL_WIDTH - 45, top + 18, 0xFF7F8DA0, false);

        if (messages.isEmpty()) {
            graphics.centeredText(font, Component.literal("No video notes"), width / 2, top + 180, 0xFFB9C2CF);
            return;
        }

        clampSelectedIndex(messages.size());
        if (preparedStateInvalid) {
            graphics.centeredText(font, Component.literal("Invalid video note"), width / 2, top + 180, 0xFFFF8A8A);
            return;
        }

        VideoNoteRenderState state = preparedState;
        Identifier texture = preparedTexture;
        if (state == null || texture == null) {
            graphics.centeredText(font, Component.literal("Preparing video…"), width / 2, top + 180, 0xFFB9C2CF);
            return;
        }

        int cx = width / 2;
        int cy = top + 148;
        int imageLeft = cx - VIDEO_RADIUS;
        int imageTop = cy - VIDEO_RADIUS;

        graphics.fill(imageLeft - 6, imageTop - 6, imageLeft + VIDEO_SIZE + 6, imageTop + VIDEO_SIZE + 6, 0xFF303A49);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, imageLeft, imageTop,
                0, 0, VIDEO_SIZE, VIDEO_SIZE, state.width(), state.height());
        drawProgressRing(graphics, cx, cy, VIDEO_RADIUS + 7, state.progress());

        boolean videoHovered = Math.hypot(mouseX - cx, mouseY - cy) <= VIDEO_RADIUS;
        if (videoHovered) {
            graphics.fill(imageLeft, imageTop, imageLeft + VIDEO_SIZE, imageTop + VIDEO_SIZE, 0x40000000);
            drawPlaybackGlyph(graphics, cx, cy, state.playing());
        }

        graphics.centeredText(font, Component.literal(state.senderName()), cx, top + 248, 0xFFFFFFFF);
        graphics.centeredText(font,
                Component.literal(formatTime(state.positionMillis()) + " / " + formatTime(state.durationMillis())),
                cx, top + 267, 0xFFB9C2CF);

        int barLeft = left + 40;
        int barRight = left + PANEL_WIDTH - 40;
        int barY = top + 300;
        graphics.fill(barLeft, barY, barRight, barY + 4, 0xFF384454);
        graphics.fill(barLeft, barY, barLeft + Math.round((barRight - barLeft) * state.progress()), barY + 4, 0xFF4EA1FF);

        drawControl(graphics, left + 68, top + 330, "‹", selectedIndex > 0, mouseX, mouseY);
        drawControl(graphics, left + PANEL_WIDTH - 68, top + 330, "›", selectedIndex < messages.size() - 1, mouseX, mouseY);

        String action = state.playing() ? "Pause" : (state.positionMillis() >= state.durationMillis() ? "Replay" : "Play");
        graphics.centeredText(font, Component.literal(action), cx, top + 323, 0xFFFFFFFF);
        graphics.centeredText(font,
                Component.literal((selectedIndex + 1) + " / " + messages.size()),
                cx, top + 349, 0xFF7F8DA0);
    }

    private void drawPlaybackGlyph(GuiGraphicsExtractor graphics, int cx, int cy, boolean playing) {
        if (playing) {
            graphics.fill(cx - 10, cy - 12, cx - 3, cy + 12, 0xFFFFFFFF);
            graphics.fill(cx + 3, cy - 12, cx + 10, cy + 12, 0xFFFFFFFF);
            return;
        }
        graphics.fill(cx - 7, cy - 13, cx - 2, cy + 13, 0xFFFFFFFF);
        graphics.fill(cx - 2, cy - 9, cx + 4, cy + 9, 0xFFFFFFFF);
        graphics.fill(cx + 4, cy - 5, cx + 9, cy + 5, 0xFFFFFFFF);
    }

    private void drawControl(GuiGraphicsExtractor graphics, int cx, int cy, String glyph, boolean enabled, int mouseX, int mouseY) {
        int alpha = enabled ? 0xFF : 0x55;
        boolean hovered = enabled && Math.hypot(mouseX - cx, mouseY - cy) <= 18;
        graphics.fill(cx - 18, cy - 15, cx + 18, cy + 15, hovered ? 0xFF344153 : 0xCC202936);
        graphics.centeredText(font, Component.literal(glyph), cx, cy - 8, (alpha << 24) | 0xFFFFFF);
    }

    private void drawProgressRing(GuiGraphicsExtractor graphics, int cx, int cy, int radius, float progress) {
        int segments = 72;
        int active = Math.round(segments * Math.max(0.0f, Math.min(1.0f, progress)));
        for (int i = 0; i < segments; i++) {
            double angle = -Math.PI / 2.0 + (Math.PI * 2.0 * i / segments);
            int x = cx + (int) Math.round(Math.cos(angle) * radius);
            int y = cy + (int) Math.round(Math.sin(angle) * radius);
            int alpha = i < active ? 0xFF : 0x55;
            graphics.fill(x - 1, y - 1, x + 2, y + 2, (alpha << 24) | 0x4EA1FF);
        }
    }

    private void selectIndex(int newIndex) {
        List<VideoNotePayload> messages = VideoNoteManager.getInstance().messages();
        if (newIndex < 0 || newIndex >= messages.size() || newIndex == selectedIndex) return;
        if (!messages.isEmpty() && selectedIndex >= 0 && selectedIndex < messages.size()) {
            VideoNotePayload current = messages.get(selectedIndex);
            VideoNotePlayback playback = VideoNotePlaybackManager.getInstance().get(current.messageId());
            if (playback != null) playback.stop();
        }
        selectedIndex = newIndex;
        preparedState = null;
        preparedTexture = null;
        preparedStateInvalid = false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        List<VideoNotePayload> messages = VideoNoteManager.getInstance().messages();
        if (messages.isEmpty()) return super.mouseClicked(event, doubleClick);
        clampSelectedIndex(messages.size());

        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
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

            if (mouseY >= top + 315 && mouseY <= top + 360) {
                if (mouseX >= left + 45 && mouseX <= left + 95 && selectedIndex > 0) {
                    selectIndex(selectedIndex - 1);
                    return true;
                }
                if (mouseX >= left + PANEL_WIDTH - 95 && mouseX <= left + PANEL_WIDTH - 45
                        && selectedIndex < messages.size() - 1) {
                    selectIndex(selectedIndex + 1);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        List<VideoNotePayload> messages = VideoNoteManager.getInstance().messages();
        if (messages.isEmpty()) return super.keyPressed(event);
        clampSelectedIndex(messages.size());
        if (event.key() == 263 && selectedIndex > 0) {
            selectIndex(selectedIndex - 1);
            return true;
        }
        if (event.key() == 262 && selectedIndex < messages.size() - 1) {
            selectIndex(selectedIndex + 1);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        List<VideoNotePayload> messages = VideoNoteManager.getInstance().messages();
        if (!messages.isEmpty() && selectedIndex >= 0 && selectedIndex < messages.size()) {
            VideoNotePayload current = messages.get(selectedIndex);
            VideoNotePlayback playback = VideoNotePlaybackManager.getInstance().get(current.messageId());
            if (playback != null) playback.stop();
        }
        frameCache.clear();
        textureManager.close();
        preparedState = null;
        preparedTexture = null;
        preparedStateInvalid = false;
        super.removed();
    }

    private static String formatTime(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
