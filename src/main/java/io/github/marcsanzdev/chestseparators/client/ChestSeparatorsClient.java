package io.github.marcsanzdev.chestseparators.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChestSeparatorsClient implements ClientModInitializer{
    public static final Logger LOGGER = LoggerFactory.getLogger("chestseparators-client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("[ChestSeparators] Client Initializer loaded.");
    }
}
