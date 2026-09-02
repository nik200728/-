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
    private final URI endpoint, inboxEndpoint, inboxAckEndpoint, linkCodeEndpoint, linkUnlinkEndpoint, linkStatusEndpoint, chatEndpoint;
    private final String token;

    public BridgeHttpClient() {
        String url = setting("tgvoice.bridge.url", "TGVOICE_BRIDGE_URL", "http://127.0.0.1:8787");
        this.token = setting("tgvoice.bridge.token", "TGVOICE_BRIDGE_TOKEN", "");
        String base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        endpoint = URI.create(base + "/v1/messages");
        inboxEndpoint = URI.create(base + "/v1/inbox");
        inboxAckEndpoint = URI.create(base + "/v1/inbox/ack");
        linkCodeEndpoint = URI.create(base + "/v1/link/code");
        linkUnlinkEndpoint = URI.create(base + "/v1/link/unlink");
        linkStatusEndpoint = URI.create(base + "/v1/link/status");
        chatEndpoint = URI.create(base + "/v1/chat");
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public boolean isConfigured() { return !token.isBlank() && token.length() >= 32; }

    public CompletableFuture<Void> send(VoiceMessagePayload payload) {
        if (!isConfigured()) return CompletableFuture.failedFuture(new IllegalStateException("Bridge token is not configured"));
        String json = "{\"messageId\":\"" + escape(payload.messageId()) + "\",\"minecraftUuid\":\"" + payload.senderUuid() + "\",\"durationMs\":" + payload.durationMillis() + ",\"audioBase64\":\"" + Base64.getEncoder().encodeToString(payload.opusData()) + "\"}";
        return client.sendAsync(authorizedPost(endpoint, json, Duration.ofSeconds(30)), HttpResponse.BodyHandlers.discarding()).thenCompose(this::require2xx("Bridge returned HTTP "));
    }

    public CompletableFuture<LinkCode> createLinkCode(UUID minecraftUuid) {
        if (!isConfigured()) return CompletableFuture.failedFuture(new IllegalStateException("Bridge token is not configured"));
        return client.sendAsync(authorizedPost(linkCodeEndpoint, "{\"minecraftUuid\":\"" + minecraftUuid + "\"}", Duration.ofSeconds(10)), HttpResponse.BodyHandlers.ofString()).thenCompose(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) return CompletableFuture.failedFuture(new IllegalStateException("Bridge link code returned HTTP " + response.statusCode()));
            try { JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject(); return CompletableFuture.completedFuture(new LinkCode(root.get("code").getAsString(), root.get("expiresAt").getAsLong())); }
            catch (RuntimeException e) { return CompletableFuture.failedFuture(new IllegalStateException("Invalid Bridge link code response", e)); }
        });
    }

    public CompletableFuture<Boolean> unlink(UUID minecraftUuid) {
        if (!isConfigured()) return CompletableFuture.failedFuture(new IllegalStateException("Bridge token is not configured"));
        return client.sendAsync(authorizedPost(linkUnlinkEndpoint, "{\"minecraftUuid\":\"" + minecraftUuid + "\"}", Duration.ofSeconds(10)), HttpResponse.BodyHandlers.ofString()).thenCompose(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) return CompletableFuture.failedFuture(new IllegalStateException("Bridge unlink returned HTTP " + response.statusCode()));
            try { return CompletableFuture.completedFuture(JsonParser.parseString(response.body()).getAsJsonObject().get("unlinked").getAsBoolean()); }
            catch (RuntimeException e) { return CompletableFuture.failedFuture(new IllegalStateException("Invalid Bridge unlink response", e)); }
        });
    }

    public CompletableFuture<LinkStatus> linkStatus(UUID minecraftUuid) {
        if (!isConfigured()) return CompletableFuture.failedFuture(new IllegalStateException("Bridge token is not configured"));
        URI uri = URI.create(linkStatusEndpoint + "?minecraftUuid=" + minecraftUuid);
        return client.sendAsync(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString()).thenCompose(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) return CompletableFuture.failedFuture(new IllegalStateException("Bridge status returned HTTP " + response.statusCode()));
            try { JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject(); return CompletableFuture.completedFuture(new LinkStatus(root.get("linked").getAsBoolean(), root.has("telegramUserId") ? root.get("telegramUserId").getAsString() : null)); }
            catch (RuntimeException e) { return CompletableFuture.failedFuture(new IllegalStateException("Invalid Bridge status response", e)); }
        });
    }

    public CompletableFuture<Void> sendChat(UUID minecraftUuid, String text) {
        if (!isConfigured()) return CompletableFuture.failedFuture(new IllegalStateException("Bridge token is not configured"));
        if (text == null || text.isBlank() || text.length() > 4096) return CompletableFuture.failedFuture(new IllegalArgumentException("chat text must contain 1-4096 characters"));
        String json = "{\"minecraftUuid\":\"" + minecraftUuid + "\",\"text\":\"" + escape(text) + "\"}";
        return client.sendAsync(authorizedPost(chatEndpoint, json, Duration.ofSeconds(15)), HttpResponse.BodyHandlers.discarding()).thenCompose(this::require2xx("Bridge chat returned HTTP "));
    }

    public CompletableFuture<List<InboundVoiceMessage>> pollInbox(UUID minecraftUuid) {
        if (!isConfigured()) return CompletableFuture.failedFuture(new IllegalStateException("Bridge token is not configured"));
        URI uri = URI.create(inboxEndpoint + "?minecraftUuid=" + minecraftUuid);
        return client.sendAsync(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString()).thenCompose(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) return CompletableFuture.failedFuture(new IllegalStateException("Bridge inbox returned HTTP " + response.statusCode()));
            try {
                JsonArray array = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("messages"); List<InboundVoiceMessage> result = new ArrayList<>();
                if (array != null) for (var element : array) { JsonObject item = element.getAsJsonObject(); result.add(new InboundVoiceMessage(item.get("messageId").getAsString(), item.get("telegramUserId").getAsString(), item.get("durationMs").getAsLong(), Base64.getDecoder().decode(item.get("audioBase64").getAsString()))); }
                return CompletableFuture.completedFuture(List.copyOf(result));
            } catch (RuntimeException e) { return CompletableFuture.failedFuture(new IllegalStateException("Invalid Bridge inbox response", e)); }
        });
    }

    public CompletableFuture<Void> acknowledgeInbox(UUID minecraftUuid, String messageId) {
        if (!isConfigured()) return CompletableFuture.failedFuture(new IllegalStateException("Bridge token is not configured"));
        return client.sendAsync(authorizedPost(inboxAckEndpoint, "{\"minecraftUuid\":\"" + minecraftUuid + "\",\"messageId\":\"" + escape(messageId) + "\"}", Duration.ofSeconds(10)), HttpResponse.BodyHandlers.discarding()).thenCompose(this::require2xx("Bridge inbox ack returned HTTP "));
    }

    private CompletableFuture<Void> require2xx(HttpResponse<?> response, String prefix) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) return CompletableFuture.completedFuture(null);
        return CompletableFuture.failedFuture(new IllegalStateException(prefix + response.statusCode()));
    }
    private HttpRequest authorizedPost(URI uri, String json, Duration timeout) { return HttpRequest.newBuilder(uri).timeout(timeout).header("Authorization", "Bearer " + token).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build(); }

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
    private static String setting(String property, String env, String fallback) { String value = System.getProperty(property); if (value != null && !value.isBlank()) return value; value = System.getenv(env); return value == null ? fallback : value; }
    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
