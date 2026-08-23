package nik200728.tgvoice.client.plasmovoice;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Async boundary for the Plasmo Voice Opus encoder.
 * The concrete adapter is intentionally isolated here so API changes in
 * Plasmo Voice do not leak into recording/network code.
 */
public final class PlasmoVoiceOpusEncoder {
    private final Executor executor;
    private final Consumer<short[]> encodeImplementation;

    public PlasmoVoiceOpusEncoder(Executor executor, Consumer<short[]> encodeImplementation) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.encodeImplementation = Objects.requireNonNull(encodeImplementation, "encodeImplementation");
    }

    public CompletableFuture<Void> encodeAsync(short[] pcm) {
        Objects.requireNonNull(pcm, "pcm");
        return CompletableFuture.runAsync(() -> encodeImplementation.accept(pcm), executor);
    }
}
