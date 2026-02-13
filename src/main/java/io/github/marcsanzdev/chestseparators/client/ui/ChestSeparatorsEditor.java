package io.github.marcsanzdev.chestseparators.client.ui;

import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.mixin.client.HandledScreenAccessor;
import io.github.marcsanzdev.chestseparators.util.ChestPosStorage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class ChestSeparatorsEditor {

    private final HandledScreen<?> screen;
    private final HandledScreenAccessor accessor;

    // --- ESTADO UI ---
    private boolean isEditMode = false;
    private ButtonWidget entryButton;

    // --- EDICIÓN ---
    private int editingCustomIndex = -1;
    private int clickedActionId = -1;
    private long clickedActionTime = 0;

    // --- PICKER ---
    private boolean isColorPickerOpen = false;
    private float pickerHue = 0.0f;
    private float pickerSat = 1.0f;
    private float pickerVal = 1.0f;
    private int pickerCurrentRGB = 0xFFFF0000;

    // --- LAYOUT ---
    private static final int SIDEBAR_WIDTH = 100;
    private static final int SWATCH_SIZE = 12;
    private static final int TOOL_BTN_SIZE = 20;
    private static final int SIDEBAR_Y_OFFSET = -18;

    // --- PALETA ESTÁNDAR ---
    private static final int[] STANDARD_PALETTE = {
            0xFF993333, 0xFFD87F33, 0xFFE5E533, 0xFF7FCC19,
            0xFF667F33, 0xFF4C7F99, 0xFF6699D8, 0xFF334CB2,
            0xFF7F3FB2, 0xFFB24CD8, 0xFFF27FA5, 0xFF664C33,
            0xFFFFFFFF, 0xFF999999, 0xFF4C4C4C, 0xFF191919
    };

    private static final String[] STANDARD_COLOR_KEYS = {
            "red", "orange", "yellow", "lime",
            "green", "cyan", "light_blue", "blue",
            "purple", "magenta", "pink", "brown",
            "white", "light_gray", "gray", "black"
    };

    private int selectedColorIndex = 0;
    private static final int TOOL_ERASER_ID = -1;

    // --- ARRASTRAR ---
    private boolean isDraggingLine = false;
    private int currentDragAction = 0;
    private Slot dragStartSlot = null;
    private Slot dragCurrentSlot = null;
    private boolean isDragModeErasing = false;

    // --- CONTEXT ---
    private BlockPos currentChestPos;
    private String currentDimension;
    private boolean isEnderChest = false;
    private boolean isEntityChest = false;
    private UUID currentEntityUUID;
    private Text statusMessage = null;
    private long statusMessageTime = 0;

    public ChestSeparatorsEditor(HandledScreen<?> screen) {
        this.screen = screen;
        this.accessor = (HandledScreenAccessor) screen;
    }

    public void init() {
        this.currentChestPos = ChestPosStorage.lastClickedPos;
        this.currentDimension = ChestPosStorage.lastClickedDimension;
        this.isEntityChest = ChestPosStorage.isEntityOpened;
        this.currentEntityUUID = ChestPosStorage.lastClickedEntityUUID;
        this.isEnderChest = false;

        ChestConfigManager.getInstance().loadWorldPalette();

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

        int x = accessor.getX();
        int y = accessor.getY();
        int bgWidth = accessor.getBackgroundWidth();

        Text keyName = io.github.marcsanzdev.chestseparators.event.KeyInputHandler.toggleButtonKey.getBoundKeyLocalizedText().copy().formatted(Formatting.YELLOW);

        this.entryButton = ButtonWidget.builder(Text.literal("✎"), button -> toggleEditMode())
                .dimensions(x + bgWidth - 22, y - 22, 20, 20)
                .tooltip(Tooltip.of(Text.translatable("tooltip.chestseparators.edit_mode_hint", keyName)))
                .build();
        this.entryButton.visible = GlobalChestConfig.isShowEditButton();

        if (screen instanceof Screen s) {
            ((io.github.marcsanzdev.chestseparators.mixin.client.ScreenAccessor) s).invokeAddDrawableChild(this.entryButton);
        }

        setupInputHandlers();
    }

    public boolean isEditMode() {
        return isEditMode;
    }

    public void onClose() {
        ChestConfigManager.getInstance().clearCurrentConfig();
        isColorPickerOpen = false;
    }

    private void setupInputHandlers() {
        ScreenMouseEvents.allowMouseClick(screen).register((_screen, context) -> {
            double mouseX = context.x();
            double mouseY = context.y();
            int button = context.button();

            if (!GlobalChestConfig.isShowEditButton() || !isEditMode) {
                if (this.entryButton != null && this.entryButton.isMouseOver(mouseX, mouseY)) {
                    return true;
                }
                return true;
            }

            if (isColorPickerOpen) {
                if (isInsidePickerWindow(mouseX, mouseY)) {
                    handleColorPickerClick(mouseX, mouseY);
                    return false;
                } else if (isClickingCustomColumn(mouseX, mouseY)) {
                    handleSidebarClick(mouseX, mouseY, button);
                    return false;
                } else if (isClickingPaletteButton(mouseX, mouseY)) {
                    isColorPickerOpen = false;
                    playClickSound(1.0f);
                    return false;
                }

                // Si clicamos fuera, cerramos el picker (incluye el botón toggle)
                isColorPickerOpen = false;
                return false;
            }

            if (this.entryButton != null && this.entryButton.isMouseOver(mouseX, mouseY)) {
                return true;
            }

            if (handleSidebarClick(mouseX, mouseY, button)) return false;

            if (button == 0) {
                Slot slot = accessor.getFocusedSlot();
                if (slot != null && !(slot.inventory instanceof PlayerInventory)) {
                    int action = calculateAction(slot, mouseX, mouseY);
                    if (action != 0) {
                        this.isDraggingLine = true;
                        this.currentDragAction = action;
                        this.dragStartSlot = slot;
                        this.dragCurrentSlot = slot;

                        if (selectedColorIndex == TOOL_ERASER_ID) {
                            this.isDragModeErasing = true;
                        } else {
                            ChestConfigManager manager = ChestConfigManager.getInstance();
                            int existingColor = manager.getLineColor(slot.getIndex(), action);
                            int selectedColorVal = getCurrentSelectedColorValue();

                            if (selectedColorVal == 0) return false;

                            if (existingColor != 0 && existingColor == (selectedColorVal | 0xFF000000)) {
                                isDragModeErasing = true;
                            } else {
                                isDragModeErasing = false;
                            }
                        }
                        return false;
                    }
                }
            }

            return false;
        });

        ScreenMouseEvents.allowMouseRelease(screen).register((_screen, context) -> {
            int button = context.button();

            if (button == 0 && this.isDraggingLine) {
                commitDrag();
                this.isDraggingLine = false;
                this.currentDragAction = 0;
                this.dragStartSlot = null;
                this.dragCurrentSlot = null;
                this.isDragModeErasing = false;
            }
            return true;
        });

        ScreenMouseEvents.allowMouseDrag(screen).register((_screen, context, deltaX, deltaY) -> {
            double mouseX = context.x();
            double mouseY = context.y();

            if (!isEditMode) return true;
            if (isColorPickerOpen) {
                handleColorPickerDrag(mouseX, mouseY);
                return false;
            }
            if (isDraggingLine) {
                Slot slot = accessor.getFocusedSlot();
                if (slot != null && !(slot.inventory instanceof PlayerInventory)) {
                    this.dragCurrentSlot = slot;
                }
                return false;
            }
            return true;
        });
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean showButton = GlobalChestConfig.isShowEditButton();
        if (this.entryButton != null) {
            // Ocultamos el botón nativo si el picker está abierto
            this.entryButton.visible = !isColorPickerOpen && showButton;
            this.entryButton.setMessage(Text.literal(this.isEditMode ? "✖" : "✎"));
            if (screen.getFocused() == this.entryButton) screen.setFocused(null);
        }

        if (!showButton && this.isEditMode) toggleEditMode();

        // Botón en modo Normal (fuera de edición)
        if (this.entryButton != null && showButton && !this.isEditMode) {
            drawModernToggleButton(context, mouseX, mouseY);
        }

        if (this.isEditMode) {
            context.fill(0, 0, screen.width, screen.height, 0x88000000);

            // Botón en Modo Edición (ANTES del overlay del picker, para que se oscurezca si se abre)
            if (this.entryButton != null && showButton) {
                drawModernToggleButton(context, mouseX, mouseY);
            }

            context.getMatrices().pushMatrix();
            // CORREGIDO: translate con solo 2 argumentos (x, y)
            context.getMatrices().translate((float)accessor.getX(), (float)accessor.getY());
            renderSavedLinesLayer(context);
            context.getMatrices().popMatrix();

            drawSidebar(context, mouseX, mouseY);
            renderDragPreview(context);
            if (!isDraggingLine && !isColorPickerOpen) {
                renderHoverPreview(context, mouseX, mouseY);
            }
            renderStatusMessage(context);

            if (isColorPickerOpen) {
                // 1. Capa de oscurecimiento (tapa el botón del lápiz)
                context.fill(0, 0, screen.width, screen.height, 0x50000000);

                // 2. Redibujamos la columna custom (brillante)
                redrawCustomColumnHighlight(context, mouseX, mouseY);

                // 3. Redibujamos el botón de la paleta (brillante)
                int[] pos = getPaletteBtnPos();
                drawPaletteButton(context, pos[0], pos[1], mouseX, mouseY);

                // 4. Dibujamos la ventana
                drawColorPickerWindow(context, mouseX, mouseY);
            }
        }
    }

    public void renderSavedLinesLayer(DrawContext context) {
        ChestConfigManager manager = ChestConfigManager.getInstance();

        for (Slot s : accessor.getHandler().slots) {
            if (s.inventory instanceof PlayerInventory) continue;
            renderLineRaw(context, s.x, s.y, manager.getLineColor(s.getIndex(), ChestConfigManager.ACTION_TOP), ChestConfigManager.ACTION_TOP);
            renderLineRaw(context, s.x, s.y, manager.getLineColor(s.getIndex(), ChestConfigManager.ACTION_BOTTOM), ChestConfigManager.ACTION_BOTTOM);
            renderLineRaw(context, s.x, s.y, manager.getLineColor(s.getIndex(), ChestConfigManager.ACTION_LEFT), ChestConfigManager.ACTION_LEFT);
            renderLineRaw(context, s.x, s.y, manager.getLineColor(s.getIndex(), ChestConfigManager.ACTION_RIGHT), ChestConfigManager.ACTION_RIGHT);
        }
    }

    private int[] getPaletteBtnPos() {
        int guiX = accessor.getX();
        int guiY = accessor.getY();
        int sx = guiX + accessor.getBackgroundWidth() + 4;

        int contentX = sx + 7;
        int paletteBoxY = guiY + SIDEBAR_Y_OFFSET + 25;

        int pX = contentX + 8;
        int pY = paletteBoxY + 4;
        int col3X = pX + (SWATCH_SIZE + 4) * 2 + 4;

        int pickerBtnX = col3X + SWATCH_SIZE + 6;
        int pickerBtnY = pY + (3 * (SWATCH_SIZE + 4));

        return new int[]{pickerBtnX, pickerBtnY};
    }

    private boolean isClickingPaletteButton(double mx, double my) {
        int[] pos = getPaletteBtnPos();
        return isHovering(pos[0], pos[1], 20, 20, mx, my);
    }

    private void drawModernToggleButton(DrawContext context, int mouseX, int mouseY) {
        if (this.entryButton == null) return;

        int x = this.entryButton.getX();
        int y = this.entryButton.getY();
        int w = this.entryButton.getWidth();
        int h = this.entryButton.getHeight();

        // Hover solo si el picker está cerrado
        boolean hover = !isColorPickerOpen && this.entryButton.isMouseOver(mouseX, mouseY);

        int bgColor = hover ? 0xFF303030 : 0xFF212121;
        context.fill(x, y, x + w, y + h, bgColor);

        drawDarkBevel(context, x, y, w, h, false);

        int textColor = 0xFFFFFFFF;
        Text message = this.entryButton.getMessage();
        int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(message);
        context.drawText(MinecraftClient.getInstance().textRenderer, message, x + (w - textWidth) / 2, y + (h - 8) / 2, textColor, false);

        if (hover) {
            context.drawStrokedRectangle(x, y, w, h, 0x40FFFFFF);
        }
    }

    private void redrawCustomColumnHighlight(DrawContext context, int mouseX, int mouseY) {
        int guiX = accessor.getX();
        int bgWidth = accessor.getBackgroundWidth();
        int sx = guiX + bgWidth + 4;
        int sy = accessor.getY();
        int contentX = sx + 7;

        int paletteBoxY = sy + SIDEBAR_Y_OFFSET + 25;
        int pX = contentX + 8;
        int pY = paletteBoxY + 4;
        int col3X = pX + (SWATCH_SIZE + 4) * 2 + 4;

        int[] worldColors = ChestConfigManager.getInstance().getCustomColors();
        for (int i = 0; i < 8; i++) {
            int y = pY + (i * (SWATCH_SIZE + 4));
            drawSwatch(context, col3X, y, worldColors[i], 16 + i, mouseX, mouseY, true);
            if (isHovering(col3X, y, SWATCH_SIZE, SWATCH_SIZE, mouseX, mouseY)) {
                context.drawTooltip(MinecraftClient.getInstance().textRenderer, Text.translatable("color.chestseparators.custom", (i + 1)), mouseX, mouseY);
            }
        }
    }

    private void drawSidebar(DrawContext context, int mouseX, int mouseY) {
        int guiX = accessor.getX();
        int guiY = accessor.getY();
        int sx = guiX + accessor.getBackgroundWidth() + 4;
        int sy = guiY;
        int contentX = sx + 7;
        int currentY = sy + SIDEBAR_Y_OFFSET;

        drawToolButton(context, contentX, currentY, TOOL_ERASER_ID, Text.translatable("tooltip.chestseparators.eraser").getString(), mouseX, mouseY);
        drawToolButton(context, contentX + 22, currentY, 100, Text.translatable("tooltip.chestseparators.clear_all").getString(), mouseX, mouseY);
        drawToolButton(context, contentX + 44, currentY, 101, Text.translatable("tooltip.chestseparators.copy").getString(), mouseX, mouseY);
        drawToolButton(context, contentX + 66, currentY, 102, Text.translatable("tooltip.chestseparators.paste").getString(), mouseX, mouseY);

        currentY += 26;

        int paletteBoxW = 86;
        int paletteBoxH = 135;
        int paletteBoxX = contentX;
        int paletteBoxY = currentY - 1;

        context.fill(paletteBoxX, paletteBoxY, paletteBoxX + paletteBoxW, paletteBoxY + paletteBoxH, 0xFF212121);
        drawDarkBevel(context, paletteBoxX, paletteBoxY, paletteBoxW, paletteBoxH, false);

        int pX = paletteBoxX + 8;
        int pY = paletteBoxY + 4;

        int col1X = pX;
        int col2X = pX + SWATCH_SIZE + 4;
        int col3X = pX + (SWATCH_SIZE + 4) * 2 + 4;

        for (int i = 0; i < 16; i++) {
            int col = i / 8; int row = i % 8;
            int x = (col == 0) ? col1X : col2X;
            int y = pY + (row * (SWATCH_SIZE + 4));

            drawSwatch(context, x, y, STANDARD_PALETTE[i], i, mouseX, mouseY, false);

            if (!isColorPickerOpen && isHovering(x, y, SWATCH_SIZE, SWATCH_SIZE, mouseX, mouseY)) {
                context.drawTooltip(MinecraftClient.getInstance().textRenderer, Text.translatable("color.minecraft." + STANDARD_COLOR_KEYS[i]), mouseX, mouseY);
            }
        }

        int[] worldColors = ChestConfigManager.getInstance().getCustomColors();
        for (int i = 0; i < 8; i++) {
            int row = i;
            int x = col3X;
            int y = pY + (row * (SWATCH_SIZE + 4));
            drawSwatch(context, x, y, worldColors[i], 16 + i, mouseX, mouseY, true);
            if (!isColorPickerOpen && isHovering(x, y, SWATCH_SIZE, SWATCH_SIZE, mouseX, mouseY)) {
                context.drawTooltip(MinecraftClient.getInstance().textRenderer, Text.translatable("color.chestseparators.custom", (i + 1)), mouseX, mouseY);
            }
        }

        int[] btnPos = getPaletteBtnPos();
        drawPaletteButton(context, btnPos[0], btnPos[1], mouseX, mouseY);
    }

    private void drawColorPickerWindow(DrawContext context, int mouseX, int mouseY) {
        int w = 220; int h = 170;
        int x = (screen.width - w) / 2; int y = (screen.height - h) / 2;

        int darkBg = 0xFF212121;
        context.fill(x, y, x + w, y + h, darkBg);

        drawDarkBevel(context, x, y, w, h, false);

        context.drawText(MinecraftClient.getInstance().textRenderer, Text.translatable("window.chestseparators.edit_color").formatted(Formatting.BOLD), x + 12, y + 12, 0xFFE0E0E0, false);

        int contentY = y + 35;
        int contentX = x + 12;

        drawSaturationValueBox(context, contentX, contentY, 100, 100);
        drawDarkBevel(context, contentX - 1, contentY - 1, 102, 102, true);

        int cursorX = contentX + (int)(pickerSat * 100);
        int cursorY = contentY + (int)((1.0f - pickerVal) * 100);
        context.drawStrokedRectangle(cursorX - 2, cursorY - 2, 5, 5, 0xFF000000);
        context.drawStrokedRectangle(cursorX - 1, cursorY - 1, 3, 3, 0xFFFFFFFF);

        int hueX = contentX + 115;
        drawHueBar(context, hueX, contentY, 20, 100);
        drawDarkBevel(context, hueX - 1, contentY - 1, 22, 102, true);

        int hueCursorY = contentY + (int)(pickerHue * 100);
        context.fill(hueX - 3, hueCursorY - 1, hueX + 23, hueCursorY + 2, 0xFF000000);
        context.fill(hueX - 1, hueCursorY, hueX + 21, hueCursorY + 1, 0xFFFFFFFF);

        int infoX = hueX + 35;
        context.fill(infoX, contentY, infoX + 45, contentY + 45, 0xFF000000 | pickerCurrentRGB);
        drawDarkBevel(context, infoX, contentY, 45, 45, true);

        int r = (pickerCurrentRGB >> 16) & 0xFF;
        int g = (pickerCurrentRGB >> 8) & 0xFF;
        int b = pickerCurrentRGB & 0xFF;

        int textY = contentY + 55;
        context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal("R: " + r).formatted(Formatting.RED), infoX, textY, 0xFFFFFFFF, false);
        context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal("G: " + g).formatted(Formatting.GREEN), infoX, textY + 12, 0xFFFFFFFF, false);
        context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal("B: " + b).formatted(Formatting.BLUE), infoX, textY + 24, 0xFFFFFFFF, false);

        int btnY = y + h - 30;
        int btnW = 60;
        drawModernButton(context, x + w - 135, btnY, btnW, 18, Text.translatable("button.chestseparators.save").getString(), 0xFF2D852D, mouseX, mouseY);
        drawModernButton(context, x + w - 70, btnY, btnW, 18, Text.translatable("button.chestseparators.cancel").getString(), 0xFF852D2D, mouseX, mouseY);
    }

    private void drawDarkBevel(DrawContext context, int x, int y, int width, int height, boolean sunken) {
        int light = 0xFF505050;
        int dark = 0xFF000000;

        if (sunken) {
            context.fill(x, y, x + width - 1, y + 1, dark);
            context.fill(x, y, x + 1, y + height - 1, dark);
            context.fill(x + width - 1, y, x + width, y + height, light);
            context.fill(x, y + height - 1, x + width, y + height, light);
        } else {
            context.fill(x, y, x + width - 1, y + 1, light);
            context.fill(x, y, x + 1, y + height - 1, light);
            context.fill(x + width - 1, y, x + width, y + height, dark);
            context.fill(x, y + height - 1, x + width, y + height, dark);
        }
    }

    private void drawModernButton(DrawContext context, int x, int y, int w, int h, String label, int baseColor, int mx, int my) {
        boolean hover = isHovering(x, y, w, h, mx, my);
        int color = hover ? shiftColor(baseColor, 30) : baseColor;

        context.fill(x, y, x + w, y + h, 0xFF000000 | color);
        drawDarkBevel(context, x, y, w, h, false);

        if (hover) {
            context.drawStrokedRectangle(x, y, w, h, 0x40FFFFFF);
        }

        int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(label);
        context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal(label), x + (w - textWidth)/2, y + (h - 8)/2, 0xFFFFFFFF, false);
    }

    private void drawSaturationValueBox(DrawContext context, int x, int y, int w, int h) {
        int step = 2;
        for (int i = 0; i < w; i += step) {
            for (int j = 0; j < h; j += step) {
                float sat = (float)i / w;
                float val = 1.0f - ((float)j / h);
                int color = Color.HSBtoRGB(pickerHue, sat, val);
                context.fill(x + i, y + j, x + i + step, y + j + step, color);
            }
        }
    }

    private void drawHueBar(DrawContext context, int x, int y, int w, int h) {
        for (int i = 0; i < h; i++) {
            float hue = (float)i / h;
            int color = Color.HSBtoRGB(hue, 1.0f, 1.0f);
            context.fill(x, y + i, x + w, y + i + 1, color);
        }
    }

    private void drawPaletteButton(DrawContext context, int x, int y, int mouseX, int mouseY) {
        boolean isTempClicked = (clickedActionId == 103 && (System.currentTimeMillis() - clickedActionTime < 200));

        // El botón reacciona visualmente siempre, incluso con picker abierto
        boolean hover = isHovering(x, y, 20, 20, mouseX, mouseY);

        int bgColor = isTempClicked ? 0xFF505050 : 0xFF212121;
        context.fill(x, y, x + 20, y + 20, bgColor);

        drawDarkBevel(context, x, y, 20, 20, isTempClicked);

        int wood = 0xFF8B4513;
        context.fill(x+4, y+4, x+16, y+14, wood);
        context.fill(x+3, y+6, x+4, y+12, wood);
        context.fill(x+16, y+6, x+17, y+12, wood);
        context.fill(x+6, y+14, x+14, y+15, wood);
        context.fill(x+6, y+3, x+14, y+4, wood);
        context.fill(x+5, y+6, x+7, y+8, 0xFF333333);
        context.fill(x+11, y+5, x+13, y+7, 0xFFFF0000);
        context.fill(x+13, y+8, x+15, y+10, 0xFF00FF00);
        context.fill(x+10, y+11, x+12, y+13, 0xFF0000FF);

        if (hover) {
            context.drawStrokedRectangle(x, y, 20, 20, 0x40FFFFFF);
            context.drawTooltip(MinecraftClient.getInstance().textRenderer, Text.translatable("tooltip.chestseparators.open_picker"), mouseX, mouseY);
        }
    }

    private void drawGenericButton(DrawContext context, int x, int y, int w, int h, String label, int mx, int my) {
        boolean hover = isHovering(x, y, w, h, mx, my);
        context.fill(x, y, x + w, y + h, 0xFFC6C6C6);
        drawStandardBevel(context, x, y, w, h, false);
        int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(label);
        context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal(label), x + (w - textWidth)/2, y + (h - 8)/2, 0xFF000000, false);
        if (hover) context.drawStrokedRectangle(x, y, w, h, 0xFFFFFFFF);
    }

    private boolean isInsidePickerWindow(double mx, double my) {
        int w = 220; int h = 170;
        int x = (screen.width - w) / 2; int y = (screen.height - h) / 2;
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private boolean isClickingCustomColumn(double mx, double my) {
        int guiX = accessor.getX();
        int sx = guiX + accessor.getBackgroundWidth() + 4;
        int sy = accessor.getY();
        int contentX = sx + 7;
        int paletteBoxY = sy + SIDEBAR_Y_OFFSET + 25;
        int pX = contentX + 8;
        int pY = paletteBoxY + 4;
        int col3X = pX + (SWATCH_SIZE + 4) * 2 + 4;
        return mx >= col3X && mx <= col3X + SWATCH_SIZE && my >= pY && my <= pY + (8 * (SWATCH_SIZE + 4));
    }

    private void handleColorPickerClick(double mx, double my) {
        int w = 220; int h = 170;
        int x = (screen.width - w) / 2; int y = (screen.height - h) / 2;
        int contentX = x + 12;
        int contentY = y + 35;

        if (isHovering(contentX, contentY, 100, 100, mx, my)) {
            updateSatValFromMouse(mx, my, contentX, contentY);
            playClickSound(1.0f);
            return;
        }
        int hueX = contentX + 115;
        if (isHovering(hueX, contentY, 20, 100, mx, my)) {
            updateHueFromMouse(my, contentY);
            playClickSound(1.0f);
            return;
        }
        int btnY = y + h - 30;
        if (isHovering(x + w - 135, btnY, 60, 18, mx, my)) {
            if (editingCustomIndex != -1) {
                ChestConfigManager.getInstance().setCustomColor(editingCustomIndex, pickerCurrentRGB & 0x00FFFFFF);
                ChestConfigManager.getInstance().saveWorldPalette();
            }
            isColorPickerOpen = false;
            playClickSound(1.0f);
            return;
        }
        if (isHovering(x + w - 70, btnY, 60, 18, mx, my)) {
            isColorPickerOpen = false;
            playClickSound(1.0f);
            return;
        }
    }

    private void handleColorPickerDrag(double mx, double my) {
        int w = 220; int h = 170;
        int x = (screen.width - w) / 2; int y = (screen.height - h) / 2;
        int contentX = x + 12;
        int contentY = y + 35;

        if (mx >= contentX - 20 && mx <= contentX + 110 && my >= contentY - 20 && my <= contentY + 120) {
            updateSatValFromMouse(mx, my, contentX, contentY);
        } else if (mx >= contentX + 100 && mx <= contentX + 150 && my >= contentY - 20 && my <= contentY + 120) {
            updateHueFromMouse(my, contentY);
        }
    }

    private void updateSatValFromMouse(double mx, double my, int x, int y) {
        float relX = (float)(mx - x);
        float relY = (float)(my - y);
        pickerSat = MathHelper.clamp(relX / 100.0f, 0.0f, 1.0f);
        pickerVal = 1.0f - MathHelper.clamp(relY / 100.0f, 0.0f, 1.0f);
        updatePickerColor();
    }

    private void updateHueFromMouse(double my, int y) {
        float relY = (float)(my - y);
        pickerHue = MathHelper.clamp(relY / 100.0f, 0.0f, 1.0f);
        updatePickerColor();
    }

    private void updatePickerColor() {
        pickerCurrentRGB = Color.HSBtoRGB(pickerHue, pickerSat, pickerVal);
    }

    private void openColorPicker(int initialColor) {
        isColorPickerOpen = true;
        if (initialColor == 0) initialColor = 0xFF0000;
        float[] hsb = new float[3];
        int r = (initialColor >> 16) & 0xFF;
        int g = (initialColor >> 8) & 0xFF;
        int b = initialColor & 0xFF;
        Color.RGBtoHSB(r, g, b, hsb);
        pickerHue = hsb[0];
        pickerSat = hsb[1];
        pickerVal = hsb[2];
        pickerCurrentRGB = initialColor | 0xFF000000;
    }

    private void drawSwatch(DrawContext context, int x, int y, int color, int index, int mouseX, int mouseY, boolean isCustom) {
        boolean disabled = isColorPickerOpen && !isCustom;
        boolean selected = (index == selectedColorIndex);
        boolean sunken = selected;
        boolean hover = !disabled && isHovering(x, y, SWATCH_SIZE, SWATCH_SIZE, mouseX, mouseY);

        if (color == 0) {
            context.fill(x, y, x + SWATCH_SIZE, y + SWATCH_SIZE, 0xFF555555);
            int checkSize = SWATCH_SIZE / 2;
            context.fill(x, y, x + checkSize, y + checkSize, 0xFF333333);
            context.fill(x + checkSize, y + checkSize, x + SWATCH_SIZE, y + SWATCH_SIZE, 0xFF333333);
            drawStandardBevel(context, x, y, SWATCH_SIZE, SWATCH_SIZE, sunken);
        } else {
            context.fill(x, y, x + SWATCH_SIZE, y + SWATCH_SIZE, 0xFF000000 | color);
            drawColorBevel(context, x, y, SWATCH_SIZE, SWATCH_SIZE, color, sunken);
        }

        if (hover && !selected && !isCustom) {
            context.drawStrokedRectangle(x, y, SWATCH_SIZE, SWATCH_SIZE, 0xFFFFFFFF);
        } else if (isCustom && hover && !selected) {
            context.drawStrokedRectangle(x, y, SWATCH_SIZE, SWATCH_SIZE, 0x80FFFFFF);
        }

        if (disabled) {
            context.fill(x, y, x + SWATCH_SIZE, y + SWATCH_SIZE, 0x80000000);
        }
    }

    private int shiftColor(int color, int amount) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = MathHelper.clamp(r + amount, 0, 255);
        g = MathHelper.clamp(g + amount, 0, 255);
        b = MathHelper.clamp(b + amount, 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void drawColorBevel(DrawContext context, int x, int y, int width, int height, int baseColor, boolean sunken) {
        int light = shiftColor(baseColor, 80) | 0xFF000000;
        int dark = shiftColor(baseColor, -80) | 0xFF000000;
        int shadow = shiftColor(baseColor, -40) | 0xFF000000;

        if (sunken) {
            context.fill(x, y, x + width - 1, y + 1, dark);
            context.fill(x, y, x + 1, y + height - 1, dark);
            context.fill(x + width - 1, y, x + width, y + height, light);
            context.fill(x, y + height - 1, x + width, y + height, light);
        } else {
            context.fill(x, y, x + width - 1, y + 1, light);
            context.fill(x, y, x + 1, y + height - 1, light);
            context.fill(x + width - 1, y, x + width, y + height, dark);
            context.fill(x, y + height - 1, x + width, y + height, dark);
            context.fill(x + width - 2, y + 1, x + width - 1, y + height - 1, shadow);
            context.fill(x + 1, y + height - 2, x + width - 2, y + height - 1, shadow);
        }
    }

    private void drawStandardBevel(DrawContext context, int x, int y, int width, int height, boolean sunken) {
        int light = 0xFFFFFFFF; int dark = 0xFF373737; int shadow = 0xFF8B8B8B;
        if (sunken) {
            context.fill(x, y, x + width - 1, y + 1, dark);
            context.fill(x, y, x + 1, y + height - 1, dark);
            context.fill(x + width - 1, y, x + width, y + height, light);
            context.fill(x, y + height - 1, x + width, y + height, light);
        } else {
            context.fill(x, y, x + width - 1, y + 1, light);
            context.fill(x, y, x + 1, y + height - 1, light);
            context.fill(x + width - 1, y, x + width, y + height, dark);
            context.fill(x, y + height - 1, x + width, y + height, dark);
            context.fill(x + width - 2, y + 1, x + width - 1, y + height - 1, shadow);
            context.fill(x + 1, y + height - 2, x + width - 2, y + height - 1, shadow);
        }
    }

    private void drawToolButton(DrawContext context, int x, int y, int id, String label, int mouseX, int mouseY) {
        boolean activeState = false;
        if (id == TOOL_ERASER_ID) activeState = (selectedColorIndex == TOOL_ERASER_ID);
        else if (id == 102 && !ChestConfigManager.getInstance().hasClipboardData()) {
            context.fill(x, y, x + TOOL_BTN_SIZE, y + TOOL_BTN_SIZE, 0xFF454545);
            drawDarkBevel(context, x, y, TOOL_BTN_SIZE, TOOL_BTN_SIZE, false);
            drawPasteIcon(context, x, y, false);
            return;
        }

        boolean isTempClicked = false;
        if ((id >= 100 && id <= 103)) {
            if (clickedActionId == id && (System.currentTimeMillis() - clickedActionTime < 200)) isTempClicked = true;
        }

        boolean hover = !isColorPickerOpen && isHovering(x, y, TOOL_BTN_SIZE, TOOL_BTN_SIZE, mouseX, mouseY);
        boolean sunken = activeState || isTempClicked;

        int bgColor = sunken ? 0xFF101010 : 0xFF212121;
        context.fill(x, y, x + TOOL_BTN_SIZE, y + TOOL_BTN_SIZE, bgColor);

        drawDarkBevel(context, x, y, TOOL_BTN_SIZE, TOOL_BTN_SIZE, sunken);

        if (id == TOOL_ERASER_ID) drawEraserIcon(context, x, y);
        else if (id == 100) drawTrashIcon(context, x, y);
        else if (id == 101) drawCopyIcon(context, x, y);
        else if (id == 102) drawPasteIcon(context, x, y, true);

        if (hover) {
            context.drawStrokedRectangle(x, y, TOOL_BTN_SIZE, TOOL_BTN_SIZE, 0x40FFFFFF);
            context.drawTooltip(MinecraftClient.getInstance().textRenderer, Text.literal(label), mouseX, mouseY);
        }

        if (isColorPickerOpen) {
            context.fill(x, y, x + TOOL_BTN_SIZE, y + TOOL_BTN_SIZE, 0x80000000);
        }
    }

    private void drawEraserIcon(DrawContext context, int x, int y) {
        int cx = x + 6; int cy = y + 5; int width = 7; int split = 7;
        context.fill(cx + 1, cy, cx + width, cy + split, 0xFFE88787);
        context.fill(cx + 1, cy + split, cx + width, cy + 10, 0xFFFFFFFF);
    }
    private void drawTrashIcon(DrawContext context, int x, int y) {
        int cx = x + 5; int cy = y + 4;
        context.fill(cx + 1, cy + 2, cx + 9, cy + 3, 0xFFFFFFFF);
        context.fill(cx + 4, cy + 1, cx + 6, cy + 2, 0xFFFFFFFF);
        context.fill(cx + 2, cy + 3, cx + 8, cy + 10, 0xFFEEEEEE);
        context.fill(cx + 3, cy + 4, cx + 4, cy + 9, 0xFF555555);
        context.fill(cx + 6, cy + 4, cx + 7, cy + 9, 0xFF555555);
    }
    private void drawCopyIcon(DrawContext context, int x, int y) {
        int cx = x + 6; int cy = y + 4;
        context.fill(cx + 3, cy + 1, cx + 8, cy + 7, 0xFFAAAAAA);
        context.fill(cx + 1, cy + 4, cx + 6, cy + 10, 0xFFFFFFFF);
    }
    private void drawPasteIcon(DrawContext context, int x, int y, boolean active) {
        int cx = x + 5; int cy = y + 3;
        int boardColor = active ? 0xFF8B4513 : 0xFF404040;
        context.fill(cx + 1, cy + 2, cx + 9, cy + 12, boardColor);
        int paperColor = active ? 0xFFFFFFFF : 0xFF808080;
        context.fill(cx + 2, cy + 3, cx + 8, cy + 11, paperColor);
        context.fill(cx + 3, cy + 1, cx + 7, cy + 3, 0xFFCCCCCC);
    }

    private void triggerActionAnimation(int actionId) {
        this.clickedActionId = actionId;
        this.clickedActionTime = System.currentTimeMillis();
    }

    private boolean handleSidebarClick(double mx, double my, int button) {
        int guiX = accessor.getX();
        int sx = guiX + accessor.getBackgroundWidth() + 4;
        int sy = accessor.getY();

        if (mx < sx || mx > sx + SIDEBAR_WIDTH || my < sy + SIDEBAR_Y_OFFSET || my > sy + accessor.getBackgroundHeight()) return false;

        int contentX = sx + 7;
        int currentY = sy + SIDEBAR_Y_OFFSET;

        if (isHovering(contentX, currentY, TOOL_BTN_SIZE, TOOL_BTN_SIZE, mx, my)) {
            this.selectedColorIndex = TOOL_ERASER_ID;
            this.editingCustomIndex = -1;
            playClickSound(1.0f);
            return true;
        }
        if (isHovering(contentX + 22, currentY, TOOL_BTN_SIZE, TOOL_BTN_SIZE, mx, my)) {
            if (isEntityChest && currentEntityUUID != null) ChestConfigManager.getInstance().clearEntityChest(currentEntityUUID);
            else if (isEnderChest) ChestConfigManager.getInstance().clearEnderChest();
            else ChestConfigManager.getInstance().clearChest(currentChestPos, currentDimension);
            playClickSound(0.8f);
            showStatus(Text.translatable("message.chestseparators.cleared"), Formatting.RED);
            triggerActionAnimation(100);
            return true;
        }
        if (isHovering(contentX + 44, currentY, TOOL_BTN_SIZE, TOOL_BTN_SIZE, mx, my)) {
            ChestConfigManager.getInstance().copyToClipboard();
            playClickSound(1.0f);
            showStatus(Text.translatable("message.chestseparators.copied"), Formatting.GRAY);
            triggerActionAnimation(101);
            return true;
        }
        if (isHovering(contentX + 66, currentY, TOOL_BTN_SIZE, TOOL_BTN_SIZE, mx, my)) {
            if (ChestConfigManager.getInstance().hasClipboardData()) {
                ChestConfigManager.getInstance().pasteFromClipboard();
                saveSmart();
                playClickSound(1.0f);
                showStatus(Text.translatable("message.chestseparators.pasted"), Formatting.GREEN);
                triggerActionAnimation(102);
            }
            return true;
        }

        currentY += 26;

        int paletteBoxX = contentX;
        int paletteBoxY = currentY - 1;
        int pX = paletteBoxX + 8;
        int pY = paletteBoxY + 4;

        int col1X = pX;
        int col2X = pX + SWATCH_SIZE + 4;
        for (int i = 0; i < 16; i++) {
            int col = i / 8; int row = i % 8;
            int x = (col == 0) ? col1X : col2X;
            int y = pY + (row * (SWATCH_SIZE + 4));
            if (isHovering(x, y, SWATCH_SIZE, SWATCH_SIZE, mx, my)) {
                this.selectedColorIndex = i;
                this.editingCustomIndex = -1;
                playClickSound(1.0f);
                return true;
            }
        }

        int col3X = pX + (SWATCH_SIZE + 4) * 2 + 4;
        int[] worldColors = ChestConfigManager.getInstance().getCustomColors();
        for (int i = 0; i < 8; i++) {
            int row = i;
            int x = col3X;
            int y = pY + (row * (SWATCH_SIZE + 4));
            if (isHovering(x, y, SWATCH_SIZE, SWATCH_SIZE, mx, my)) {
                this.selectedColorIndex = 16 + i;
                this.editingCustomIndex = i;
                playClickSound(1.0f);
                return true;
            }
        }

        int pickerBtnX = col3X + SWATCH_SIZE + 6;
        int pickerBtnY = pY + (3 * (SWATCH_SIZE + 4));
        if (isHovering(pickerBtnX, pickerBtnY, 20, 20, mx, my)) {
            if (editingCustomIndex == -1) {
                editingCustomIndex = 0;
                selectedColorIndex = 16;
            }
            openColorPicker(ChestConfigManager.getInstance().getCustomColors()[editingCustomIndex]);
            playClickSound(1.0f);
            triggerActionAnimation(103);
            return true;
        }

        return true;
    }

    private void renderLineRaw(DrawContext context, int x, int y, int color, int action) {
        if (color == 0) return;
        int renderColor = color | 0xFF000000;

        if (action == ChestConfigManager.ACTION_TOP) context.fill(x - 1, y - 1, x + 17, y, renderColor);
        else if (action == ChestConfigManager.ACTION_BOTTOM) context.fill(x - 1, y + 16, x + 17, y + 17, renderColor);
        else if (action == ChestConfigManager.ACTION_LEFT) context.fill(x - 1, y - 1, x, y + 17, renderColor);
        else if (action == ChestConfigManager.ACTION_RIGHT) context.fill(x + 16, y - 1, x + 17, y + 17, renderColor);
    }

    private void commitDrag() {
        if (dragStartSlot == null || dragCurrentSlot == null) return;
        List<Slot> affectedSlots = calculateAffectedSlots();
        ChestConfigManager manager = ChestConfigManager.getInstance();
        boolean changeMade = false;
        int colorToPaint = getCurrentSelectedColorValue();
        if (colorToPaint == 0 && !isDragModeErasing) return;

        for (Slot slot : affectedSlots) {
            if (isDragModeErasing) manager.removeLine(slot.getIndex(), currentDragAction);
            else manager.paintLine(slot.getIndex(), currentDragAction, colorToPaint);
            changeMade = true;
        }
        if (changeMade) {
            saveSmart();
            playClickSound(1.0f);
        }
    }

    private void renderHoverPreview(DrawContext context, int mouseX, int mouseY) {
        Slot slot = accessor.getFocusedSlot();
        if (slot != null && !(slot.inventory instanceof PlayerInventory)) {
            int action = calculateAction(slot, mouseX, mouseY);
            if (action != 0) {
                int colorVal = getCurrentSelectedColorValue();
                if (colorVal == 0 && selectedColorIndex != TOOL_ERASER_ID) return;

                int color = (selectedColorIndex == TOOL_ERASER_ID) ? 0x88FFFFFF : (colorVal & 0x00FFFFFF) | 0x88000000;
                int guiX = accessor.getX();
                int guiY = accessor.getY();
                int x = guiX + slot.x;
                int y = guiY + slot.y;

                if ((action & ChestConfigManager.ACTION_TOP) != 0) context.fill(x - 1, y - 1, x + 17, y, color);
                else if ((action & ChestConfigManager.ACTION_BOTTOM) != 0) context.fill(x - 1, y + 16, x + 17, y + 17, color);
                else if ((action & ChestConfigManager.ACTION_LEFT) != 0) context.fill(x - 1, y - 1, x, y + 17, color);
                else if ((action & ChestConfigManager.ACTION_RIGHT) != 0) context.fill(x + 16, y - 1, x + 17, y + 17, color);
            }
        }
    }

    private void renderDragPreview(DrawContext context) {
        if (!isDraggingLine) return;
        List<Slot> activeSlots = calculateAffectedSlots();
        if (!activeSlots.isEmpty()) {
            int colorVal = getCurrentSelectedColorValue();
            if (colorVal == 0 && selectedColorIndex != TOOL_ERASER_ID) return;

            int color = (selectedColorIndex == TOOL_ERASER_ID) ? 0x88FFFFFF : (colorVal & 0x00FFFFFF) | 0x88000000;
            int guiX = accessor.getX();
            int guiY = accessor.getY();

            for (Slot slot : activeSlots) {
                int x = guiX + slot.x; int y = guiY + slot.y;
                if ((currentDragAction & ChestConfigManager.ACTION_TOP) != 0) context.fill(x - 1, y - 1, x + 17, y, color);
                if ((currentDragAction & ChestConfigManager.ACTION_BOTTOM) != 0) context.fill(x - 1, y + 16, x + 17, y + 17, color);
                if ((currentDragAction & ChestConfigManager.ACTION_LEFT) != 0) context.fill(x - 1, y - 1, x, y + 17, color);
                if ((currentDragAction & ChestConfigManager.ACTION_RIGHT) != 0) context.fill(x + 16, y - 1, x + 17, y + 17, color);
            }
        }
    }

    private boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void toggleEditMode() {
        this.isEditMode = !this.isEditMode;
        if (this.entryButton != null) this.entryButton.setMessage(Text.literal(this.isEditMode ? "✖" : "✎"));
        if (!this.isEditMode) {
            GlobalChestConfig.saveConfig();
            this.editingCustomIndex = -1;
        }
    }

    private void saveSmart() {
        if (isEntityChest && currentEntityUUID != null) ChestConfigManager.getInstance().saveEntityConfig(currentEntityUUID);
        else if (isEnderChest) ChestConfigManager.getInstance().saveEnderConfig();
        else ChestConfigManager.getInstance().saveConfig(currentChestPos, currentDimension);
    }

    private void showStatus(Text message, Formatting color) {
        this.statusMessage = message.copy().formatted(color);
        this.statusMessageTime = System.currentTimeMillis();
    }

    private void renderStatusMessage(DrawContext context) {
        if (this.statusMessage != null) {
            long elapsed = System.currentTimeMillis() - this.statusMessageTime;
            if (elapsed < 2000) {
                int alpha = 255;
                if (elapsed > 1500) alpha = (int) (255 * (1.0f - (elapsed - 1500) / 500.0f));
                int color = (alpha << 24) | 0xFFFFFF;
                context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, this.statusMessage, screen.width / 2, screen.height - 40, color);
            } else {
                this.statusMessage = null;
            }
        }
    }

    private int getCurrentSelectedColorValue() {
        if (selectedColorIndex == TOOL_ERASER_ID) return 0;
        if (selectedColorIndex < 16) return STANDARD_PALETTE[selectedColorIndex];
        if (selectedColorIndex < 24) {
            return ChestConfigManager.getInstance().getCustomColors()[selectedColorIndex - 16];
        }
        return 0;
    }

    private int calculateAction(Slot slot, double mouseX, double mouseY) {
        int guiX = accessor.getX(); int guiY = accessor.getY();
        double relativeX = mouseX - (guiX + slot.x); double relativeY = mouseY - (guiY + slot.y);
        double distTop = Math.abs(relativeY); double distBottom = Math.abs(relativeY - 16);
        double distLeft = Math.abs(relativeX); double distRight = Math.abs(relativeX - 16);
        double minDist = Math.min(Math.min(distTop, distBottom), Math.min(distLeft, distRight));
        if (minDist <= 5.0) {
            if (minDist == distTop) return ChestConfigManager.ACTION_TOP;
            else if (minDist == distBottom) return ChestConfigManager.ACTION_BOTTOM;
            else if (minDist == distLeft) return ChestConfigManager.ACTION_LEFT;
            else if (minDist == distRight) return ChestConfigManager.ACTION_RIGHT;
        }
        return 0;
    }

    private List<Slot> calculateAffectedSlots() {
        if (dragStartSlot == null || dragCurrentSlot == null) return new ArrayList<>();
        List<Slot> slots = new ArrayList<>();
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

    private void playClickSound(float pitch) {
        MinecraftClient.getInstance().getSoundManager().play(
                net.minecraft.client.sound.PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, pitch)
        );
    }

    public boolean keyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.isEditMode) {
            if (isColorPickerOpen) {
                isColorPickerOpen = false;
            } else {
                toggleEditMode();
            }
            return true;
        }
        return false;
    }
}