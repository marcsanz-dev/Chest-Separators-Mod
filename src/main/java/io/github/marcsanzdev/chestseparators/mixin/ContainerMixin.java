package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.common.UniqueContainer;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Mixin to inject a persistent UUID into any storage container.
 * This ensures every Chest/Barrel/Shulker has a unique ID for our mod to track.
 */
@Mixin(LockableContainerBlockEntity.class)
public abstract class ContainerMixin extends BlockEntity implements UniqueContainer {

    @Unique
    private UUID chestSeparators$uuid;

    /**
     * FIXED CONSTRUCTOR:
     * BlockEntity requires 3 arguments: Type, Pos, and State.
     * We must pass them to super() to satisfy the Java compiler.
     */
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

    // --- DATA PERSISTENCE ---

    @Inject(method = "writeNbt", at = @At("HEAD"))
    private void injectWriteNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup, CallbackInfo ci) {
        if (this.chestSeparators$uuid == null) {
            this.chestSeparators$uuid = UUID.randomUUID();
        }
        // Save UUID using standard NBT helper methods
        nbt.putUuid("ChestSeparatorsId", this.chestSeparators$uuid);
    }

    @Inject(method = "readNbt", at = @At("HEAD"))
    private void injectReadNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup, CallbackInfo ci) {
        // Load UUID if it exists in the data
        if (nbt.contains("ChestSeparatorsId")) {
            this.chestSeparators$uuid = nbt.getUuid("ChestSeparatorsId");
        }
    }
}