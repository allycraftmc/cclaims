package de.tert0.containerclaims.mixin;

import de.tert0.containerclaims.ClaimAccess;
import de.tert0.containerclaims.ClaimUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.PistonBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBlock.class)
public abstract class PistonBlockMixin {
    @Inject(
            method = "onSyncedBlockEvent",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;removeBlock(Lnet/minecraft/util/math/BlockPos;Z)Z"),
            cancellable = true
    )
    void onSyncedBlockEvent(BlockState state, World world, BlockPos pos, int type, int data, CallbackInfoReturnable<Boolean> cir) {
        BlockPos affectedPos = pos.offset(state.get(PistonBlock.FACING));
        ClaimAccess claimAccess = (ClaimAccess) world.getBlockEntity(affectedPos);
        if(claimAccess != null && ClaimUtils.isClaimed(claimAccess)) {
            cir.setReturnValue(false);
        }
    }
}
