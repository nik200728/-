package dev.nikita.tgvoice.network;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

/** Sends explicit Voice Messages from the Minecraft server to the external bridge. */
public final class BridgeHttpClient {
    private final HttpClient client;
    private final URI endpoint;
    private final String token;

    public BridgeHttpClient() {
        String url = setting("tgvoice.bridge.url", "TGVOICE_BRIDGE_URL", "http://127.0.0.1:8787");
        this.token = setting("tgvoice.bridge.token", "TGVOICE_BRIDGE_TOKEN", "");
        this.endpoint = URI.create(url.endsWith("/") ? url + "v1/messages" : url + "/v1/messages");
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
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return CompletableFuture.failedFuture(
                            new IllegalStateException("Bridge returned HTTP " + response.statusCode()));
                });
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
