package de.tert0.containerclaims.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import de.tert0.containerclaims.ClaimAccess;
import de.tert0.containerclaims.ClaimUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CrafterBlock.class)
public class CrafterBlockMixin {
    @WrapOperation(method = "dispenseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;"))
    ItemStack wrapItemTransfer(Container from, Container container, ItemStack itemStack, Direction direction, Operation<ItemStack> original, @Local(name = "level", argsOnly = true) ServerLevel level, @Local(name = "pos", argsOnly = true) BlockPos pos, @Local(name = "blockEntity", argsOnly = true) CrafterBlockEntity blockEntity) {
        ClaimAccess crafterClaimAccess = (ClaimAccess) blockEntity;
        ClaimAccess claimAccess = (ClaimAccess) level.getBlockEntity(pos.relative(direction.getOpposite()));
        if(claimAccess != null && ClaimUtils.isClaimed(claimAccess)) {
            if(
                    !ClaimUtils.isClaimed(crafterClaimAccess)
                        || (!claimAccess.cclaims$getClaim().owner().equals(crafterClaimAccess.cclaims$getClaim().owner()) && !claimAccess.cclaims$getClaim().trusted().contains(crafterClaimAccess.cclaims$getClaim().owner()))
            ) {
                return itemStack;
            }
        }
        return original.call(from, container, itemStack, direction);
    }
}
