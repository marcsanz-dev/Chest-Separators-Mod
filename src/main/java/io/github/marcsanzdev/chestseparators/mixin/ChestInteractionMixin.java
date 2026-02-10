package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.util.ChestPosStorage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.VehicleInventory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Intercepts low-level player interaction events (Block breaking, Entity interaction, Block interaction)
// to capture contextual metadata required for data persistence.
// This acts as a Context Provider, buffering the target's location or UUID before the GUI layer initializes.
@Mixin(ClientPlayerInteractionManager.class)
public class ChestInteractionMixin {

    // Hooks into the block interaction phase to cache the target BlockPos and Dimension ID.
    // This state injection is critical for linking the subsequent ContainerScreen to specific NBT data files.
    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void captureChestPos(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        if (hand == Hand.MAIN_HAND) {
            ChestPosStorage.lastClickedPos = hitResult.getBlockPos();
            // Explicitly resets the entity flag to ensure mutual exclusivity between Block and Entity contexts.
            ChestPosStorage.isEntityOpened = false;

            if (MinecraftClient.getInstance().world != null) {
                ChestPosStorage.lastClickedDimension = MinecraftClient.getInstance().world.getRegistryKey().getValue().toString();
            }
        }
    }

    // Intercepts entity interactions to support mobile containers (e.g., Donkey, Chest Minecart).
    // Uses structural typing checks (instanceof VehicleInventory) and heuristic class name analysis
    // to identify valid inventory holders dynamically across different mod/version implementations.
    @Inject(method = "interactEntity", at = @At("HEAD"))
    private void captureEntity(PlayerEntity player, Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (hand == Hand.MAIN_HAND) {
            if (entity instanceof VehicleInventory || entity.getClass().getName().contains("Chest")) {
                ChestPosStorage.lastClickedEntityUUID = entity.getUuid();
                ChestPosStorage.isEntityOpened = true;

                if (MinecraftClient.getInstance().world != null) {
                    ChestPosStorage.lastClickedDimension = MinecraftClient.getInstance().world.getRegistryKey().getValue().toString();
                }
            }
        }
    }

    // Hooks into the block destruction event to trigger a data cleanup routine.
    // This enforces referential integrity by purging orphaned configuration files associated with the destroyed block.
    @Inject(method = "breakBlock", at = @At("HEAD"))
    private void onBreakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (MinecraftClient.getInstance().world != null) {
            String dim = MinecraftClient.getInstance().world.getRegistryKey().getValue().toString();
            ChestConfigManager.getInstance().clearChest(pos, dim);
        }
    }
}