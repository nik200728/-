package dev.nikita.tgvoice.network;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;

/** Constant-time bearer-token validation for the Minecraft-server to Bridge boundary. */
public final class BridgeAuth {
    private final byte[] expected;

    public BridgeAuth(String token) {
        if (token == null || token.length() < 32) throw new IllegalArgumentException("Bridge token must be at least 32 characters");
        this.expected = token.getBytes(StandardCharsets.UTF_8);
    }

    public boolean verify(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) return false;
        byte[] supplied = authorizationHeader.substring(7).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, supplied);
    }

    public String encodedLengthHint() {
        return Base64.getEncoder().encodeToString(new byte[expected.length]).substring(0, Math.min(8, expected.length));
    }
}
