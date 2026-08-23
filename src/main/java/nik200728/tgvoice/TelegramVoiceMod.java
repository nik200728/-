package nik200728.tgvoice;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TelegramVoiceMod implements ModInitializer {
    public static final String MOD_ID = "tgvoice";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Telegram Voice Messages addon initialized; Plasmo Voice proximity audio is left untouched.");
    }
}
