package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.util.ChestPosStorage;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

// Mixin integrating the Chest Separators UI directly into the generic container screen.
// Leverages ASM transformation to inject rendering hooks and event handlers for the separator editor.
// Targeted specifically at HandledScreen to ensure broad compatibility with inventory-based UIs.
@Mixin(HandledScreen.class)
public abstract class GenericContainerScreenMixin extends Screen {

    protected GenericContainerScreenMixin(Text title) {
        super(title);
    }

    // --- STATE MANAGEMENT ---

    // Toggle for the editing mode overlay. Determines whether the UI intercepts
    // mouse interactions or passes them through to the vanilla container logic.
    @Unique private boolean isEditMode = false;

    // Transient UI state for displaying feedback toasts (e.g., "Copied to clipboard").
    // Uses a timestamp-based approach for rendering decay.
    @Unique private Text statusMessage = null;
    @Unique private long statusMessageTime = 0;

    // --- UI COMPONENT REFERENCES ---

    // Retaining references to dynamically toggle visibility during the render loop
    // without triggering a full screen re-initialization.
    @Unique private ButtonWidget editButton;
    @Unique private ButtonWidget eraserBtn;
    @Unique private ButtonWidget trashBtn;
    @Unique private ButtonWidget copyBtn;
    @Unique private ButtonWidget pasteBtn;

    // --- CONTEXTUAL DATA ---

    // Identifying information for the currently open container.
    // Critical for resolving the correct persistence key (BlockPos vs Entity UUID).
    @Unique private BlockPos currentChestPos;
    @Unique private String currentDimension;

    @Unique private boolean isEnderChest = false;
    @Unique private boolean isEntityChest = false;
    @Unique private UUID currentEntityUUID;

    // --- EDITOR CONSTANTS & PALETTE ---

    // Pre-defined color palette matching internal integer IDs stored in NBT data.
    // Mapped sequentially to facilitate index-based selection logic.
    @Unique
    private static final int[] PALETTE = {
            0xFF993333, 0xFFD87F33, 0xFFE5E533, 0xFF7FCC19,
            0xFF667F33, 0xFF4C7F99, 0xFF6699D8, 0xFF334CB2,
            0xFF7F3FB2, 0xFFB24CD8, 0xFFF27FA5, 0xFF664C33,
            0xFFFFFFFF, 0xFF999999, 0xFF4C4C4C, 0xFF191919
    };

    @Unique private int selectedColorIndex = 0;
    @Unique private static final int TOOL_ERASER = 16; // Sentinel value for the eraser tool state.

    // --- INTERACTION STATE ---

    // Tracks drag-and-drop operations for painting lines across multiple slots.
    @Unique private boolean isDraggingLine = false;
    @Unique private int currentDragAction = 0;
    @Unique private Slot dragStartSlot = null;
    @Unique private Slot dragCurrentSlot = null;
    @Unique private boolean isDragModeErasing = false;

    // --- INITIALIZATION INJECTION ---

