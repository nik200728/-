package dev.nikita.tgvoice.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** HTTP transport between the Minecraft server and the external Telegram bridge. */
public final class BridgeHttpClient {
    private final HttpClient client;
    private final URI endpoint;
    private final URI inboxEndpoint;
    private final URI inboxAckEndpoint;
    private final URI linkCodeEndpoint;
    private final URI linkUnlinkEndpoint;
    private final URI linkStatusEndpoint;
    private final String token;

    public BridgeHttpClient() {
        String url = setting("tgvoice.bridge.url", "TGVOICE_BRIDGE_URL", "http://127.0.0.1:8787");
        this.token = setting("tgvoice.bridge.token", "TGVOICE_BRIDGE_TOKEN", "");
        String base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.endpoint = URI.create(base + "/v1/messages");
        this.inboxEndpoint = URI.create(base + "/v1/inbox");
        this.inboxAckEndpoint = URI.create(base + "/v1/inbox/ack");
        this.linkCodeEndpoint = URI.create(base + "/v1/link/code");
        this.linkUnlinkEndpoint = URI.create(base + "/v1/link/unlink");
        this.linkStatusEndpoint = URI.create(base + "/v1/link/status");
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public boolean isConfigured() {
        return !token.isBlank() && token.length() >= 32;
    }

    public CompletableFuture<Void> send(VoiceMessagePayload payload) {
        if (!isConfigured()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Bridge token is not configured"));
        }
        String json = "{\"messageId\":\"" + escape(payload.messageId())
                + "\",\"minecraftUuid\":\"" + payload.senderUuid()
                + "\",\"durationMs\":" + payload.durationMillis()
                + ",\"audioBase64\":\"" + Base64.getEncoder().encodeToString(payload.opusData()) + "\"}";
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenCompose(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) return CompletableFuture.completedFuture(null);
                    return CompletableFuture.failedFuture(new IllegalStateException("Bridge returned HTTP " + response.statusCode()));
                });
    }

    /** Creates a short-lived six-digit code for linking the Minecraft account to Telegram. */
    public CompletableFuture<LinkCode> createLinkCode(UUID minecraftUuid) {
        if (!isConfigured()) return CompletableFuture.failedFuture(new IllegalStateException("Bridge token is not configured"));
        String json = "{\"minecraftUuid\":\"" + minecraftUuid + "\"}";
        HttpRequest request = authorizedPost(linkCodeEndpoint, json, Duration.ofSeconds(10));
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenCompose(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return CompletableFuture.failedFuture(new IllegalStateException("Bridge link code returned HTTP " + response.statusCode()));
            }
            try {
                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                return CompletableFuture.completedFuture(new LinkCode(root.get("code").getAsString(), root.get("expiresAt").getAsLong()));
            } catch (RuntimeException exception) {
                return CompletableFuture.failedFuture(new IllegalStateException("Invalid Bridge link code response", exception));
            }
        });
    }

    public CompletableFuture<Boolean> unlink(UUID minecraftUuid) {
        if (!isConfigured()) return CompletableFuture.failedFuture(new IllegalStateException("Bridge token is not configured"));
        HttpRequest request = authorizedPost(linkUnlinkEndpoint, "{\"minecraftUuid\":\"" + minecraftUuid + "\"}", Duration.ofSeconds(10));
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenCompose(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) return CompletableFuture.failedFuture(new IllegalStateException("Bridge unlink returned HTTP " + response.statusCode()));
            try { return CompletableFuture.completedFuture(JsonParser.parseString(response.body()).getAsJsonObject().get("unlinked").getAsBoolean()); }
            catch (RuntimeException e) { return CompletableFuture.failedFuture(new IllegalStateException("Invalid Bridge unlink response", e)); }
        });
    }

    public CompletableFuture<LinkStatus> linkStatus(UUID minecraftUuid) {
        if (!isConfigured()) return CompletableFuture.failedFuture(new IllegalStateException("Bridge token is not configured"));
        URI uri = URI.create(linkStatusEndpoint + "?minecraftUuid=" + minecraftUuid);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token).GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenCompose(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) return CompletableFuture.failedFuture(new IllegalStateException("Bridge status returned HTTP " + response.statusCode()));
            try {
                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                return CompletableFuture.completedFuture(new LinkStatus(root.get("linked").getAsBoolean(), root.has("telegramUserId") ? root.get("telegramUserId").getAsString() : null));
            } catch (RuntimeException e) { return CompletableFuture.failedFuture(new IllegalStateException("Invalid Bridge status response", e)); }
        });
    }

    /** Reads pending messages without deleting them; callers acknowledge after client delivery. */
    public CompletableFuture<List<InboundVoiceMessage>> pollInbox(UUID minecraftUuid) {
        if (!isConfigured()) return CompletableFuture.failedFuture(new IllegalStateException("Bridge token is not configured"));
        URI uri = URI.create(inboxEndpoint + "?minecraftUuid=" + minecraftUuid);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token).GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenCompose(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) return CompletableFuture.failedFuture(new IllegalStateException("Bridge inbox returned HTTP " + response.statusCode()));
            try {
                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonArray array = root.getAsJsonArray("messages");
                List<InboundVoiceMessage> result = new ArrayList<>();
                if (array != null) for (var element : array) {
                    JsonObject item = element.getAsJsonObject();
                    result.add(new InboundVoiceMessage(item.get("messageId").getAsString(), item.get("telegramUserId").getAsString(), item.get("durationMs").getAsLong(), Base64.getDecoder().decode(item.get("audioBase64").getAsString())));
                }
                return CompletableFuture.completedFuture(List.copyOf(result));
            } catch (RuntimeException exception) { return CompletableFuture.failedFuture(new IllegalStateException("Invalid Bridge inbox response", exception)); }
        });
    }

    /** Acknowledges a message only after Minecraft has accepted it for client delivery. */
    public CompletableFuture<Void> acknowledgeInbox(UUID minecraftUuid, String messageId) {
        if (!isConfigured()) return CompletableFuture.failedFuture(new IllegalStateException("Bridge token is not configured"));
        HttpRequest request = authorizedPost(inboxAckEndpoint, "{\"minecraftUuid\":\"" + minecraftUuid + "\",\"messageId\":\"" + escape(messageId) + "\"}", Duration.ofSeconds(10));
        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).thenCompose(response -> {
            if (response.statusCode() >= 200 && response.statusCode() < 300) return CompletableFuture.completedFuture(null);
            return CompletableFuture.failedFuture(new IllegalStateException("Bridge inbox ack returned HTTP " + response.statusCode()));
        });
    }

    private HttpRequest authorizedPost(URI uri, String json, Duration timeout) {
        return HttpRequest.newBuilder(uri).timeout(timeout).header("Authorization", "Bearer " + token).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build();
    }

    public record LinkCode(String code, long expiresAt) {}
    public record LinkStatus(boolean linked, String telegramUserId) {}

    public record InboundVoiceMessage(String messageId, String telegramUserId, long durationMs, byte[] audio) {
        public InboundVoiceMessage {
            if (messageId == null || messageId.isBlank() || messageId.length() > 128) throw new IllegalArgumentException("invalid inbound messageId");
            if (telegramUserId == null || telegramUserId.isBlank()) throw new IllegalArgumentException("invalid telegramUserId");
            if (durationMs < 1 || durationMs > VoiceMessagePayload.MAX_DURATION_MILLIS) throw new IllegalArgumentException("invalid inbound duration");
            if (audio == null || audio.length == 0 || audio.length > VoiceMessagePayload.MAX_AUDIO_BYTES) throw new IllegalArgumentException("invalid inbound audio");
        }
    }

    private static String setting(String property, String env, String fallback) {
        String value = System.getProperty(property);
        if (value != null && !value.isBlank()) return value;
        value = System.getenv(env);
        return value == null ? fallback : value;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
