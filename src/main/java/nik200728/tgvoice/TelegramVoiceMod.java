package nik200728.tgvoice;

import dev.nikita.tgvoice.network.VoiceMessagePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TelegramVoiceMod implements ModInitializer {
    public static final String MOD_ID = "tgvoice";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(VoiceMessagePayload.TYPE, VoiceMessagePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(VoiceMessagePayload.TYPE, VoiceMessagePayload.CODEC);
        LOGGER.info("Telegram Voice Messages initialized; proximity voice packets remain isolated.");
    }
}
