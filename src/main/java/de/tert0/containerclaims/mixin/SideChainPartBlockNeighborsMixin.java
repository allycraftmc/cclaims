package de.tert0.containerclaims.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import de.tert0.containerclaims.ClaimAccess;
import de.tert0.containerclaims.ClaimUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SideChainPartBlock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SideChainPartBlock.Neighbors.class)
public class SideChainPartBlockNeighborsMixin {
    @Shadow
    @Final
    private LevelAccessor level;

    @Shadow
    @Final
    private BlockPos center;

    @ModifyExpressionValue(method = "createNewNeighbor", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/SideChainPartBlock$Neighbors;isConnectableToThisBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    boolean preventConnectingClaimedBlocks(boolean original, @Local(name = "pos", argsOnly = true) BlockPos pos) {
        ClaimAccess claimAccess = (ClaimAccess) this.level.getBlockEntity(this.center);
        ClaimAccess neighborClaimAccess = (ClaimAccess) this.level.getBlockEntity(pos);
        if((claimAccess != null && ClaimUtils.isClaimed(claimAccess)) || (neighborClaimAccess != null && ClaimUtils.isClaimed(neighborClaimAccess))) {
            return false;
        }
        return original;
    }
}
