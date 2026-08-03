package de.tert0.containerclaims.mixin;

import de.tert0.containerclaims.ClaimAccess;
import de.tert0.containerclaims.ClaimUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

@Mixin(TransportItemsBetweenContainers.class)
public class TransportItemsBetweenContainersMixin {
    @Inject(method = "isTargetValidToPick", at = @At("RETURN"), cancellable = true)
    void isTargetValidToPick(PathfinderMob body, Level level, BlockEntity blockEntity, Set<GlobalPos> visitedPositions, Set<GlobalPos> unreachablePositions, AABB targetBlockSearchArea, CallbackInfoReturnable<TransportItemsBetweenContainers.TransportItemTarget> cir) {
        ClaimAccess claimAccess = (ClaimAccess) blockEntity;
        if(claimAccess != null && ClaimUtils.isClaimed(claimAccess)) {
            cir.setReturnValue(null);
        }
    }
}
