package de.tert0.containerclaims.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import de.tert0.containerclaims.ClaimAccess;
import de.tert0.containerclaims.ClaimUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DropperBlock.class)
public class DropperBlockMixin {
    @Inject(
            method = "dispenseFrom",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;"
            ),
            cancellable = true
    )
    void beforeItemTransfer(ServerLevel level, BlockState state, BlockPos pos, CallbackInfo ci, @Local(name = "direction") Direction direction) {
        ClaimAccess claimAccess = (ClaimAccess) level.getBlockEntity(pos.relative(direction));
        if(claimAccess != null && ClaimUtils.isClaimed(claimAccess)) {
            ci.cancel();
        }
    }
}
