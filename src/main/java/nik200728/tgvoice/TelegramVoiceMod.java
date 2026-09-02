package nik200728.tgvoice;

import dev.nikita.tgvoice.network.BridgeHttpClient;
import dev.nikita.tgvoice.network.ServerMessageRouter;
import dev.nikita.tgvoice.network.VoiceMessagePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TelegramVoiceMod implements ModInitializer {
    public static final String MOD_ID = "tgvoice";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final int INBOX_POLL_INTERVAL_TICKS = 40;
    private static BridgeHttpClient bridgeClient;
    private static ServerMessageRouter messageRouter;
    private static final Set<UUID> INBOX_POLLS_IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static int inboxTickCounter;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(VoiceMessagePayload.TYPE, VoiceMessagePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(VoiceMessagePayload.TYPE, VoiceMessagePayload.CODEC);

        bridgeClient = new BridgeHttpClient();
        messageRouter = new ServerMessageRouter(4096, payload -> bridgeClient.send(payload)
                .exceptionally(error -> {
                    LOGGER.error("Voice Message {} failed to reach bridge", payload.messageId(), error);
                    return null;
                }));

        ServerPlayNetworking.registerGlobalReceiver(VoiceMessagePayload.TYPE, (payload, context) -> {
            if (payload.opusData().length > VoiceMessagePayload.MAX_AUDIO_BYTES
                    || payload.waveform().length > VoiceMessagePayload.MAX_WAVEFORM_BYTES
                    || payload.durationMillis() > VoiceMessagePayload.MAX_DURATION_MILLIS) {
                LOGGER.warn("Rejected oversized Voice Message from {}", context.player().getGameProfile().name());
                return;
            }

            VoiceMessagePayload authoritative = new VoiceMessagePayload(
                    payload.messageId(),
                    context.player().getUUID(),
                    context.player().getGameProfile().name(),
                    payload.durationMillis(),
                    payload.opusData(),
                    payload.waveform()
            );
            var result = messageRouter.accept(authoritative);
            if (result.status() == dev.nikita.tgvoice.network.DeliveryResult.Status.FAILED) {
                LOGGER.error("Voice Message {} rejected by router: {}", result.messageId(), result.detail());
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(TelegramVoiceMod::pollTelegramInbox);
        LOGGER.info("Telegram Voice Messages initialized; explicit messages are isolated from proximity voice.");
    }

    private static void pollTelegramInbox(MinecraftServer server) {
        if (!bridgeClient.isConfigured()) return;
        if (++inboxTickCounter < INBOX_POLL_INTERVAL_TICKS) return;
        inboxTickCounter = 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerUuid = player.getUUID();
            if (!INBOX_POLLS_IN_FLIGHT.add(playerUuid)) continue;

            bridgeClient.pollInbox(playerUuid).whenComplete((messages, error) -> {
                INBOX_POLLS_IN_FLIGHT.remove(playerUuid);
                if (error != null) {
                    LOGGER.debug("Telegram inbox poll failed for {}: {}", playerUuid, error.getMessage());
                    return;
                }
                if (messages == null || messages.isEmpty()) return;

                server.execute(() -> {
                    ServerPlayer target = server.getPlayerList().getPlayer(playerUuid);
                    if (target == null) return;

                    for (BridgeHttpClient.InboundVoiceMessage message : messages) {
                        UUID telegramSender = UUID.nameUUIDFromBytes(
                                ("tgvoice:telegram:" + message.telegramUserId()).getBytes(StandardCharsets.UTF_8));
                        try {
                            ServerPlayNetworking.send(target, new VoiceMessagePayload(
                                    message.messageId(),
                                    telegramSender,
                                    "Telegram",
                                    message.durationMs(),
                                    message.audio(),
                                    new byte[]{0}
                            ));
                        } catch (RuntimeException exception) {
                            LOGGER.warn("Failed to deliver Telegram Voice Message {} to {}: {}",
                                    message.messageId(), playerUuid, exception.getMessage());
                        }
                    }
                });
            });
        }
    }
}