    // Hooks into the end of the screen initialization phase.
    // Responsible for bootstrapping the layout configuration, detecting the container context,
    // and instantiating the declarative UI widgets.
    @Inject(method = "init", at = @At("TAIL"))
    protected void init(CallbackInfo ci) {
        // Validation check to ensure strict type safety within the mixin environment.
        if (!((Object)this instanceof net.minecraft.client.gui.screen.ingame.GenericContainerScreen)) return;

        // Hydrate context from thread-local storage or static cache.
        this.currentChestPos = ChestPosStorage.lastClickedPos;
        this.currentDimension = ChestPosStorage.lastClickedDimension;
        this.isEntityChest = ChestPosStorage.isEntityOpened;
        this.currentEntityUUID = ChestPosStorage.lastClickedEntityUUID;
        this.isEnderChest = false;

        // Select appropriate configuration loading strategy based on container type.
        if (this.isEntityChest && this.currentEntityUUID != null) {
            ChestConfigManager.getInstance().loadEntityConfig(this.currentEntityUUID);
        } else if (this.currentChestPos != null && MinecraftClient.getInstance().world != null) {
            if (MinecraftClient.getInstance().world.getBlockState(currentChestPos).getBlock() == Blocks.ENDER_CHEST) {
                this.isEnderChest = true;
                ChestConfigManager.getInstance().loadEnderConfig();
            } else {
                ChestConfigManager.getInstance().loadConfig(this.currentChestPos, this.currentDimension);
            }
        }

        // Construct UI widgets using the Accessor mixin to read protected parent fields.
        if ((Object)this instanceof HandledScreenAccessor accessor) {
            int x = accessor.getX();
            int y = accessor.getY();
            int bgWidth = accessor.getBackgroundWidth();

            Text keyName = io.github.marcsanzdev.chestseparators.event.KeyInputHandler.toggleButtonKey.getBoundKeyLocalizedText().copy().formatted(Formatting.YELLOW);
            Text tooltipText = Text.translatable("tooltip.chestseparators.edit_mode_hint", keyName);

            this.editButton = ButtonWidget.builder(Text.literal("✎"), button -> toggleEditMode())
                    .dimensions(x + bgWidth - 22, y - 22, 20, 20)
                    .tooltip(Tooltip.of(tooltipText))
                    .build();
            // Initialize as hidden to prevent layout shifts during the first render frame.
            this.editButton.visible = false;
            this.addDrawableChild(this.editButton);

            int startX = x + bgWidth + 4;
            int startY = y + 16;
            int paletteHeight = 8 * 12;
            int buttonsBlockHeight = 2 * 22;
            int toolsStartX = startX + (2 * 12) + 8;
            int toolsStartY = startY + (paletteHeight / 2) - (buttonsBlockHeight / 2);

            // Eraser Tool Widget.
            // Explicit focus clearing required to prevent vanilla UI focus ring interference.
            this.eraserBtn = ButtonWidget.builder(Text.literal(""), b -> {
                        this.selectedColorIndex = TOOL_ERASER;
                        playClickSound(1.0f);
                        this.setFocused(null);
                    }).dimensions(toolsStartX, toolsStartY, 20, 20)
                    .tooltip(Tooltip.of(Text.translatable("tooltip.chestseparators.eraser")))
                    .build();
            this.eraserBtn.visible = false;
            this.addDrawableChild(this.eraserBtn);

            // Clear All / Trash Widget.
            this.trashBtn = ButtonWidget.builder(Text.literal(""), b -> {
                        if (isEntityChest && currentEntityUUID != null) ChestConfigManager.getInstance().clearEntityChest(currentEntityUUID);
                        else if (isEnderChest) ChestConfigManager.getInstance().clearEnderChest();
                        else ChestConfigManager.getInstance().clearChest(currentChestPos, currentDimension);

                        playClickSound(0.8f);
                        this.selectedColorIndex = 0;
                        showStatus(Text.translatable("message.chestseparators.cleared"), Formatting.RED);
                        this.setFocused(null);
                    }).dimensions(toolsStartX + 22, toolsStartY, 20, 20)
                    .tooltip(Tooltip.of(Text.translatable("tooltip.chestseparators.clear_all")))
                    .build();
            this.trashBtn.visible = false;
            this.addDrawableChild(this.trashBtn);

            // Copy to Clipboard Widget.
            this.copyBtn = ButtonWidget.builder(Text.literal(""), b -> {
                        ChestConfigManager.getInstance().copyToClipboard();
                        playClickSound(1.0f);
                        showStatus(Text.translatable("message.chestseparators.copied"), Formatting.GRAY);
                        this.setFocused(null);
                    }).dimensions(toolsStartX, toolsStartY + 22, 20, 20)
                    .tooltip(Tooltip.of(Text.translatable("tooltip.chestseparators.copy")))
                    .build();
            this.copyBtn.visible = false;
            this.addDrawableChild(this.copyBtn);

            // Paste from Clipboard Widget.
            this.pasteBtn = ButtonWidget.builder(Text.literal(""), b -> {
                        if (ChestConfigManager.getInstance().hasClipboardData()) {
                            ChestConfigManager.getInstance().pasteFromClipboard();
                            saveSmart();
                            playClickSound(1.0f);
                            showStatus(Text.translatable("message.chestseparators.pasted"), Formatting.GREEN);
                        }
                        this.setFocused(null);
                    }).dimensions(toolsStartX + 22, toolsStartY + 22, 20, 20)
                    .tooltip(Tooltip.of(Text.translatable("tooltip.chestseparators.paste")))
                    .build();
            this.pasteBtn.visible = false;
            this.addDrawableChild(this.pasteBtn);
        }

        // --- INPUT INTERCEPTION LAYER ---

        // Prioritizes mod logic over vanilla interaction when Edit Mode is active.
        ScreenMouseEvents.allowMouseClick((Screen)(Object)this).register((screen, context) -> {
            if (!GlobalChestConfig.isShowEditButton()) return true;

            if (!isEditMode || context.button() != 0) return true;
            HandledScreenAccessor accessor = (HandledScreenAccessor) (Object) GenericContainerScreenMixin.this;
            if (handlePaletteClick(accessor, context.x(), context.y())) return false;

            Slot slot = accessor.getFocusedSlot();
            if (slot != null) {
                // Restrict painting logic to container slots only (excluding player inventory).
                if (!(slot.inventory instanceof PlayerInventory)) {
                    int action = calculateAction(slot, accessor, context.x(), context.y());
                    if (action != 0) {
                        this.isDraggingLine = true;
                        this.currentDragAction = action;
                        this.dragStartSlot = slot;
                        this.dragCurrentSlot = slot;

                        ChestConfigManager manager = ChestConfigManager.getInstance();
                        if (selectedColorIndex == TOOL_ERASER) {
                            this.isDragModeErasing = true;
                        } else {
                            // Smart erasing logic: toggle to erase if painting over an existing line of the same color.
                            boolean lineExists = isLinePresent(manager, slot.getIndex(), action);
                            int existingColor = getLineColor(manager, slot.getIndex(), action);
                            if (lineExists && existingColor == selectedColorIndex) isDragModeErasing = true;
                            else isDragModeErasing = false;
                        }
                    }
                }

                // Consume the event to sandbox inventory slots, preventing vanilla item interaction
                // while the user is editing the layout.
                return false;
            }
            return true;
        });

        // Handler for committing changes upon mouse release (end of drag operation).
        ScreenMouseEvents.allowMouseRelease((Screen)(Object)this).register((screen, context) -> {
            if (context.button() == 0 && this.isDraggingLine) {
                commitDrag();
                this.isDraggingLine = false;
                this.currentDragAction = 0;
                this.dragStartSlot = null;
                this.dragCurrentSlot = null;
                this.isDragModeErasing = false;
            }
            return true;
        });

        // Handler for updating the "current target" during a drag operation.
        ScreenMouseEvents.allowMouseDrag((Screen)(Object)this).register((screen, context, dx, dy) -> {
            if (!isEditMode || !isDraggingLine) return true;
            HandledScreenAccessor accessor = (HandledScreenAccessor) (Object) GenericContainerScreenMixin.this;
            Slot slot = accessor.getFocusedSlot();
            if (slot != null && !(slot.inventory instanceof PlayerInventory)) {
                this.dragCurrentSlot = slot;
            }
            return false;
        });
    }

