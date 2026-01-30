package io.github.marcsanzdev.chestseparators;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.marcsanzdev.chestseparators.network.SyncContainerIdPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class Chestseparators implements ModInitializer {

    public static final String MOD_ID = "chestseparators";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[ChestSeparators] Common/Server Initializer loaded.");
        // REGISTER NETWORK PAYLOAD
        // We tell Fabric: "Hey, we are going to use this packet type for Play (Gameplay) phase"
        PayloadTypeRegistry.playS2C().register(SyncContainerIdPayload.ID, SyncContainerIdPayload.CODEC);
    }
}
