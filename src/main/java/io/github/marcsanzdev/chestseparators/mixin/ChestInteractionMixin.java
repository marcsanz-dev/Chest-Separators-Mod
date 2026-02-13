package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.util.ChestPosStorage;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
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
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ChestInteractionMixin {

    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void captureChestPos(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        if (hand == Hand.MAIN_HAND) {
            BlockPos clickedPos = hitResult.getBlockPos();
            BlockPos finalPos = clickedPos;

            if (player.getEntityWorld() != null) {
                BlockState state = player.getEntityWorld().getBlockState(clickedPos);

                if (state.getBlock() instanceof ChestBlock) {
                    ChestType type = state.get(ChestBlock.CHEST_TYPE);

                    if (type != ChestType.SINGLE) {
                        Direction facing = state.get(ChestBlock.FACING);
                        Direction neighborDir = type == ChestType.LEFT ?
                                facing.rotateYClockwise() :
                                facing.rotateYCounterclockwise();

                        BlockPos neighborPos = clickedPos.offset(neighborDir);

                        // Determinar la posición "Main" (coordenadas menores) y "Secondary"
                        BlockPos minPos = (neighborPos.compareTo(clickedPos) < 0) ? neighborPos : clickedPos;
                        BlockPos maxPos = (neighborPos.compareTo(clickedPos) < 0) ? clickedPos : neighborPos;

                        // La configuración siempre se carga desde la posición Main
                        finalPos = minPos;

                        // --- MIGRACIÓN AL CREAR COFRE DOBLE ---
                        // Si se forma un cofre doble poniendo uno nuevo a la izquierda (en minPos),
                        // la configuración antigua estará en maxPos. Hay que moverla a minPos.
                        String dim = player.getEntityWorld().getRegistryKey().getValue().toString();
                        ChestConfigManager manager = ChestConfigManager.getInstance();

                        if (!manager.hasConfig(minPos, dim) && manager.hasConfig(maxPos, dim)) {
                            manager.moveConfig(maxPos, minPos, dim);
                        }
                    }
                }
            }

            ChestPosStorage.lastClickedPos = finalPos;
            ChestPosStorage.isEntityOpened = false;

            if (MinecraftClient.getInstance().world != null) {
                ChestPosStorage.lastClickedDimension = MinecraftClient.getInstance().world.getRegistryKey().getValue().toString();
            }
        }
    }

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

    @Inject(method = "breakBlock", at = @At("HEAD"))
    private void onBreakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (MinecraftClient.getInstance().world != null) {
            String dim = MinecraftClient.getInstance().world.getRegistryKey().getValue().toString();
            ChestConfigManager manager = ChestConfigManager.getInstance();

            BlockState state = MinecraftClient.getInstance().world.getBlockState(pos);

            // --- LÓGICA DE ROTURA DE COFRE DOBLE ---
            if (state.getBlock() instanceof ChestBlock) {
                ChestType type = state.get(ChestBlock.CHEST_TYPE);
                if (type != ChestType.SINGLE) {
                    Direction facing = state.get(ChestBlock.FACING);
                    Direction neighborDir = type == ChestType.LEFT ?
                            facing.rotateYClockwise() :
                            facing.rotateYCounterclockwise();

                    BlockPos neighborPos = pos.offset(neighborDir);

                    // Identificamos cuál era el Main (menor coord)
                    boolean breakingMain = pos.compareTo(neighborPos) < 0;

                    if (breakingMain) {
                        // Rompemos el Main (donde está el archivo). Movemos al vecino.
                        manager.moveConfig(pos, neighborPos, dim);
                        // Truncamos el vecino (ahora es simple)
                        manager.truncateChestConfig(neighborPos, dim, 26);
                    } else {
                        // Rompemos el secundario. La config está en el vecino (Main).
                        // Solo truncamos el vecino.
                        manager.truncateChestConfig(neighborPos, dim, 26);
                    }

                    // Borramos config de la posición rota por limpieza
                    manager.clearChest(pos, dim);
                    return;
                }
            }

            // Rotura normal (Cofre simple o cualquier otro bloque)
            manager.clearChest(pos, dim);
        }
    }
}