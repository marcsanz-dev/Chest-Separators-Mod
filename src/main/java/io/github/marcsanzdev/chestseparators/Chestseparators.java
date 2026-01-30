package io.github.marcsanzdev.chestseparators;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Chestseparators implements ModInitializer {

    public static final String MOD_ID = "chestseparators";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("¡Chest Separators se ha cargado correctamente!");
    }
}
