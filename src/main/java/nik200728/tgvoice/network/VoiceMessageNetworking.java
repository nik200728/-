package nik200728.tgvoice.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Fabric play-stage networking for explicit Voice Messages only. */
public final class VoiceMessageNetworking {
    private VoiceMessageNetworking() {}

    public static void registerPayloadTypes() {
        PayloadTypeRegistry.serverboundPlay().register(SendVoiceMessagePayload.TYPE, SendVoiceMessagePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DeliverVoiceMessagePayload.TYPE, DeliverVoiceMessagePayload.CODEC);
    }

    public static void registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(SendVoiceMessagePayload.TYPE, (payload, context) -> {
            ServerPlayer sender = context.player();
            DeliverVoiceMessagePayload outbound = new DeliverVoiceMessagePayload(
                    payload.messageId(),
                    sender.getUUID(),
                    sender.getGameProfile().name(),
                    Math.toIntExact(payload.durationMillis()),
                    payload.opusOgg(),
                    payload.waveform()
            );

            sender.level().getServer().getPlayerList().getPlayers().forEach(player -> {
                if (ServerPlayNetworking.canSend(player, DeliverVoiceMessagePayload.TYPE)) {
                    ServerPlayNetworking.send(player, outbound);
                }
            });
        });
    }

    public static void registerClientReceiver(ClientVoiceMessageConsumer consumer) {
        ClientPlayNetworking.registerGlobalReceiver(DeliverVoiceMessagePayload.TYPE,
                (payload, context) -> consumer.accept(payload));
    }

    @FunctionalInterface
    public interface ClientVoiceMessageConsumer {
        void accept(DeliverVoiceMessagePayload payload);
    }
}
