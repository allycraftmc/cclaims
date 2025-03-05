package de.tert0.containerclaims.mixin;

import de.tert0.containerclaims.ClaimAccess;
import de.tert0.containerclaims.ClaimUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.entity.Hopper;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
    @Inject(
            method = "extract(Lnet/minecraft/world/World;Lnet/minecraft/block/entity/Hopper;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;getAvailableSlots(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/util/math/Direction;)[I"),
            cancellable = true
    )
    private static void extract(World world, Hopper hopper, CallbackInfoReturnable<Boolean> cir) {
        BlockPos blockPos = BlockPos.ofFloored(hopper.getHopperX(), hopper.getHopperY() + 1.0, hopper.getHopperZ());
        ClaimAccess claimAccess = (ClaimAccess) world.getBlockEntity(blockPos);
        if(claimAccess != null && ClaimUtils.isClaimed(claimAccess)) {
            if(hopper instanceof HopperBlockEntity hopperBlockEntity) {
                ClaimAccess hopperClaimAccess = (ClaimAccess) hopperBlockEntity;
                if(
                        !ClaimUtils.isClaimed(hopperClaimAccess)
                                || (!claimAccess.cclaims$getClaim().owner().equals(hopperClaimAccess.cclaims$getClaim().owner()) && !claimAccess.cclaims$getClaim().trusted().contains(hopperClaimAccess.cclaims$getClaim().owner()))
                ) {
                    cir.setReturnValue(false);
                }
            } else {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(
            method = "insert",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void insert(World world, BlockPos pos, HopperBlockEntity blockEntity, CallbackInfoReturnable<Boolean> cir) {
        BlockState state = world.getBlockState(pos);
        ClaimAccess claimAccess = (ClaimAccess) world.getBlockEntity(pos.offset(state.get(HopperBlock.FACING)));
        if(claimAccess != null && ClaimUtils.isClaimed(claimAccess)) {
            ClaimAccess hopperClaimAccess = (ClaimAccess) blockEntity;
            if(
                    !ClaimUtils.isClaimed(hopperClaimAccess)
                            || (!hopperClaimAccess.cclaims$getClaim().owner().equals(claimAccess.cclaims$getClaim().owner()) && !claimAccess.cclaims$getClaim().trusted().contains(hopperClaimAccess.cclaims$getClaim().owner()))
            ) {
                cir.setReturnValue(false);
            }
        }
    }
}
