package io.github.marcsanzdev.chestseparators.data;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Singleton state manager for chest separator configurations.
// Handles NBT serialization/deserialization, runtime state management, and file I/O operations.
// Strictly confined to the CLIENT side as layout data is visual-only and local to the user.
@Environment(EnvType.CLIENT)
public class ChestConfigManager {

    private static final String MOD_ID = "chestseparators";
    private static final String FOLDER_NAME = "separators";
    private static final String ENDER_FILE_NAME = "ender_chest.dat";

    // Bitmask flags representing the four cardinal directions for line rendering.
    // Enables efficient bitwise operations for storing multiple lines in a single integer.
    public static final int ACTION_TOP = 1;
    public static final int ACTION_BOTTOM = 2;
    public static final int ACTION_LEFT = 4;
    public static final int ACTION_RIGHT = 8;

    // Data packing constants.
    // Each line direction occupies 5 bits to store color index (0-31), though palette is smaller.
    // 5 bits * 4 directions = 20 bits total, fitting comfortably within a 32-bit Integer.
    private static final int MASK_5BITS = 0x1F;
    private static final int SHIFT_TOP = 0;
    private static final int SHIFT_BOTTOM = 5;
    private static final int SHIFT_LEFT = 10;
    private static final int SHIFT_RIGHT = 15;

    // Runtime cache for the currently opened container's configuration.
    // Maps Slot Index -> Packed Integer Data (Color + Direction).
    private final Map<Integer, Integer> currentChestConfig = new HashMap<>();

    // Volatile storage for copy-paste functionality. Persists only during the session.
    private Map<Integer, Integer> clipboardConfig = null;

    private static final ChestConfigManager INSTANCE = new ChestConfigManager();

    public static ChestConfigManager getInstance() {
        return INSTANCE;
    }

    // Resets the active configuration state. Called when closing a container GUI.
    public void clearCurrentConfig() {
        currentChestConfig.clear();
    }

    // Retrieves the packed data integer for a specific slot index.
    // Returns 0 (no lines) if the slot has no configuration.
    public int getSlotData(int slotIndex) {
        return currentChestConfig.getOrDefault(slotIndex, 0);
    }

    // Unpacks the color index from the integer data for a specific direction shift.
    // Returns -1 if no line is present in that direction.
    // Subtracts 1 to convert stored value (1-based) back to 0-based palette index.
    public int getLineColor(int slotData, int shift) {
        int val = (slotData >> shift) & MASK_5BITS;
        if (val == 0) return -1;
        return val - 1;
    }

    // Applies a new line color to the specified slot and direction(s).
    // Stores color as 1-based index to distinguish "color 0" from "no line".
    public void paintLine(int slotIndex, int actionFlags, int selectedColorIndex) {
        int data = currentChestConfig.getOrDefault(slotIndex, 0);
        int colorVal = selectedColorIndex + 1;

        if ((actionFlags & ACTION_TOP) != 0) data = updateLine(data, SHIFT_TOP, colorVal);
        if ((actionFlags & ACTION_BOTTOM) != 0) data = updateLine(data, SHIFT_BOTTOM, colorVal);
        if ((actionFlags & ACTION_LEFT) != 0) data = updateLine(data, SHIFT_LEFT, colorVal);
        if ((actionFlags & ACTION_RIGHT) != 0) data = updateLine(data, SHIFT_RIGHT, colorVal);

        currentChestConfig.put(slotIndex, data);
    }

    // Clears line data for the specified direction(s) using bitwise masking.
    // If the resulting data is 0, the entry is removed from the map to save memory.
    public void removeLine(int slotIndex, int actionFlags) {
        int data = currentChestConfig.getOrDefault(slotIndex, 0);

        if ((actionFlags & ACTION_TOP) != 0) data &= ~(MASK_5BITS << SHIFT_TOP);
        if ((actionFlags & ACTION_BOTTOM) != 0) data &= ~(MASK_5BITS << SHIFT_BOTTOM);
        if ((actionFlags & ACTION_LEFT) != 0) data &= ~(MASK_5BITS << SHIFT_LEFT);
        if ((actionFlags & ACTION_RIGHT) != 0) data &= ~(MASK_5BITS << SHIFT_RIGHT);

        if (data == 0) currentChestConfig.remove(slotIndex);
        else currentChestConfig.put(slotIndex, data);
    }

    // Helper method to perform bitwise insertion of new data.
    // Clears the target 5 bits first, then ORs the new value.
    private int updateLine(int currentData, int shift, int newColorVal) {
        int clearMask = ~(MASK_5BITS << shift);
        int dataCleaned = currentData & clearMask;
        return dataCleaned | (newColorVal << shift);
    }

    // --- CLIPBOARD OPERATIONS ---

    // Creates a deep copy of the current configuration map to the clipboard.
    // Ensures isolation between source and destination data.
    public void copyToClipboard() {
        this.clipboardConfig = new HashMap<>(this.currentChestConfig);
    }

    // Overwrites the current chest configuration with the clipboard data.
    // Only performs the operation if valid data exists in the clipboard.
    public void pasteFromClipboard() {
        if (this.clipboardConfig != null && !this.clipboardConfig.isEmpty()) {
            this.currentChestConfig.clear();
            this.currentChestConfig.putAll(this.clipboardConfig);
        }
    }

