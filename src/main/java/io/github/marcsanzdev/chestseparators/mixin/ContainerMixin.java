package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.common.UniqueContainer;
import io.github.marcsanzdev.chestseparators.network.SyncContainerIdPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.*;
import net.minecraft.entity.ContainerUser;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin({
        ChestBlockEntity.class,
        BarrelBlockEntity.class,
        ShulkerBoxBlockEntity.class
})
public abstract class ContainerMixin extends LootableContainerBlockEntity implements UniqueContainer {

    @Unique
    private UUID chestSeparators$uuid;

    public ContainerMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // --- INTERFACE IMPLEMENTATION ---

    @Override
    public UUID chestSeparators$getUniqueId() {
        if (this.chestSeparators$uuid == null) {
            this.chestSeparators$uuid = UUID.randomUUID();
        }
        return this.chestSeparators$uuid;
    }

    @Override
    public void chestSeparators$setUniqueId(UUID uuid) {
        this.chestSeparators$uuid = uuid;
    }

    // --- NETWORK SYNC ---

    @Inject(method = "onOpen", at = @At("HEAD"))
    private void injectOnOpen(ContainerUser user, CallbackInfo ci) {
        if (user instanceof ServerPlayerEntity serverPlayer) {
            if (this.chestSeparators$uuid == null) {
                this.chestSeparators$uuid = UUID.randomUUID();
            }
            ServerPlayNetworking.send(serverPlayer, new SyncContainerIdPayload(this.chestSeparators$uuid));
        }
    }

    // --- DATA PERSISTENCE ---

    @Inject(method = "writeData", at = @At("HEAD"))
    private void injectWriteData(WriteView view, CallbackInfo ci) {
        if (this.chestSeparators$uuid == null) {
            this.chestSeparators$uuid = UUID.randomUUID();
        }
        try {
            // Guardamos el ID como texto
            view.putString("ChestSeparatorsId", this.chestSeparators$uuid.toString());
        } catch (Exception e) {
            // Ignoramos errores de escritura para no crashear
        }
    }

    @Inject(method = "readData", at = @At("HEAD"))
    private void injectReadData(ReadView view, CallbackInfo ci) {
        try {
            // CORRECCIÓN AQUÍ: Añadimos un string vacío "" como valor por defecto.
            // Si "ChestSeparatorsId" no existe, devuelve "" en lugar de fallar.
            String idString = view.getString("ChestSeparatorsId", "");

            if (idString != null && !idString.isEmpty()) {
                this.chestSeparators$uuid = UUID.fromString(idString);
            }
        } catch (Exception e) {
            this.chestSeparators$uuid = UUID.randomUUID();
        }
    }
}