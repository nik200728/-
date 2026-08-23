package dev.nikita.ptv;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PlasmoTelegramVoice implements ModInitializer {
    public static final String MOD_ID = "plasmo-telegram-voice";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Plasmo Telegram Voice Messages {} initialized", "0.1.0");
        VoiceMessageRegistry.init();
    }
}
