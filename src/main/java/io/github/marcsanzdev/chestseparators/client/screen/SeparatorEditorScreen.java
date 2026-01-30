package io.github.marcsanzdev.chestseparators.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;

// Heredamos de GenericContainerScreen para que se siga viendo el cofre y el inventario
public class SeparatorEditorScreen extends GenericContainerScreen {

    public SeparatorEditorScreen(GenericContainerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        // Aquí añadiremos el selector de colores en el futuro
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Renderiza el fondo del cofre y los items (para que veamos dónde dibujamos)
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        // Dibujamos los tooltips de los items si pasamos el ratón por encima
        this.drawMouseoverTooltip(context, mouseX, mouseY);

        // Texto temporal para saber que estamos en el editor
        context.drawText(this.textRenderer, "✏️ EDITOR MODE", this.x, this.y - 15, 0xFF0000, false);
    }
}