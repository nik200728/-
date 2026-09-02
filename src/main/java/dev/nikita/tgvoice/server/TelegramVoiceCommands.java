package dev.nikita.tgvoice.server;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import dev.nikita.tgvoice.network.BridgeHttpClient;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Player-facing commands for managing the Minecraft ↔ Telegram link. */
public final class TelegramVoiceCommands {
    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private TelegramVoiceCommands() {}

    public static void register(BridgeHttpClient bridge) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher, bridge));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, BridgeHttpClient bridge) {
        dispatcher.register(Commands.literal("tglink")
                .executes(context -> createCode(context.getSource(), bridge)));
        dispatcher.register(Commands.literal("tgunlink")
                .executes(context -> unlink(context.getSource(), bridge)));
        dispatcher.register(Commands.literal("tgstatus")
                .executes(context -> status(context.getSource(), bridge)));
    }

    private static ServerPlayer player(CommandSourceStack source) {
        return source.getPlayer();
    }

    private static int createCode(CommandSourceStack source, BridgeHttpClient bridge) {
        ServerPlayer player = player(source);
        UUID uuid = player.getUUID();
        bridge.createLinkCode(uuid).whenComplete((code, error) -> player.server.execute(() -> {
            if (error != null) {
                player.sendSystemMessage(Component.literal("§cTelegram Bridge недоступен: " + message(error)));
                return;
            }
            player.sendSystemMessage(Component.literal("§aКод привязки Telegram: §e" + code.code()));
            player.sendSystemMessage(Component.literal("§7Откройте бота и отправьте: §f/link " + code.code()));
            player.sendSystemMessage(Component.literal("§7Код действует до §f" + EXPIRY_FORMAT.format(Instant.ofEpochMilli(code.expiresAt()))));
        }));
        return 1;
    }

    private static int unlink(CommandSourceStack source, BridgeHttpClient bridge) {
        ServerPlayer player = player(source);
        bridge.unlink(player.getUUID()).whenComplete((removed, error) -> player.server.execute(() -> {
            if (error != null) {
                player.sendSystemMessage(Component.literal("§cНе удалось удалить связь: " + message(error)));
            } else if (Boolean.TRUE.equals(removed)) {
                player.sendSystemMessage(Component.literal("§aСвязь Minecraft ↔ Telegram удалена."));
            } else {
                player.sendSystemMessage(Component.literal("§7Активной связи не найдено."));
            }
        }));
        return 1;
    }

    private static int status(CommandSourceStack source, BridgeHttpClient bridge) {
        ServerPlayer player = player(source);
        bridge.linkStatus(player.getUUID()).whenComplete((status, error) -> player.server.execute(() -> {
            if (error != null) {
                player.sendSystemMessage(Component.literal("§cНе удалось получить статус: " + message(error)));
            } else if (status.linked()) {
                player.sendSystemMessage(Component.literal("§aMinecraft ↔ Telegram: подключено."));
                if (status.telegramUserId() != null) {
                    player.sendSystemMessage(Component.literal("§7Telegram user ID: §f" + status.telegramUserId()));
                }
            } else {
                player.sendSystemMessage(Component.literal("§eMinecraft ↔ Telegram: не подключено."));
            }
        }));
        return 1;
    }

    private static String message(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        String text = cause.getMessage();
        return text == null || text.isBlank() ? cause.getClass().getSimpleName() : text;
    }
}
