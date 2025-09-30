package de.tert0.containerclaims.mixin;

import de.tert0.containerclaims.ClaimAccess;
import de.tert0.containerclaims.ClaimUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShelfBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShelfBlock.class)
public class ShelfBlockMixin {
    @Inject(method = "onUseWithItem", at = @At("HEAD"), cancellable = true)
    void canPlayerUse(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        ClaimAccess claimAccess = (ClaimAccess) blockEntity;
        if(claimAccess == null || !ClaimUtils.isClaimed(claimAccess)) return;
        if(!ClaimUtils.canUse(claimAccess, (ServerPlayerEntity) player)) {
            // Sync block entity and player inventory to override client side predictions
            blockEntity.markDirty();
            player.playerScreenHandler.syncState();

            cir.setReturnValue(ActionResult.FAIL);
        }
    }
}
