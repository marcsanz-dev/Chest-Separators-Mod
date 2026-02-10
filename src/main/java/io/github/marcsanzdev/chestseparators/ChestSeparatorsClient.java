package io.github.marcsanzdev.chestseparators;

import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.event.KeyInputHandler;
import net.fabricmc.api.ClientModInitializer;

// Implements the client-side bootstrap logic for the Fabric mod lifecycle.
// This entry point is isolated from the dedicated server path to ensure strict separation of concerns
// regarding rendering and input handling subsystems, preventing server-side class loading violations.
public class ChestSeparatorsClient implements ClientModInitializer {

    // Lifecycle hook invoked by the Fabric Loader specifically for the physical client environment.
    // Orchestrates the initialization of client-exclusive modules such as configuration IO
    // and input event registration.
    @Override
    public void onInitializeClient() {
        // Hydrates the global configuration state from disk immediately upon startup.
        // This preemptive loading strategy ensures that render systems have valid state access
        // during their initialization phase, preventing race conditions or uninitialized state access.
        GlobalChestConfig.loadConfig();

        // Registers the GLFW keybindings and attaches the associated event listeners
        // to the client tick loop. This establishes the input processing pipeline
        // required for user interaction with the mod's features.
        KeyInputHandler.register();
    }
}