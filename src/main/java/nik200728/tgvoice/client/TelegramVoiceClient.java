package nik200728.tgvoice.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TelegramVoiceClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("tgvoice/client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Telegram Voice Messages client initialized; Plasmo Voice UI and proximity voice remain untouched.");
    }
}