    // --- RENDER LAYER 1: SAVED SEPARATORS (Background Layer) ---

    // Injects logic at the HEAD of 'drawSlots'.
    // This allows us to render our lines exactly once per frame, after the background is drawn
    // but before individual slots are rendered. Ideally positioned for Z-layer ordering.
    @Inject(method = "drawSlots", at = @At("HEAD"))
    public void renderSavedLinesLayer(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        if (!((Object)this instanceof net.minecraft.client.gui.screen.ingame.GenericContainerScreen)) return;
        renderSavedLinesLayerInternal(context);
    }

    @Unique
    private void renderSavedLinesLayerInternal(DrawContext context) {
        HandledScreenAccessor accessor = (HandledScreenAccessor) (Object) this;
        ChestConfigManager manager = ChestConfigManager.getInstance();

        // Technical Note:
        // We are inside 'drawSlots', where Minecraft has ALREADY applied a matrix translation
        // to the GUI position (guiX, guiY).
        // Therefore, we must use RELATIVE coordinates (slot.x, slot.y) and NOT absolute ones.
        // Adding guiX/guiY here would cause the lines to be displaced by double the distance.

        for (Slot s : accessor.getHandler().slots) {
            if (s.inventory instanceof PlayerInventory) continue;
            int data = manager.getSlotData(s.getIndex());
            if (data == 0) continue;

            // Use only s.x and s.y (coordinates relative to the container).
            int x = s.x;
            int y = s.y;

            // Render saved lines (stateless, no preview effects).
            renderSavedLine(context, x, y, manager, data, 0, ChestConfigManager.ACTION_TOP, Collections.emptyList(), 0, s, false);
            renderSavedLine(context, x, y, manager, data, 5, ChestConfigManager.ACTION_BOTTOM, Collections.emptyList(), 0, s, false);
            renderSavedLine(context, x, y, manager, data, 10, ChestConfigManager.ACTION_LEFT, Collections.emptyList(), 0, s, false);
            renderSavedLine(context, x, y, manager, data, 15, ChestConfigManager.ACTION_RIGHT, Collections.emptyList(), 0, s, false);
        }
    }

