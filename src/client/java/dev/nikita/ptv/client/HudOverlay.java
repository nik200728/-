package dev.nikita.ptv.client;

import dev.nikita.ptv.PlasmoTelegramVoice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import java.util.function.BiConsumer;

public final class HudOverlay {
    private static BiConsumer<GuiGraphicsExtractor, Float> renderer = (graphics, delta) -> {};

    private HudOverlay() {}

    public static void register(BiConsumer<GuiGraphicsExtractor, Float> callback) {
        renderer = callback;
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(PlasmoTelegramVoice.MOD_ID, "voice_message_overlay"),
                (graphics, deltaTracker) -> renderer.accept(
                        graphics,
                        deltaTracker.getGameTimeDeltaPartialTick(true)
                )
        );
    }

    public static Minecraft client() {
        return Minecraft.getInstance();
    }
}
