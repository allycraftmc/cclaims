package de.tert0.containerclaims.mixin;

import de.tert0.containerclaims.ClaimComponent;
import de.tert0.containerclaims.ClaimAccess;
import de.tert0.containerclaims.ContainerClaimMod;
import de.tert0.containerclaims.GlobalClaimState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin implements ClaimAccess {
    @Shadow
    public abstract void setChanged();

    @Shadow @Nullable public abstract Level getLevel();

    @Shadow public abstract BlockPos getBlockPos();

    @Shadow public abstract boolean isRemoved();

    @Unique
    private ClaimComponent claim;

    @Unique
    private boolean dataFixupCompleted = false;

    @Inject(method = "saveAdditional", at = @At("RETURN"))
    private void saveAdditional(ValueOutput view, CallbackInfo ci) {
        if(this.claim == null) return;
        view.store(ContainerClaimMod.CLAIM_DATA_ID.toString(), ClaimComponent.CODEC, this.claim);

        // to track claimed containers that were not directly claimed through the mod (e.g. modifying nbt or cloning a block entity)
        if(!this.isRemoved()) {
            GlobalClaimState.getWorldState((ServerLevel) this.getLevel()).addPosition(this.getBlockPos());
        }
    }

    @Inject(method = "loadAdditional", at = @At("RETURN"))
    private void loadAdditional(ValueInput view, CallbackInfo ci) {
        view.read(ContainerClaimMod.CLAIM_DATA_ID.toString(), ClaimComponent.CODEC).ifPresent(claim -> this.claim = claim);
    }

    @Unique
    @Override
    public @Nullable ClaimComponent cclaims$getClaim() {
        if(this.claim != null && !this.dataFixupCompleted && this.getLevel() != null) {
            this.claim = this.claim.fixup(this.getLevel().getServer());
            this.setChanged();
            this.dataFixupCompleted = true;
        }
        return this.claim;
    }

    @Unique
    @Override
    public void cclaims$setClaim(ClaimComponent claim) {
        this.claim = claim;
        this.setChanged();
    }
}
