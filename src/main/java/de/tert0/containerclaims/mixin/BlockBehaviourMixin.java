package de.tert0.containerclaims.mixin;

import de.tert0.containerclaims.ClaimAccess;
import de.tert0.containerclaims.ClaimUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(BlockBehaviour.class)
public class BlockBehaviourMixin {
    @Inject(method = "onExplosionHit", at = @At("HEAD"), cancellable = true)
    void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> onHit, CallbackInfo ci) {
        ClaimAccess claimAccess = (ClaimAccess) level.getBlockEntity(pos);
        if(claimAccess != null && ClaimUtils.isClaimed(claimAccess)) {
            ci.cancel();
        }
    }
}
