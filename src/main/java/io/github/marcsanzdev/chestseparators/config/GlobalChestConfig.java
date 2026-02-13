package io.github.marcsanzdev.chestseparators.config;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@Environment(EnvType.CLIENT)
public class GlobalChestConfig {

    private static final String CONFIG_FILE = "chestseparators_global.properties";
    private static boolean showEditButton = true;

    public static boolean isShowEditButton() {
        return showEditButton;
    }

    public static void toggleShowEditButton() {
        showEditButton = !showEditButton;
        saveConfig();
    }

    public static void loadConfig() {
        Path configDir = MinecraftClient.getInstance().runDirectory.toPath().resolve("config");
        Path file = configDir.resolve(CONFIG_FILE);

        if (!Files.exists(file)) {
            saveConfig();
            return;
        }

        try (InputStream in = Files.newInputStream(file)) {
            Properties props = new Properties();
            props.load(in);
            showEditButton = Boolean.parseBoolean(props.getProperty("showEditButton", "true"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveConfig() {
        Path configDir = MinecraftClient.getInstance().runDirectory.toPath().resolve("config");
        try {
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }

            Path file = configDir.resolve(CONFIG_FILE);
            try (OutputStream out = Files.newOutputStream(file)) {
                Properties props = new Properties();
                props.setProperty("showEditButton", String.valueOf(showEditButton));
                props.store(out, "Chest Separators Global Config");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}