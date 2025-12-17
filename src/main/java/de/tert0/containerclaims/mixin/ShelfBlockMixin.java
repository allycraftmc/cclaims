package de.tert0.containerclaims.mixin;

import de.tert0.containerclaims.ClaimAccess;
import de.tert0.containerclaims.ClaimUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ShelfBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShelfBlock.class)
public class ShelfBlockMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    void useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        ClaimAccess claimAccess = (ClaimAccess) blockEntity;
        if(claimAccess == null || !ClaimUtils.isClaimed(claimAccess)) return;
        if(!ClaimUtils.canUse(claimAccess, (ServerPlayer) player)) {
            // Sync block entity and player inventory to override client side predictions
            blockEntity.setChanged();
            player.inventoryMenu.sendAllDataToRemote();

            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
