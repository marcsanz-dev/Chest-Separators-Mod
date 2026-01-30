package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.client.screen.SeparatorEditorScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin para añadir el botón de edición.
 * Apuntamos a HandledScreen (padre) porque GenericContainerScreen no tiene el método 'init'.
 */
@Mixin(HandledScreen.class)
public abstract class GenericContainerScreenMixin<T extends ScreenHandler> extends net.minecraft.client.gui.screen.Screen {

    protected GenericContainerScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addEditorButton(CallbackInfo ci) {
        // FILTRO IMPORTANTE:
        // Solo añadimos el botón si esta pantalla es, de hecho, un Cofre (GenericContainerScreen).
        // Si no ponemos esto, el botón saldría en el inventario, hornos, mesas de crafteo, etc.
        if (!((Object) this instanceof GenericContainerScreen)) {
            return;
        }

        // Recuperamos la instancia correcta casteando 'this'
        HandledScreen<?> screen = (HandledScreen<?>) (Object) this;

        // Accedemos a las coordenadas del GUI (x, y) y ancho (backgroundWidth)
        // Como estamos en un Mixin de HandledScreen, a veces necesitamos accessors,
        // pero vamos a intentar obtenerlos dinámicamente o usar valores fijos relativos al centro si falla.

        // Nota: En HandledScreen, las variables 'x', 'y' y 'backgroundWidth' suelen ser accesibles
        // vía getters o directamente si el mapeo lo permite.
        // Si te da error en 'screen.getX()', probaremos con reflexión o shadow.

        // Intento 1: Usar los métodos públicos de acceso (en versiones modernas existen)
        int guiLeft = 0;
        int guiTop = 0;
        int guiWidth = 176; // Ancho estándar

        try {
            // Intentamos acceder a campos comunes (esto depende de tus mappings yarn)
            // Si esto falla al compilar, te daré la versión con @Shadow
            guiLeft = ((HandledScreenAccessor) screen).getX();
            guiTop = ((HandledScreenAccessor) screen).getY();
            guiWidth = ((HandledScreenAccessor) screen).getBackgroundWidth();
        } catch (Exception e) {
            // Fallback por si acaso: calculamos el centro a mano
            guiLeft = (this.width - 176) / 2;
            guiTop = (this.height - 166) / 2;
        }

        // Posición: Esquina superior derecha del cofre
        int buttonX = guiLeft + guiWidth - 20;
        int buttonY = guiTop + 5;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("E"), button -> {
                    if (this.client != null && this.client.player != null) {
                        // Abrimos el editor
                        // Necesitamos el handler y el inventario. Al estar en HandledScreen, 'screen.getScreenHandler()' funciona.
                        GenericContainerScreen genericScreen = (GenericContainerScreen) (Object) this;
                        this.client.setScreen(new SeparatorEditorScreen(
                                genericScreen.getScreenHandler(),
                                this.client.player.getInventory(),
                                this.title
                        ));
                    }
                })
                .dimensions(buttonX, buttonY, 20, 20)
                .build());
    }
}