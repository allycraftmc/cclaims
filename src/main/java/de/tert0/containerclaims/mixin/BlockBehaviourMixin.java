package de.tert0.containerclaims.mixin;

import de.tert0.containerclaims.ClaimAccess;
import de.tert0.containerclaims.ClaimUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.CommonColors;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

@Mixin(BlockBehaviour.class)
public class BlockBehaviourMixin {
    @Inject(method = "useItemOn", at = @At("RETURN"), cancellable = true)
    void useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        ClaimAccess claimAccess = (ClaimAccess) world.getBlockEntity(pos);
        if(claimAccess == null || !ClaimUtils.isClaimed(claimAccess)) return;

        if(!ClaimUtils.canUse(claimAccess, (ServerPlayer) player)) {
            player.sendOverlayMessage(Component.literal("This block is claimed!").withColor(CommonColors.RED));
            cir.setReturnValue(InteractionResult.SUCCESS); // this will prevent the default action
        }
    }

    @Inject(method = "onExplosionHit", at = @At("HEAD"), cancellable = true)
    void onExplosionHit(BlockState state, ServerLevel world, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> stackMerger, CallbackInfo ci) {
        ClaimAccess claimAccess = (ClaimAccess) world.getBlockEntity(pos);
        if(claimAccess != null && ClaimUtils.isClaimed(claimAccess)) {
            ci.cancel();
        }
    }
}
