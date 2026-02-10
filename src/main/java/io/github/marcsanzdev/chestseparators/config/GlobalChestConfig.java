package io.github.marcsanzdev.chestseparators.config;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

import java.io.*;
import java.util.Properties;

// Defines a static configuration manager strictly scoped to the CLIENT environment
// to prevent class loading exceptions on dedicated server environments.
// Utilizes Java Properties for lightweight key-value persistence.
@Environment(EnvType.CLIENT)
public class GlobalChestConfig {

    private static final String CONFIG_FILE = "chestseparators_global.properties";

    // Default visibility state is set to true to maximize feature discoverability
    // for new users upon first installation.
    private static boolean showEditButton = true;

    public static boolean isShowEditButton() {
        return showEditButton;
    }

    // Mutates the visibility state and immediately triggers a persistence cycle
    // to ensure atomicity between the runtime state and the disk storage.
    public static void toggleShowEditButton() {
        showEditButton = !showEditButton;
        saveConfig();
    }

    // Initializes the configuration state from the disk.
    // Implements a self-healing mechanism: if the configuration file is missing,
    // it automatically regenerates the default configuration to prevent I/O errors.
    public static void loadConfig() {
        File file = new File(MinecraftClient.getInstance().runDirectory, "config/" + CONFIG_FILE);
        if (!file.exists()) {
            saveConfig();
            return;
        }

        // Using try-with-resources to guarantee stream closure and prevent resource leaks.
        try (FileInputStream in = new FileInputStream(file)) {
            Properties props = new Properties();
            props.load(in);
            // Parsing employs a default value fallback strategy to ensure robustness
            // against corrupted property files or missing keys.
            showEditButton = Boolean.parseBoolean(props.getProperty("showEditButton", "true"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Serializes the current runtime configuration to the filesystem.
    // Enforces directory structure integrity (mkdirs) before attempting to write the file.
    public static void saveConfig() {
        File configDir = new File(MinecraftClient.getInstance().runDirectory, "config");
        if (!configDir.exists()) configDir.mkdirs();

        File file = new File(configDir, CONFIG_FILE);
        try (FileOutputStream out = new FileOutputStream(file)) {
            Properties props = new Properties();
            props.setProperty("showEditButton", String.valueOf(showEditButton));
            props.store(out, "Chest Separators Global Config");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}