package io.github.marcsanzdev.chestseparators.event;

import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

// Manages client-side input registration and event handling.
// Acts as the bridge between the raw GLFW input layer and the mod's configuration logic.
public class KeyInputHandler {

    public static KeyBinding toggleButtonKey;

    // Defines a dedicated keybinding category to group mod-specific controls within the game settings options.
    private static final KeyBinding.Category CHEST_SEPARATORS_CATEGORY = KeyBinding.Category.create(
            Identifier.of("chestseparators", "general")
    );

    // Initializes and registers the keybinding definition with the Fabric API.
    // Uses GLFW_KEY_C as the default trigger, mapped to the custom category.
    public static void register() {
        toggleButtonKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chestseparators.toggle_button",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_C,
                CHEST_SEPARATORS_CATEGORY
        ));

        registerKeyInputs();
    }

    // Subscribes to the client tick loop to poll for input events.
    // This approach ensures input is captured reliably even if the frame rate fluctuates.
    private static void registerKeyInputs() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Processing a "wasPressed" event consumes the input buffer, preventing multiple triggers per tick.
            while (toggleButtonKey.wasPressed()) {
                GlobalChestConfig.toggleShowEditButton();

                if (client.player != null) {
                    // Dynamically resolves the localization key based on the new configuration state
                    // to ensure user feedback matches the actual runtime value.
                    String key = GlobalChestConfig.isShowEditButton() ?
                            "message.chestseparators.button_visible" :
                            "message.chestseparators.button_hidden";

                    // Dispatches an overlay message (Action Bar) rather than a chat message
                    // to provide immediate, transient feedback without cluttering the chat history.
                    client.player.sendMessage(
                            Text.translatable(key).formatted(Formatting.GRAY),
                            true
                    );
                }
            }
        });
    }
}