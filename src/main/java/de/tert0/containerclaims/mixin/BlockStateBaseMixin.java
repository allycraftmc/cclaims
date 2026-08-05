package de.tert0.containerclaims.mixin;

import de.tert0.containerclaims.ClaimAccess;
import de.tert0.containerclaims.ClaimUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.CommonColors;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public class BlockStateBaseMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    void useItemOn(ItemStack itemStack, Level level, Player player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        BlockPos pos = hitResult.getBlockPos();
        ClaimAccess claimAccess = (ClaimAccess) level.getBlockEntity(pos);
        if(claimAccess == null || !ClaimUtils.isClaimed(claimAccess)) return;

        if(!ClaimUtils.canUse(claimAccess, (ServerPlayer) player)) {
            player.sendOverlayMessage(Component.literal("This block is claimed!").withColor(CommonColors.RED));
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }

}
