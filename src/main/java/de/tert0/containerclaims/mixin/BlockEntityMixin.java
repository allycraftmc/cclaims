package de.tert0.containerclaims.mixin;

import de.tert0.containerclaims.ClaimComponent;
import de.tert0.containerclaims.ClaimAccess;
import de.tert0.containerclaims.ContainerClaimMod;
import de.tert0.containerclaims.GlobalClaimState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
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

    @Shadow @Final private static Logger LOGGER;

    @Shadow @Nullable public abstract World getWorld();

    @Shadow public abstract BlockPos getPos();

    @Shadow public abstract boolean isRemoved();

    @Unique
    private ClaimComponent claim;

    @Inject(method = "writeNbt", at = @At("RETURN"))
    private void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries, CallbackInfo ci) {
        if(this.claim == null) return;
        NbtElement nbtClaim = ClaimComponent.CODEC.encodeStart(NbtOps.INSTANCE, this.claim)
                .resultOrPartial(LOGGER::error)
                .orElseThrow();
        nbt.put(ContainerClaimMod.CLAIM_DATA_ID.toString(), nbtClaim);

        // to track claimed containers that were not directly claimed through the mod (e.g. modifying nbt or cloning a block entity)
        if(!this.isRemoved()) {
            GlobalClaimState.getWorldState((ServerWorld) this.getWorld()).addPosition(this.getPos());
        }
    }

    @Inject(method = "readNbt", at = @At("RETURN"))
    private void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries, CallbackInfo ci) {
        nbt.getCompound(ContainerClaimMod.CLAIM_DATA_ID.toString()).ifPresent(nbtClaim -> {
            this.claim = ClaimComponent.CODEC.parse(NbtOps.INSTANCE, nbtClaim)
                    .resultOrPartial(LOGGER::error)
                    .orElseThrow();
        });
    }

    @Unique
    @Override
    public ClaimComponent cclaims$getClaim() {
        return this.claim;
    }

    @Unique
    @Override
    public void cclaims$setClaim(ClaimComponent claim) {
        this.claim = claim;
        this.markDirty();
    }
}
