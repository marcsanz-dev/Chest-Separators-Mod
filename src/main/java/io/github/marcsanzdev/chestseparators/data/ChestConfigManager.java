package io.github.marcsanzdev.chestseparators.data;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class ChestConfigManager {

    private static final String MOD_ID = "chestseparators";
    private static final String FOLDER_NAME = "separators";
    private static final String ENDER_FILE_NAME = "ender_chest.dat";
    private static final String PALETTE_FILE_NAME = "world_palette.dat";

    public static final int ACTION_TOP = 1;
    public static final int ACTION_BOTTOM = 2;
    public static final int ACTION_LEFT = 4;
    public static final int ACTION_RIGHT = 8;

    private static final int IDX_TOP = 0;
    private static final int IDX_BOTTOM = 1;
    private static final int IDX_LEFT = 2;
    private static final int IDX_RIGHT = 3;

    private final Map<Integer, int[]> currentChestConfig = new HashMap<>();
    private Map<Integer, int[]> clipboardConfig = null;
    private int[] worldCustomColors = new int[8];

    private static final ChestConfigManager INSTANCE = new ChestConfigManager();

    public static ChestConfigManager getInstance() {
        return INSTANCE;
    }

    public void clearCurrentConfig() {
        currentChestConfig.clear();
    }

    public int[] getCustomColors() {
        return worldCustomColors;
    }

    public void setCustomColor(int index, int color) {
        if (index >= 0 && index < worldCustomColors.length) {
            worldCustomColors[index] = color;
        }
    }

    // --- CARGA/GUARDA PALETA (NIO) ---

    public void loadWorldPalette() {
        worldCustomColors = new int[8];
        Path path = getWorldConfigDir().resolve(PALETTE_FILE_NAME);

        if (!Files.exists(path)) return;

        try {
            NbtCompound root = NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes());
            if (root.contains("Palette")) {
                root.getIntArray("Palette").ifPresent(loadedColors -> {
                    if (loadedColors.length == 8) {
                        worldCustomColors = loadedColors;
                    } else {
                        System.arraycopy(loadedColors, 0, worldCustomColors, 0, Math.min(loadedColors.length, 8));
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveWorldPalette() {
        Path path = getWorldConfigDir().resolve(PALETTE_FILE_NAME);
        NbtCompound root = new NbtCompound();
        root.putIntArray("Palette", worldCustomColors);
        try {
            NbtIo.writeCompressed(root, path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- MIGRACIÓN Y LIMPIEZA (NIO) ---

    public boolean hasConfig(BlockPos pos, String dimensionId) {
        Path path = getFileForPos(pos, dimensionId);
        return path != null && Files.exists(path);
    }

    public void moveConfig(BlockPos from, BlockPos to, String dimensionId) {
        Path pathFrom = getFileForPos(from, dimensionId);
        Path pathTo = getFileForPos(to, dimensionId);

        if (pathFrom != null && Files.exists(pathFrom) && pathTo != null) {
            try {
                // Files.move es más robusto y permite reemplazar si existe (aunque aquí validamos antes)
                Files.move(pathFrom, pathTo, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                System.err.println("[ChestSeparators] Error moviendo config: " + e.getMessage());
            }
        }
    }

    public void truncateChestConfig(BlockPos pos, String dimensionId, int maxSlotIndex) {
        Path path = getFileForPos(pos, dimensionId);
        if (path == null || !Files.exists(path)) return;

        try {
            NbtCompound root = NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes());
            boolean[] changed = {false};

            root.getCompound("Separators").ifPresent(separatorsTag -> {
                Set<String> keysToRemove = new HashSet<>();

                for (String key : separatorsTag.getKeys()) {
                    try {
                        int slot = Integer.parseInt(key);
                        if (slot > maxSlotIndex) {
                            keysToRemove.add(key);
                        }
                    } catch (NumberFormatException ignored) {}
                }

                if (!keysToRemove.isEmpty()) {
                    for (String key : keysToRemove) {
                        separatorsTag.remove(key);
                    }
                    changed[0] = true;
                }
            });

            if (changed[0]) {
                NbtIo.writeCompressed(root, path);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- GESTIÓN DE RUTAS (NIO) ---

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

    private Path getWorldConfigDir() {
        // Obtenemos el directorio de ejecución como Path
        Path runDir = MinecraftClient.getInstance().runDirectory.toPath();
        Path baseModDir = runDir.resolve("config/" + MOD_ID);
        Path worldDir = baseModDir.resolve(getWorldFolderName());

        try {
            if (!Files.exists(worldDir)) {
                Files.createDirectories(worldDir);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return worldDir;
    }

    private Path getSeparatorsDir() {
        Path sepDir = getWorldConfigDir().resolve(FOLDER_NAME);
        try {
            if (!Files.exists(sepDir)) {
                Files.createDirectories(sepDir);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sepDir;
    }

    private Path getFileForPos(BlockPos pos, String dimensionId) {
        if (pos == null) return null;
        Path dir = getSeparatorsDir();
        String safeDim = dimensionId.replace(":", "_");
        String fileName = String.format("%s_%d_%d_%d.dat", safeDim, pos.getX(), pos.getY(), pos.getZ());
        return dir.resolve(fileName);
    }

    private Path getEnderChestFile() {
        return getWorldConfigDir().resolve(ENDER_FILE_NAME);
    }

    private Path getFileForEntity(UUID uuid) {
        if (uuid == null) return null;
        Path dir = getSeparatorsDir();
        return dir.resolve("entity_" + uuid.toString() + ".dat");
    }

    // --- LÓGICA DE COFRES ---

    private int[] getSlotColors(int slotIndex) {
        return currentChestConfig.computeIfAbsent(slotIndex, k -> new int[4]);
    }

    public int getLineColor(int slotIndex, int actionFlag) {
        if (!currentChestConfig.containsKey(slotIndex)) return 0;
        int[] colors = currentChestConfig.get(slotIndex);
        if (actionFlag == ACTION_TOP) return colors[IDX_TOP];
        if (actionFlag == ACTION_BOTTOM) return colors[IDX_BOTTOM];
        if (actionFlag == ACTION_LEFT) return colors[IDX_LEFT];
        if (actionFlag == ACTION_RIGHT) return colors[IDX_RIGHT];
        return 0;
    }

    public void paintLine(int slotIndex, int actionFlags, int argbColor) {
        int[] colors = getSlotColors(slotIndex);
        if ((actionFlags & ACTION_TOP) != 0) colors[IDX_TOP] = argbColor;
        if ((actionFlags & ACTION_BOTTOM) != 0) colors[IDX_BOTTOM] = argbColor;
        if ((actionFlags & ACTION_LEFT) != 0) colors[IDX_LEFT] = argbColor;
        if ((actionFlags & ACTION_RIGHT) != 0) colors[IDX_RIGHT] = argbColor;
    }

    public void removeLine(int slotIndex, int actionFlags) {
        if (!currentChestConfig.containsKey(slotIndex)) return;
        int[] colors = currentChestConfig.get(slotIndex);
        if ((actionFlags & ACTION_TOP) != 0) colors[IDX_TOP] = 0;
        if ((actionFlags & ACTION_BOTTOM) != 0) colors[IDX_BOTTOM] = 0;
        if ((actionFlags & ACTION_LEFT) != 0) colors[IDX_LEFT] = 0;
        if ((actionFlags & ACTION_RIGHT) != 0) colors[IDX_RIGHT] = 0;
        if (colors[0] == 0 && colors[1] == 0 && colors[2] == 0 && colors[3] == 0) {
            currentChestConfig.remove(slotIndex);
        }
    }

    public void copyToClipboard() {
        this.clipboardConfig = new HashMap<>();
        for (Map.Entry<Integer, int[]> entry : this.currentChestConfig.entrySet()) {
            this.clipboardConfig.put(entry.getKey(), entry.getValue().clone());
        }
    }

    public void pasteFromClipboard() {
        if (this.clipboardConfig != null && !this.clipboardConfig.isEmpty()) {
            this.currentChestConfig.clear();
            for (Map.Entry<Integer, int[]> entry : this.clipboardConfig.entrySet()) {
                this.currentChestConfig.put(entry.getKey(), entry.getValue().clone());
            }
        }
    }

    public boolean hasClipboardData() {
        return this.clipboardConfig != null && !this.clipboardConfig.isEmpty();
    }

    // --- OPERACIONES PÚBLICAS IO ---

    public void clearChest(BlockPos pos, String dimensionId) {
        currentChestConfig.clear();
        Path path = getFileForPos(pos, dimensionId);
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void clearEnderChest() {
        currentChestConfig.clear();
        try {
            Files.deleteIfExists(getEnderChestFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void clearEntityChest(UUID uuid) {
        currentChestConfig.clear();
        Path path = getFileForEntity(uuid);
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

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

    public void saveConfig(BlockPos pos, String dimensionId) {
        saveToFile(getFileForPos(pos, dimensionId));
    }

    public void saveEnderConfig() {
        saveToFile(getEnderChestFile());
    }

    public void saveEntityConfig(UUID uuid) {
        saveToFile(getFileForEntity(uuid));
    }

    private void loadFromFile(Path path) {
        if (path == null || !Files.exists(path)) return;
        try {
            NbtCompound root = NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes());
            if (root.contains("Separators")) {
                root.getCompound("Separators").ifPresent(separatorsTag -> {
                    for (String key : separatorsTag.getKeys()) {
                        try {
                            int slot = Integer.parseInt(key);
                            separatorsTag.getIntArray(key).ifPresent(data -> {
                                if (data.length == 4) {
                                    currentChestConfig.put(slot, data);
                                }
                            });
                        } catch (NumberFormatException ignored) {}
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveToFile(Path path) {
        if (path == null) return;
        if (currentChestConfig.isEmpty()) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
        NbtCompound root = new NbtCompound();
        NbtCompound separatorsTag = new NbtCompound();

        for (Map.Entry<Integer, int[]> entry : currentChestConfig.entrySet()) {
            separatorsTag.putIntArray(String.valueOf(entry.getKey()), entry.getValue());
        }

        root.put("Separators", separatorsTag);
        try {
            NbtIo.writeCompressed(root, path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}