    // --- RENDER LAYER 2: EDITOR OVERLAY (Foreground Layer) ---

    // Injects at the tail of the render cycle to ensure high-priority UI elements
    // (controls, tooltips, drag previews) are drawn on the highest Z-index,
    // guaranteeing accessibility and preventing occlusion by item stacks.
    @Inject(method = "render", at = @At("TAIL"))
    public void renderEditorOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!((Object)this instanceof net.minecraft.client.gui.screen.ingame.GenericContainerScreen)) return;

        // Aggressive focus clearing ensures the toggle button does not unintentionally capture
        // input focus, maintaining a stateless interaction model.
        if (this.getFocused() == this.editButton) this.setFocused(null);

        HandledScreenAccessor accessor = (HandledScreenAccessor) (Object) this;
        int guiX = accessor.getX();
        int guiY = accessor.getY();

        boolean showButton = GlobalChestConfig.isShowEditButton();
        if (this.editButton != null) this.editButton.visible = showButton;

        // Fail-safe: Automatically disengage Edit Mode if the trigger button is externally hidden
        // to prevent unreachable UI states.
        if (!showButton && this.isEditMode) toggleEditMode();

        boolean toolsVisible = showButton && this.isEditMode;
        if (this.eraserBtn != null) this.eraserBtn.visible = toolsVisible;
        if (this.trashBtn != null) this.trashBtn.visible = toolsVisible;
        if (this.copyBtn != null) this.copyBtn.visible = toolsVisible;
        if (this.pasteBtn != null) {
            this.pasteBtn.visible = toolsVisible;
            this.pasteBtn.active = ChestConfigManager.getInstance().hasClipboardData();
        }

        if (this.isEditMode) {
            // Apply a modal dimming effect to focus user attention on the editor workspace.
            context.fill(0, 0, this.width, this.height, 0xAA000000);

            // --- Z-INDEX CORRECTION FOR OVERLAY ---
            // Because the dimming layer (0xAA000000) is drawn above everything rendered previously
            // (including the saved lines from Layer 1), the lines appear dark.
            // We must re-render the saved lines HERE, strictly for visual clarity during editing.
            // Note: Coordinate system here is ABSOLUTE (Matrix has been popped), so we use guiX + slot.x.
            ChestConfigManager manager = ChestConfigManager.getInstance();
            for (Slot s : accessor.getHandler().slots) {
                if (s.inventory instanceof PlayerInventory) continue;
                int data = manager.getSlotData(s.getIndex());
                if (data == 0) continue;

                int x = guiX + s.x;
                int y = guiY + s.y;

                renderSavedLine(context, x, y, manager, data, 0, ChestConfigManager.ACTION_TOP, Collections.emptyList(), 0, s, false);
                renderSavedLine(context, x, y, manager, data, 5, ChestConfigManager.ACTION_BOTTOM, Collections.emptyList(), 0, s, false);
                renderSavedLine(context, x, y, manager, data, 10, ChestConfigManager.ACTION_LEFT, Collections.emptyList(), 0, s, false);
                renderSavedLine(context, x, y, manager, data, 15, ChestConfigManager.ACTION_RIGHT, Collections.emptyList(), 0, s, false);
            }
            // -------------------------------------

            if (this.editButton != null) this.editButton.render(context, mouseX, mouseY, delta);

            // Render Eraser Tool with manual focus handling.
            if (this.eraserBtn != null) {
                this.eraserBtn.setFocused(false);
                this.eraserBtn.render(context, mouseX, mouseY, delta);
                if (this.selectedColorIndex == TOOL_ERASER) {
                    drawButtonSelectionBorder(context, this.eraserBtn.getX(), this.eraserBtn.getY());
                }
            }

            // Render Action Buttons (Stateless).
            if (this.trashBtn != null) {
                this.trashBtn.setFocused(false);
                this.trashBtn.render(context, mouseX, mouseY, delta);
            }
            if (this.copyBtn != null) {
                this.copyBtn.setFocused(false);
                this.copyBtn.render(context, mouseX, mouseY, delta);
            }
            if (this.pasteBtn != null) {
                this.pasteBtn.setFocused(false);
                this.pasteBtn.render(context, mouseX, mouseY, delta);
            }

            int bgWidth = accessor.getBackgroundWidth();
            int startX = guiX + bgWidth + 4;
            int startY = guiY + 16;

            // Render Color Palette Swatches.
            for (int i = 0; i < PALETTE.length; i++) {
                int col = i % 2; int row = i / 2;
                int x = startX + (col * 12); int y = startY + (row * 12);
                context.fill(x, y, x + 10, y + 10, PALETTE[i]);
                if (i == this.selectedColorIndex) drawSelectionBorder(context, x, y);
            }

            // Overlay custom pixel-art icons.
            if (this.eraserBtn != null) drawEraserIcon(context, this.eraserBtn.getX(), this.eraserBtn.getY());
            if (this.trashBtn != null) drawTrashIcon(context, this.trashBtn.getX(), this.trashBtn.getY());
            if (this.copyBtn != null) drawCopyIcon(context, this.copyBtn.getX(), this.copyBtn.getY());
            if (this.pasteBtn != null) drawPasteIcon(context, this.pasteBtn.getX(), this.pasteBtn.getY(), this.pasteBtn.active);

            // Render Active Drag Preview Lines.
            List<Slot> activeSlots = new ArrayList<>();
            int activeAction = 0;
            boolean eraserActive = (this.selectedColorIndex == TOOL_ERASER);

            if (this.isDraggingLine && dragStartSlot != null && dragCurrentSlot != null) {
                activeSlots = calculateAffectedSlots();
                activeAction = currentDragAction;
                if (isDragModeErasing) eraserActive = true;
            } else {
                Slot hoveredSlot = accessor.getFocusedSlot();
                if (hoveredSlot != null && !(hoveredSlot.inventory instanceof PlayerInventory)) {
                    int action = calculateAction(hoveredSlot, accessor, mouseX, mouseY);
                    if (action != 0) {
                        activeSlots = Collections.singletonList(hoveredSlot);
                        activeAction = action;
                    }
                }
            }

            if (!eraserActive && !activeSlots.isEmpty() && activeAction != 0) {
                int color = PALETTE[this.selectedColorIndex];
                // Apply alpha channel modification for transparency (0x80) to distinguish
                // preview state from committed state.
                int previewColor = (color & 0x00FFFFFF) | 0x80000000;
                for (Slot slot : activeSlots) {
                    drawPreviewLine(context, guiX, guiY, slot, activeAction, previewColor);
                }
            }

            // Render Toast Notification System.
            if (this.statusMessage != null) {
                long elapsed = System.currentTimeMillis() - this.statusMessageTime;
                if (elapsed < 2000) {
                    int alpha = 255;
                    // Implement linear fade-out interpolation during the last 500ms.
                    if (elapsed > 1500) {
                        alpha = (int) (255 * (1.0f - (elapsed - 1500) / 500.0f));
                    }
                    int color = (alpha << 24) | 0xFFFFFF;
                    context.drawCenteredTextWithShadow(this.textRenderer, this.statusMessage, this.width / 2, this.height - 68, color);
                } else {
                    this.statusMessage = null;
                }
            }
        }
    }

    // --- UTILITY METHODS ---

    // Updates the transient status message and resets the decay timer.
    @Unique
    private void showStatus(Text message, Formatting color) {
        this.statusMessage = message.copy().formatted(color);
        this.statusMessageTime = System.currentTimeMillis();
    }

    // Facade for persistence logic, routing the save operation to the correct backend.
    @Unique
    private void saveSmart() {
        if (isEntityChest && currentEntityUUID != null) {
            ChestConfigManager.getInstance().saveEntityConfig(currentEntityUUID);
        } else if (isEnderChest) {
            ChestConfigManager.getInstance().saveEnderConfig();
        } else {
            ChestConfigManager.getInstance().saveConfig(currentChestPos, currentDimension);
        }
    }

    // Hit detection for the custom color palette component.
    @Unique
    private boolean handlePaletteClick(HandledScreenAccessor accessor, double mouseX, double mouseY) {
        int guiX = accessor.getX(); int guiY = accessor.getY();
        int bgWidth = accessor.getBackgroundWidth();
        int startX = guiX + bgWidth + 4; int startY = guiY + 16;

        for (int i = 0; i < PALETTE.length; i++) {
            int col = i % 2; int row = i / 2;
            int colorX = startX + (col * 12); int colorY = startY + (row * 12);
            if (mouseX >= colorX && mouseX < colorX + 10 && mouseY >= colorY && mouseY < colorY + 10) {
                this.selectedColorIndex = i;
                // Clearing focus ensures visual consistency by removing focus rings from tools.
                this.setFocused(null);
                playClickSound(1.2f);
                return true;
            }
        }
        return false;
    }

    // --- DRAWING PRIMITIVES ---

    @Unique
    private void renderSavedLine(DrawContext context, int x, int y, ChestConfigManager manager, int data, int shift, int actionFlag, List<Slot> activeSlots, int activeAction, Slot currentSlot, boolean eraserActive) {
        int colorIndex = manager.getLineColor(data, shift);
        if (colorIndex == -1) return;
        if (colorIndex >= 0 && colorIndex < PALETTE.length) {
            int color = PALETTE[colorIndex];
            // Apply visual dimming if the line is currently targeted by the eraser tool.
            if (eraserActive && activeSlots.contains(currentSlot) && (activeAction & actionFlag) != 0) {
                color = (color & 0x00FFFFFF) | 0x55000000;
            }
            if (actionFlag == ChestConfigManager.ACTION_TOP) context.fill(x - 1, y - 1, x + 17, y, color);
            else if (actionFlag == ChestConfigManager.ACTION_BOTTOM) context.fill(x - 1, y + 16, x + 17, y + 17, color);
            else if (actionFlag == ChestConfigManager.ACTION_LEFT) context.fill(x - 1, y - 1, x, y + 17, color);
            else if (actionFlag == ChestConfigManager.ACTION_RIGHT) context.fill(x + 16, y - 1, x + 17, y + 17, color);
        }
    }

    @Unique
    private void drawEraserIcon(DrawContext context, int x, int y) {
        int cx = x + 6; int cy = y + 5;
        int width = 7;
        int height = 10;
        int split = 7;

        context.fill(cx + 1, cy, cx + width, cy + split, 0xFFE88787);
        context.fill(cx + 1, cy + split, cx + width, cy + height, 0xFFFFFFFF);
    }

    @Unique
    private void drawTrashIcon(DrawContext context, int x, int y) {
        int cx = x + 5; int cy = y + 4;
        context.fill(cx + 1, cy + 2, cx + 9, cy + 3, 0xFFFFFFFF);
        context.fill(cx + 4, cy + 1, cx + 6, cy + 2, 0xFFFFFFFF);
        context.fill(cx + 2, cy + 3, cx + 8, cy + 10, 0xFFEEEEEE);
        context.fill(cx + 3, cy + 4, cx + 4, cy + 9, 0xFF555555);
        context.fill(cx + 6, cy + 4, cx + 7, cy + 9, 0xFF555555);
    }

    @Unique
    private void drawCopyIcon(DrawContext context, int x, int y) {
        int cx = x + 6; int cy = y + 4;
        context.fill(cx + 3, cy + 1, cx + 8, cy + 7, 0xFFAAAAAA);
        context.fill(cx + 1, cy + 4, cx + 6, cy + 10, 0xFFFFFFFF);
    }

    @Unique
    private void drawPasteIcon(DrawContext context, int x, int y, boolean active) {
        int cx = x + 5; int cy = y + 3;
        int paperColor = active ? 0xFFFFFFFF : 0xFF888888;
        int boardColor = active ? 0xFF8B4513 : 0xFF554433;
        context.fill(cx + 1, cy + 2, cx + 9, cy + 12, boardColor);
        context.fill(cx + 2, cy + 3, cx + 8, cy + 11, paperColor);
        context.fill(cx + 3, cy + 1, cx + 7, cy + 3, 0xFFCCCCCC);
    }

    // Persists the changes made during a drag operation to the ConfigManager.
    @Unique private void commitDrag() {
        if (dragStartSlot == null || dragCurrentSlot == null) return;
        List<Slot> affectedSlots = calculateAffectedSlots();
        ChestConfigManager manager = ChestConfigManager.getInstance();
        boolean changeMade = false;
        for (Slot slot : affectedSlots) {
            if (isDragModeErasing) manager.removeLine(slot.getIndex(), currentDragAction);
            else manager.paintLine(slot.getIndex(), currentDragAction, selectedColorIndex);
            changeMade = true;
        }
        if (changeMade) {
            saveSmart();
            playClickSound(1.0f);
        }
    }

    // Calculates the range of slots affected by a drag operation (horizontal or vertical).
    @Unique private List<Slot> calculateAffectedSlots() {
        if (dragStartSlot == null || dragCurrentSlot == null) return new ArrayList<>();
        List<Slot> slots = new ArrayList<>();
        HandledScreenAccessor accessor = (HandledScreenAccessor) (Object) this;
        boolean isHorizontal = (currentDragAction & (ChestConfigManager.ACTION_TOP | ChestConfigManager.ACTION_BOTTOM)) != 0;
        int startX = dragStartSlot.x; int startY = dragStartSlot.y;
        int targetX = dragCurrentSlot.x; int targetY = dragCurrentSlot.y;
        for (Slot slot : accessor.getHandler().slots) {
            if (slot.inventory instanceof PlayerInventory) continue;
            boolean inRange = false;
            if (isHorizontal) {
                if (slot.y == startY) {
                    int minX = Math.min(startX, targetX); int maxX = Math.max(startX, targetX);
                    if (slot.x >= minX && slot.x <= maxX) inRange = true;
                }
            } else {
                if (slot.x == startX) {
                    int minY = Math.min(startY, targetY); int maxY = Math.max(startY, targetY);
                    if (slot.y >= minY && slot.y <= maxY) inRange = true;
                }
            }
            if (inRange) slots.add(slot);
        }
        return slots;
    }

    @Unique private boolean isLinePresent(ChestConfigManager manager, int slotIndex, int action) {
        int data = manager.getSlotData(slotIndex);
        int shift = -1;
        if (action == ChestConfigManager.ACTION_TOP) shift = 0;
        else if (action == ChestConfigManager.ACTION_BOTTOM) shift = 5;
        else if (action == ChestConfigManager.ACTION_LEFT) shift = 10;
        else if (action == ChestConfigManager.ACTION_RIGHT) shift = 15;
        return manager.getLineColor(data, shift) != -1;
    }

    @Unique private int getLineColor(ChestConfigManager manager, int slotIndex, int action) {
        int data = manager.getSlotData(slotIndex);
        int shift = -1;
        if (action == ChestConfigManager.ACTION_TOP) shift = 0;
        else if (action == ChestConfigManager.ACTION_BOTTOM) shift = 5;
        else if (action == ChestConfigManager.ACTION_LEFT) shift = 10;
        else if (action == ChestConfigManager.ACTION_RIGHT) shift = 15;
        return manager.getLineColor(data, shift);
    }

    // Determines which border of a slot (Top, Bottom, Left, Right) is being targeted based on mouse position.
    // Uses a proximity threshold to detect intent.
    @Unique private int calculateAction(Slot slot, HandledScreenAccessor accessor, double mouseX, double mouseY) {
        int guiX = accessor.getX(); int guiY = accessor.getY();
        double relativeX = mouseX - (guiX + slot.x); double relativeY = mouseY - (guiY + slot.y);
        double distTop = Math.abs(relativeY); double distBottom = Math.abs(relativeY - 16);
        double distLeft = Math.abs(relativeX); double distRight = Math.abs(relativeX - 16);
        double minDist = Math.min(Math.min(distTop, distBottom), Math.min(distLeft, distRight));
        double clickThreshold = 5.0;
        if (minDist <= clickThreshold) {
            if (minDist == distTop) return ChestConfigManager.ACTION_TOP;
            else if (minDist == distBottom) return ChestConfigManager.ACTION_BOTTOM;
            else if (minDist == distLeft) return ChestConfigManager.ACTION_LEFT;
            else if (minDist == distRight) return ChestConfigManager.ACTION_RIGHT;
        }
        return 0;
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    public void onKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        int keyCode = input.key();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.isEditMode) {
            toggleEditMode();
            cir.setReturnValue(true);
        }
    }

    @Unique
    private void playClickSound(float pitch) {
        MinecraftClient.getInstance().getSoundManager().play(
                net.minecraft.client.sound.PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, pitch)
        );
    }

    @Unique private void drawPreviewLine(DrawContext context, int guiX, int guiY, Slot slot, int action, int color) {
        int x = guiX + slot.x; int y = guiY + slot.y;
        if ((action & ChestConfigManager.ACTION_TOP) != 0) context.fill(x - 1, y - 1, x + 17, y, color);
        if ((action & ChestConfigManager.ACTION_BOTTOM) != 0) context.fill(x - 1, y + 16, x + 17, y + 17, color);
        if ((action & ChestConfigManager.ACTION_LEFT) != 0) context.fill(x - 1, y - 1, x, y + 17, color);
        if ((action & ChestConfigManager.ACTION_RIGHT) != 0) context.fill(x + 16, y - 1, x + 17, y + 17, color);
    }

    @Unique private void drawSelectionBorder(DrawContext context, int x, int y) {
        int bx = x - 1, by = y - 1, bSize = 12, c = 0xFFFFFFFF;
        context.fill(bx, by, bx + bSize, by + 1, c);
        context.fill(bx, by + bSize - 1, bx + bSize, by + bSize, c);
        context.fill(bx, by + 1, bx + 1, by + bSize - 1, c);
        context.fill(bx + bSize - 1, by + 1, bx + bSize, by + bSize - 1, c);
    }

    // Custom implementation for drawing the tool selection ring.
    // Draws strictly within the button boundaries (20x20) to maintain a clean UI.
    @Unique private void drawButtonSelectionBorder(DrawContext context, int x, int y) {
        int width = 20;
        int height = 20;
        int color = 0xFFFFFFFF;
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    @Unique private void toggleEditMode() {
        this.isEditMode = !this.isEditMode;
        if (this.editButton != null) {
            this.editButton.setMessage(Text.literal(this.isEditMode ? "✖" : "✎"));
            this.setFocused(null);
        }
    }

    // Cleanup hook ensuring state resets when the container is closed.
    @Inject(method = "close", at = @At("HEAD"))
    public void onClose(CallbackInfo ci) {
        ChestConfigManager.getInstance().clearCurrentConfig();
    }
}