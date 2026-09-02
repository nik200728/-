package nik200728.tgvoice;

import dev.nikita.tgvoice.network.BridgeHttpClient;
import dev.nikita.tgvoice.network.ServerMessageRouter;
import dev.nikita.tgvoice.network.VoiceMessagePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TelegramVoiceMod implements ModInitializer {
    public static final String MOD_ID = "tgvoice";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static BridgeHttpClient bridgeClient;
    private static ServerMessageRouter messageRouter;

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

            // Never trust identity fields supplied by the client. The server is authoritative.
            VoiceMessagePayload authoritative = new VoiceMessagePayload(
                    payload.messageId(),
                    context.player().getUUID(),
                    context.player().getGameProfile().name(),
                    payload.durationMillis(),
                    payload.opusData(),
                    payload.waveform()
            );
            context.player().server.execute(() -> {
                var result = messageRouter.accept(authoritative);
                if (result.status() == dev.nikita.tgvoice.network.DeliveryResult.Status.FAILED) {
                    LOGGER.error("Voice Message {} rejected by router: {}", result.messageId(), result.detail());
                }
            });
        });

        LOGGER.info("Telegram Voice Messages initialized; explicit messages are isolated from proximity voice.");
    }
}
