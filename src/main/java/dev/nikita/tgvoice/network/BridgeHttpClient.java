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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** HTTP transport between the Minecraft server and the external Telegram bridge. */
public final class BridgeHttpClient {
    private static final int MAX_SEND_ATTEMPTS = 4;
    private static final long[] RETRY_DELAYS_MS = {500L, 1000L, 2000L};
    private final HttpClient client;
    private final ScheduledExecutorService retryExecutor;
    private final URI endpoint, inboxEndpoint, inboxAckEndpoint, linkCodeEndpoint, linkUnlinkEndpoint, linkStatusEndpoint, chatEndpoint, videoEndpoint;
    private final String token;

    public BridgeHttpClient() {
        String url = setting("tgvoice.bridge.url", "TGVOICE_BRIDGE_URL", "http://127.0.0.1:8787");
        this.token = setting("tgvoice.bridge.token", "TGVOICE_BRIDGE_TOKEN", "");
        String base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        endpoint = URI.create(base + "/v1/messages"); inboxEndpoint = URI.create(base + "/v1/inbox"); inboxAckEndpoint = URI.create(base + "/v1/inbox/ack");
        linkCodeEndpoint = URI.create(base + "/v1/link/code"); linkUnlinkEndpoint = URI.create(base + "/v1/link/unlink"); linkStatusEndpoint = URI.create(base + "/v1/link/status"); chatEndpoint = URI.create(base + "/v1/chat"); videoEndpoint = URI.create(base + "/v1/video-notes");
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        retryExecutor = Executors.newSingleThreadScheduledExecutor(r -> { Thread thread = new Thread(r, "tgvoice-bridge-retry"); thread.setDaemon(true); return thread; });
    }
    public boolean isConfigured() { return !token.isBlank() && token.length() >= 32; }
    public CompletableFuture<Void> send(VoiceMessagePayload payload) {
        if (!isConfigured()) return CompletableFuture.failedFuture(new IllegalStateException("Bridge token is not configured"));
        String json = "{\"messageId\":\"" + escape(payload.messageId()) + "\",\"minecraftUuid\":\"" + payload.senderUuid() + "\",\"durationMs\":" + payload.durationMillis() + ",\"audioBase64\":\"" + Base64.getEncoder().encodeToString(payload.opusData()) + "\"}";
        return sendVoiceAttempt(json, 1);
    }
    public CompletableFuture<Void> send(VideoNotePayload payload) {
        if (!isConfigured()) return CompletableFuture.failedFuture(new IllegalStateException("Bridge token is not configured"));
        String json = "{\"messageId\":\"" + escape(payload.messageId()) + "\",\"minecraftUuid\":\"" + payload.senderUuid() + "\",\"durationMs\":" + payload.durationMillis() + ",\"width\":" + payload.width() + ",\"height\":" + payload.height() + ",\"frameRate\":" + payload.frameRate() + ",\"videoBase64\":\"" + Base64.getEncoder().encodeToString(payload.videoData()) + "\"}";
        return sendVideoAttempt(json, 1);
    }
    private CompletableFuture<Void> sendVoiceAttempt(String json, int attempt) { return sendAttempt(endpoint, json, attempt, "Bridge returned HTTP "); }
    private CompletableFuture<Void> sendVideoAttempt(String json, int attempt) { return sendAttempt(videoEndpoint, json, attempt, "Bridge video returned HTTP "); }
    private CompletableFuture<Void> sendAttempt(URI uri, String json, int attempt, String errorPrefix) {
        return client.sendAsync(authorizedPost(uri, json, Duration.ofSeconds(60)), HttpResponse.BodyHandlers.discarding()).handle((response, error) -> {
            if (error != null) { if (attempt < MAX_SEND_ATTEMPTS) return retryFuture(uri, json, attempt + 1, errorPrefix); return CompletableFuture.<Void>failedFuture(unwrap(error)); }
            if (response.statusCode() >= 200 && response.statusCode() < 300) return CompletableFuture.<Void>completedFuture(null);
            if (response.statusCode() >= 500 && attempt < MAX_SEND_ATTEMPTS) return retryFuture(uri, json, attempt + 1, errorPrefix);
            return CompletableFuture.<Void>failedFuture(new IllegalStateException(errorPrefix + response.statusCode()));
        }).thenCompose(future -> future);
    }
    private CompletableFuture<Void> retryFuture(URI uri, String json, int nextAttempt, String errorPrefix) { long delay = RETRY_DELAYS_MS[Math.min(nextAttempt - 2, RETRY_DELAYS_MS.length - 1)]; CompletableFuture<Void> result = new CompletableFuture<>(); retryExecutor.schedule(() -> sendAttempt(uri, json, nextAttempt, errorPrefix).whenComplete((ignored, error) -> { if (error == null) result.complete(null); else result.completeExceptionally(error); }), delay, TimeUnit.MILLISECONDS); return result; }
    public CompletableFuture<LinkCode> createLinkCode(UUID minecraftUuid) { if (!isConfigured()) return CompletableFuture.failedFuture(new IllegalStateException("Bridge token is not configured")); return client.sendAsync(authorizedPost(linkCodeEndpoint, "{\"minecraftUuid\":\"" + minecraftUuid + "\"}", Duration.ofSeconds(10)), HttpResponse.BodyHandlers.ofString()).thenCompose(response -> { if (response.statusCode() < 200 || response.statusCode() >= 300) return CompletableFuture.failedFuture(new IllegalStateException("Bridge link code returned HTTP " + response.statusCode())); try { JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject(); return CompletableFuture.completedFuture(new LinkCode(root.get("code").getAsString(), root.get("expiresAt").getAsLong())); } catch (RuntimeException e) { return CompletableFuture.failedFuture(new IllegalStateException("Invalid Bridge link code response", e)); } }); }
    public CompletableFuture<Boolean> unlink(UUID minecraftUuid) { if (!isConfigured()) return CompletableFuture.failedFuture(new IllegalStateException("Bridge token is not configured")); return client.sendAsync(authorizedPost(linkUnlinkEndpoint, "{\"minecraftUuid\":\"" + minecraftUuid + "\"}", Duration.ofSeconds(10)), HttpResponse.BodyHandlers.ofString()).thenCompose(response -> { if (response.statusCode() < 200 || response.statusCode() >= 300) return CompletableFuture.failedFuture(new IllegalStateException("Bridge unlink returned HTTP " + response.statusCode())); try { return CompletableFuture.completedFuture(JsonParser.parseString(response.body()).getAsJsonObject().get("unlinked").getAsBoolean()); } catch (RuntimeException e) { return CompletableFuture.failedFuture(new IllegalStateException("Invalid Bridge unlink response", e)); } }); }
    public CompletableFuture<LinkStatus> linkStatus(UUID minecraftUuid) { if (!isConfigured()) return CompletableFuture.failedFuture(new IllegalStateException("Bridge token is not configured")); URI uri = URI.create(linkStatusEndpoint + "?minecraftUuid=" + minecraftUuid); return client.sendAsync(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString()).thenCompose(response -> { if (response.statusCode() < 200 || response.statusCode() >= 300) return CompletableFuture.failedFuture(new IllegalStateException("Bridge status returned HTTP " + response.statusCode())); try { JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject(); return CompletableFuture.completedFuture(new LinkStatus(root.get("linked").getAsBoolean(), root.has("telegramUserId") ? root.get("telegramUserId").getAsString() : null)); } catch (RuntimeException e) { return CompletableFuture.failedFuture(new IllegalStateException("Invalid Bridge status response", e)); } }); }
    public CompletableFuture<Void> sendChat(UUID minecraftUuid, String text) { if (!isConfigured()) return CompletableFuture.failedFuture(new IllegalStateException("Bridge token is not configured")); if (text == null || text.isBlank() || text.length() > 4096) return CompletableFuture.failedFuture(new IllegalArgumentException("chat text must contain 1-4096 characters")); String json = "{\"minecraftUuid\":\"" + minecraftUuid + "\",\"text\":\"" + escape(text) + "\"}"; return client.sendAsync(authorizedPost(chatEndpoint, json, Duration.ofSeconds(15)), HttpResponse.BodyHandlers.discarding()).thenApply(response -> require2xxValue(response, "Bridge chat returned HTTP ")); }
    public CompletableFuture<List<InboundMessage>> pollInbox(UUID minecraftUuid) { if (!isConfigured()) return CompletableFuture.failedFuture(new IllegalStateException("Bridge token is not configured")); URI uri = URI.create(inboxEndpoint + "?minecraftUuid=" + minecraftUuid); return client.sendAsync(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString()).thenCompose(response -> { if (response.statusCode() < 200 || response.statusCode() >= 300) return CompletableFuture.failedFuture(new IllegalStateException("Bridge inbox returned HTTP " + response.statusCode())); try { JsonArray array = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("messages"); List<InboundMessage> result = new ArrayList<>(); if (array != null) for (var element : array) { JsonObject item = element.getAsJsonObject(); String kind = item.has("kind") ? item.get("kind").getAsString() : "voice"; if ("video_note".equals(kind)) result.add(new InboundVideoMessage(item.get("messageId").getAsString(), item.get("telegramUserId").getAsString(), item.get("durationMs").getAsLong(), Base64.getDecoder().decode(item.get("videoBase64").getAsString()), item.get("width").getAsInt(), item.get("height").getAsInt(), item.get("frameRate").getAsInt())); else result.add(new InboundVoiceMessage(item.get("messageId").getAsString(), item.get("telegramUserId").getAsString(), item.get("durationMs").getAsLong(), Base64.getDecoder().decode(item.get("audioBase64").getAsString()))); } return CompletableFuture.completedFuture(List.copyOf(result)); } catch (RuntimeException e) { return CompletableFuture.failedFuture(new IllegalStateException("Invalid Bridge inbox response", e)); } }); }
    public CompletableFuture<Void> acknowledgeInbox(UUID minecraftUuid, String messageId) { if (!isConfigured()) return CompletableFuture.failedFuture(new IllegalStateException("Bridge token is not configured")); return client.sendAsync(authorizedPost(inboxAckEndpoint, "{\"minecraftUuid\":\"" + minecraftUuid + "\",\"messageId\":\"" + escape(messageId) + "\"}", Duration.ofSeconds(10)), HttpResponse.BodyHandlers.discarding()).thenApply(response -> require2xxValue(response, "Bridge inbox ack returned HTTP ")); }
    private static Void require2xxValue(HttpResponse<?> response, String prefix) { if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException(prefix + response.statusCode()); return null; }
    private HttpRequest authorizedPost(URI uri, String json, Duration timeout) { return HttpRequest.newBuilder(uri).timeout(timeout).header("Authorization", "Bearer " + token).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build(); }
    public sealed interface InboundMessage permits InboundVoiceMessage, InboundVideoMessage { String messageId(); String telegramUserId(); long durationMs(); }
    public record LinkCode(String code, long expiresAt) {}
    public record LinkStatus(boolean linked, String telegramUserId) {}
    public record InboundVoiceMessage(String messageId, String telegramUserId, long durationMs, byte[] audio) implements InboundMessage {
        public InboundVoiceMessage { if (messageId == null || messageId.isBlank() || messageId.length() > 128) throw new IllegalArgumentException("invalid inbound messageId"); if (telegramUserId == null || telegramUserId.isBlank()) throw new IllegalArgumentException("invalid telegramUserId"); if (durationMs < 1 || durationMs > VoiceMessagePayload.MAX_DURATION_MILLIS) throw new IllegalArgumentException("invalid inbound duration"); if (audio == null || audio.length == 0 || audio.length > VoiceMessagePayload.MAX_AUDIO_BYTES) throw new IllegalArgumentException("invalid inbound audio"); }
    }
    public record InboundVideoMessage(String messageId, String telegramUserId, long durationMs, byte[] video, int width, int height, int frameRate) implements InboundMessage {
        public InboundVideoMessage {
            if (messageId == null || messageId.isBlank() || messageId.length() > 128) throw new IllegalArgumentException("invalid inbound messageId");
            if (telegramUserId == null || telegramUserId.isBlank()) throw new IllegalArgumentException("invalid telegramUserId");
            if (video == null || video.length == 0 || video.length > VideoNotePayload.MAX_VIDEO_BYTES) throw new IllegalArgumentException("invalid inbound video");
            new VideoNotePayload(messageId, UUID.nameUUIDFromBytes(("tgvoice:telegram:" + telegramUserId).getBytes(java.nio.charset.StandardCharsets.UTF_8)), "Telegram", durationMs, width, height, frameRate, video);
        }
    }
    private static String setting(String property, String env, String fallback) { String value = System.getProperty(property); if (value != null && !value.isBlank()) return value; value = System.getenv(env); return value == null ? fallback : value; }
    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static Throwable unwrap(Throwable error) { return error.getCause() == null ? error : error.getCause(); }
}
