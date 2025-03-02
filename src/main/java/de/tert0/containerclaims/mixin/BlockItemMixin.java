package de.tert0.containerclaims.mixin;

import de.tert0.containerclaims.ClaimAccess;
import de.tert0.containerclaims.ClaimUtils;
import de.tert0.containerclaims.DoubleChestUtils;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;Lnet/minecraft/block/BlockState;)Z", at = @At("HEAD"), cancellable = true)
    void place(ItemPlacementContext context, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if(context.getPlayer() == null) return; // TODO

        ClaimAccess claimAccess = (ClaimAccess) DoubleChestUtils.getNeighborBlockEntity(context.getBlockPos(), context.getWorld(), state);
        if(claimAccess != null && ClaimUtils.isClaimed(claimAccess)) {
            if(!ClaimUtils.isOwnerOrAdmin(claimAccess, context.getPlayer())) {
                context.getPlayer().sendMessage(Text.literal("The other chest is claimed!").withColor(Colors.RED), false);
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "postPlacement", at = @At("RETURN"))
    void postPlacement(BlockPos pos, World world, @Nullable PlayerEntity player, ItemStack stack, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if(player == null) return; // TODO

        ClaimAccess claimAccess = (ClaimAccess) DoubleChestUtils.getNeighborBlockEntity(pos, world, state);
        if(claimAccess != null && ClaimUtils.isClaimed(claimAccess) && ClaimUtils.isOwnerOrAdmin(claimAccess, player)) {
            ClaimAccess newClaimAccess = (ClaimAccess) world.getBlockEntity(pos);
            if(newClaimAccess != null) {
                newClaimAccess.container_claims$setClaim(claimAccess.container_claims$getClaim());
                ClaimUtils.markClaimed(newClaimAccess, (ServerWorld) world);
            } else {
                player.sendMessage(Text.literal("Unable to apply claim to double chest. Please report this issue!").withColor(Colors.RED), false);
            }
        }
    }
}
