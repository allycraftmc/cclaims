package de.tert0.containerclaims.mixin;

import de.tert0.containerclaims.ClaimAccess;
import de.tert0.containerclaims.ClaimUtils;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Colors;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiConsumer;

@Mixin(AbstractBlock.class)
public class AbstractBlockMixin {
    @Inject(method = "onUseWithItem", at = @At("RETURN"), cancellable = true)
    void onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        ClaimAccess claimAccess = (ClaimAccess) world.getBlockEntity(pos);
        if(claimAccess == null || !ClaimUtils.isClaimed(claimAccess)) return;

        if(!ClaimUtils.canUse(claimAccess, player)) {
            player.sendMessage(Text.literal("This block is claimed!").withColor(Colors.RED), true);
            cir.setReturnValue(ActionResult.SUCCESS); // this will prevent the default action
        }
    }

    @Inject(method = "onExploded", at = @At("HEAD"), cancellable = true)
    void onExploded(BlockState state, ServerWorld world, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> stackMerger, CallbackInfo ci) {
        ClaimAccess claimAccess = (ClaimAccess) world.getBlockEntity(pos);
        if(claimAccess != null && ClaimUtils.isClaimed(claimAccess)) {
            ci.cancel();
        }
    }
}
