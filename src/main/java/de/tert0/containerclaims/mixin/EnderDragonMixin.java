package de.tert0.containerclaims.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import de.tert0.containerclaims.ClaimAccess;
import de.tert0.containerclaims.ClaimUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EnderDragon.class)
public abstract class EnderDragonMixin extends Mob {
    protected EnderDragonMixin(EntityType<? extends @NotNull Mob> entityType, Level world) {
        super(entityType, world);
    }

    @ModifyExpressionValue(
            method = "checkWalls",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z", ordinal = 1)
    )
    boolean isDragonImmune(boolean original, @Local BlockPos blockPos) {
        ClaimAccess claimAccess = (ClaimAccess) this.level().getBlockEntity(blockPos);
        if(claimAccess != null && ClaimUtils.isClaimed(claimAccess)) {
           return true;
        }
        return original;
    }
}
