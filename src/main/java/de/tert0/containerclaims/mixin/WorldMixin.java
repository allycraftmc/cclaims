package de.tert0.containerclaims.mixin;

import de.tert0.containerclaims.GlobalClaimState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
public abstract class WorldMixin {
    @Inject(method = "removeBlockEntity", at = @At("RETURN"))
    void removeBlockEntity(BlockPos pos, CallbackInfo ci) {
        Level world = (Level) (Object) this;
        GlobalClaimState.getWorldState((ServerLevel) world).removePosition(pos);
    }
}
