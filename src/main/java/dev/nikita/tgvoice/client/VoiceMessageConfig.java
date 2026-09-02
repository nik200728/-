package dev.nikita.tgvoice.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Small dependency-free-on-the-mod-side persistent client configuration. */
public final class VoiceMessageConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("tgvoice.json");
    private static final int DEFAULT_MAX_DURATION_SECONDS = 120;

    private static VoiceMessageConfig instance;

    public boolean toggleMode = false;
    public int maxDurationSeconds = DEFAULT_MAX_DURATION_SECONDS;

    private VoiceMessageConfig() {}

    public static synchronized VoiceMessageConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    public synchronized void save() {
        maxDurationSeconds = clamp(maxDurationSeconds, 1, DEFAULT_MAX_DURATION_SECONDS);
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            System.err.println("[tgvoice] Failed to save config: " + exception.getMessage());
        }
    }

    public long maxDurationMillis() {
        return clamp(maxDurationSeconds, 1, DEFAULT_MAX_DURATION_SECONDS) * 1000L;
    }

    private static VoiceMessageConfig load() {
        if (!Files.isRegularFile(PATH)) {
            VoiceMessageConfig config = new VoiceMessageConfig();
            config.save();
            return config;
        }
        try {
            VoiceMessageConfig config = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), VoiceMessageConfig.class);
            if (config == null) config = new VoiceMessageConfig();
            config.maxDurationSeconds = clamp(config.maxDurationSeconds, 1, DEFAULT_MAX_DURATION_SECONDS);
            return config;
        } catch (Exception exception) {
            System.err.println("[tgvoice] Failed to read config, using defaults: " + exception.getMessage());
            return new VoiceMessageConfig();
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
