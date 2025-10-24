package de.tert0.containerclaims.mixin;

import de.tert0.containerclaims.ClaimAccess;
import de.tert0.containerclaims.ClaimUtils;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ai.brain.task.MoveItemsTask;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(MoveItemsTask.class)
public class MoveItemsTaskMixin {
    @Inject(method = "getStorageFor", at = @At("RETURN"), cancellable = true)
    void getStorageFor(PathAwareEntity entity, World world, BlockEntity blockEntity, Set<GlobalPos> visitedPositions, Set<GlobalPos> unreachablePositions, Box box, CallbackInfoReturnable<MoveItemsTask.Storage> cir) {
        ClaimAccess claimAccess = (ClaimAccess) blockEntity;
        if(claimAccess != null && ClaimUtils.isClaimed(claimAccess)) {
            cir.setReturnValue(null);
        }
    }
}
