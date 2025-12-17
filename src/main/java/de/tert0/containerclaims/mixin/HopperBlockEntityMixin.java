package de.tert0.containerclaims.mixin;

import de.tert0.containerclaims.ClaimAccess;
import de.tert0.containerclaims.ClaimUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
    @Inject(
            method = "suckInItems(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/entity/Hopper;)Z",
            at = @At(value = "FIELD", target = "Lnet/minecraft/core/Direction;DOWN:Lnet/minecraft/core/Direction;"),
            cancellable = true
    )
    private static void suckInItems(Level world, Hopper hopper, CallbackInfoReturnable<Boolean> cir) {
        BlockPos blockPos = BlockPos.containing(hopper.getLevelX(), hopper.getLevelY() + 1.0, hopper.getLevelZ());
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
            method = "ejectItems",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void ejectItems(Level world, BlockPos pos, HopperBlockEntity blockEntity, CallbackInfoReturnable<Boolean> cir) {
        BlockState state = world.getBlockState(pos);
        ClaimAccess claimAccess = (ClaimAccess) world.getBlockEntity(pos.relative(state.getValue(HopperBlock.FACING)));
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
