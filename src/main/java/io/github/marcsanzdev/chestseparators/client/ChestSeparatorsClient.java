package io.github.marcsanzdev.chestseparators.client;

import io.github.marcsanzdev.chestseparators.network.SyncContainerIdPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class ChestSeparatorsClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("chestseparators-client");

    // Aquí guardaremos el ID del cofre que el jugador está mirando actualmente.
    // Si es null, significa que no hay ningún cofre (con ID) abierto.
    public static UUID currentContainerId = null;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[ChestSeparators] Initializing Client Networking...");

        // Registramos el RECEPTOR (The Receiver)
        // Cuando llegue un paquete de tipo SyncContainerIdPayload, ejecuta esto:
        ClientPlayNetworking.registerGlobalReceiver(SyncContainerIdPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                // Código que se ejecuta en el hilo principal del juego
                currentContainerId = payload.uuid();
                LOGGER.info("Client received Container ID: " + currentContainerId);
            });
        });
    }
}