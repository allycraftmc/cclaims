package de.tert0.containerclaims.mixin;

import de.tert0.containerclaims.ClaimComponent;
import de.tert0.containerclaims.ClaimAccess;
import de.tert0.containerclaims.ContainerClaimMod;
import de.tert0.containerclaims.GlobalClaimState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
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
    public abstract void markDirty();

    @Shadow @Nullable public abstract World getWorld();

    @Shadow public abstract BlockPos getPos();

    @Shadow public abstract boolean isRemoved();

    @Unique
    private ClaimComponent claim;

    @Unique
    private boolean dataFixupCompleted = false;

    @Inject(method = "writeData", at = @At("RETURN"))
    private void writeNbt(WriteView view, CallbackInfo ci) {
        if(this.claim == null) return;
        view.put(ContainerClaimMod.CLAIM_DATA_ID.toString(), ClaimComponent.CODEC, this.claim);

        // to track claimed containers that were not directly claimed through the mod (e.g. modifying nbt or cloning a block entity)
        if(!this.isRemoved()) {
            GlobalClaimState.getWorldState((ServerWorld) this.getWorld()).addPosition(this.getPos());
        }
    }

    @Inject(method = "readData", at = @At("RETURN"))
    private void readNbt(ReadView view, CallbackInfo ci) {
        view.read(ContainerClaimMod.CLAIM_DATA_ID.toString(), ClaimComponent.CODEC).ifPresent(claim -> this.claim = claim);
    }

    @Unique
    @Override
    public @Nullable ClaimComponent cclaims$getClaim() {
        if(this.claim != null && !this.dataFixupCompleted && this.getWorld() != null) {
            this.claim = this.claim.fixup(this.getWorld().getServer());
            this.markDirty();
            this.dataFixupCompleted = true;
        }
        return this.claim;
    }

    @Unique
    @Override
    public void cclaims$setClaim(ClaimComponent claim) {
        this.claim = claim;
        this.markDirty();
    }
}
