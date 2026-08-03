package de.tert0.containerclaims.mixin;

import de.tert0.containerclaims.ClaimAccess;
import de.tert0.containerclaims.ClaimUtils;
import de.tert0.containerclaims.DoubleChestUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.CommonColors;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "placeBlock(Lnet/minecraft/world/item/context/BlockPlaceContext;Lnet/minecraft/world/level/block/state/BlockState;)Z", at = @At("HEAD"), cancellable = true)
    void placeBlock(BlockPlaceContext context, BlockState placementState, CallbackInfoReturnable<Boolean> cir) {
        if(context.getPlayer() == null) return; // TODO
        ServerPlayer player = (ServerPlayer) context.getPlayer();

        ClaimAccess claimAccess = (ClaimAccess) DoubleChestUtils.getNeighborBlockEntity(context.getClickedPos(), context.getLevel(), placementState);
        if(claimAccess != null && ClaimUtils.isClaimed(claimAccess)) {
            if(!ClaimUtils.isOwnerOrAdmin(claimAccess, player)) {
                player.sendSystemMessage(Component.literal("The other chest is claimed!").withColor(CommonColors.RED), true);
                int slot = switch (context.getHand()) {
                    case MAIN_HAND -> player.getInventory().getSelectedSlot();
                    case OFF_HAND -> Inventory.SLOT_OFFHAND;
                };
                player.connection.send(player.getInventory().createInventoryUpdatePacket(slot));
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "updateCustomBlockEntityTag(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;)Z", at = @At("RETURN"))
    void updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack itemStack, BlockState placedState, CallbackInfoReturnable<Boolean> cir) {
        if(player == null) return; // TODO

        ClaimAccess claimAccess = (ClaimAccess) DoubleChestUtils.getNeighborBlockEntity(pos, level, placedState);
        if(claimAccess != null && ClaimUtils.isClaimed(claimAccess) && ClaimUtils.isOwnerOrAdmin(claimAccess, player)) {
            ClaimAccess newClaimAccess = (ClaimAccess) level.getBlockEntity(pos);
            if(newClaimAccess != null) {
                newClaimAccess.cclaims$setClaim(claimAccess.cclaims$getClaim());
                ClaimUtils.markClaimed(newClaimAccess, (ServerLevel) level);
            } else {
                player.sendSystemMessage(Component.literal("Unable to apply claim to double chest. Please report this issue!").withColor(CommonColors.RED));
            }
        }
    }
}