    public boolean hasClipboardData() {
        return this.clipboardConfig != null && !this.clipboardConfig.isEmpty();
    }

    // --- WORLD & DIRECTORY RESOLUTION ---

    // Generates a sanitized folder name based on the current world or server context.
    // Distinguishes between Singleplayer (World Name) and Multiplayer (Server Address).
    private String getWorldFolderName() {
        MinecraftClient client = MinecraftClient.getInstance();
        String name = "unknown_world";
        if (client.isInSingleplayer() && client.getServer() != null) {
            name = "sp_" + client.getServer().getSaveProperties().getLevelName();
        } else if (client.getCurrentServerEntry() != null) {
            name = "mp_" + client.getCurrentServerEntry().address;
        }
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    // Resolves the root configuration directory specific to the current world context.
    private File getWorldConfigDir() {
        File runDir = MinecraftClient.getInstance().runDirectory;
        File baseModDir = new File(runDir, "config/" + MOD_ID);
        File worldDir = new File(baseModDir, getWorldFolderName());
        if (!worldDir.exists()) worldDir.mkdirs();
        return worldDir;
    }

    // Resolves the specific subdirectory for separator data files.
    private File getSeparatorsDir() {
        File sepDir = new File(getWorldConfigDir(), FOLDER_NAME);
        if (!sepDir.exists()) sepDir.mkdirs();
        return sepDir;
    }

    // --- FILE RESOLUTION ---

    // Resolves a unique file path for a specific block position and dimension.
    // Sanitizes dimension IDs (e.g., "minecraft:overworld" -> "minecraft_overworld").
    private File getFileForPos(BlockPos pos, String dimensionId) {
        if (pos == null) return null;
        File dir = getSeparatorsDir();
        String safeDim = dimensionId.replace(":", "_");
        String fileName = String.format("%s_%d_%d_%d.dat", safeDim, pos.getX(), pos.getY(), pos.getZ());
        return new File(dir, fileName);
    }

    private File getEnderChestFile() {
        return new File(getWorldConfigDir(), ENDER_FILE_NAME);
    }

    // Resolves a unique file path based on Entity UUID (for Donkey/Mule inventories).
    private File getFileForEntity(UUID uuid) {
        if (uuid == null) return null;
        File dir = getSeparatorsDir();
        return new File(dir, "entity_" + uuid.toString() + ".dat");
    }

    // --- DELETION LOGIC ---

    // Clears runtime memory and physically deletes the persistent file.
    public void clearChest(BlockPos pos, String dimensionId) {
        currentChestConfig.clear();
        File file = getFileForPos(pos, dimensionId);
        if (file != null && file.exists()) file.delete();
    }

    public void clearEnderChest() {
        currentChestConfig.clear();
        File file = getEnderChestFile();
        if (file.exists()) file.delete();
    }

    public void clearEntityChest(UUID uuid) {
        currentChestConfig.clear();
        File file = getFileForEntity(uuid);
        if (file != null && file.exists()) file.delete();
    }

    // --- LOADING LOGIC ---

    public void loadConfig(BlockPos pos, String dimensionId) {
        clearCurrentConfig();
        loadFromFile(getFileForPos(pos, dimensionId));
    }

    public void loadEnderConfig() {
        clearCurrentConfig();
        loadFromFile(getEnderChestFile());
    }

    public void loadEntityConfig(UUID uuid) {
        clearCurrentConfig();
        loadFromFile(getFileForEntity(uuid));
    }

    // Generic NBT file loader. Reads compressed NBT data and populates the runtime map.
    // Robust error handling ignores malformed integer keys.
    private void loadFromFile(File file) {
        if (file == null || !file.exists()) return;
        try {
            NbtCompound root = NbtIo.readCompressed(file.toPath(), NbtSizeTracker.ofUnlimitedBytes());
            if (root.contains("Separators")) {
                NbtCompound listTag = null;
                if (root.get("Separators") instanceof NbtCompound comp) listTag = comp;
                else return;
                for (String key : listTag.getKeys()) {
                    try {
                        int slot = Integer.parseInt(key);
                        int data = listTag.getInt(key).orElse(0);
                        currentChestConfig.put(slot, data);
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- SAVING LOGIC ---

    public void saveConfig(BlockPos pos, String dimensionId) {
        saveToFile(getFileForPos(pos, dimensionId));
    }

    public void saveEnderConfig() {
        saveToFile(getEnderChestFile());
    }

    public void saveEntityConfig(UUID uuid) {
        saveToFile(getFileForEntity(uuid));
    }

    // Generic NBT file writer.
    // Optimization: If the runtime config is empty, deletes the file instead of saving empty data
    // to reduce disk clutter and I/O overhead.
    private void saveToFile(File file) {
        if (file == null) return;
        if (currentChestConfig.isEmpty()) {
            if (file.exists()) file.delete();
            return;
        }
        NbtCompound root = new NbtCompound();
        NbtCompound separatorsTag = new NbtCompound();
        for (Map.Entry<Integer, Integer> entry : currentChestConfig.entrySet()) {
            separatorsTag.putInt(String.valueOf(entry.getKey()), entry.getValue());
        }
        root.put("Separators", separatorsTag);
        try {
            NbtIo.writeCompressed(root, file.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}