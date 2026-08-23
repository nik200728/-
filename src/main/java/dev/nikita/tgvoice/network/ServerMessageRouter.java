package dev.nikita.tgvoice.network;

import java.util.Objects;
import java.util.function.Consumer;

/** Routes only Voice Message payloads; proximity voice packets never enter this router. */
public final class ServerMessageRouter {
    private final MessageDeduplicator deduplicator;
    private final Consumer<VoiceMessagePayload> bridgeSink;

    public ServerMessageRouter(int maxTrackedMessages, Consumer<VoiceMessagePayload> bridgeSink) {
        this.deduplicator = new MessageDeduplicator(maxTrackedMessages);
        this.bridgeSink = Objects.requireNonNull(bridgeSink, "bridgeSink");
    }

    public DeliveryResult accept(VoiceMessagePayload payload) {
        if (!deduplicator.accept(payload.messageId())) {
            return new DeliveryResult(payload.messageId(), DeliveryResult.Status.DUPLICATE, "message already accepted");
        }
        try {
            bridgeSink.accept(payload);
            return new DeliveryResult(payload.messageId(), DeliveryResult.Status.ACCEPTED, "queued for bridge");
        } catch (RuntimeException exception) {
            return new DeliveryResult(payload.messageId(), DeliveryResult.Status.FAILED, exception.getMessage());
        }
    }
}
