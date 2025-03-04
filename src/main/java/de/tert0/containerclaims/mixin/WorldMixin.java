package de.tert0.containerclaims.mixin;

import de.tert0.containerclaims.GlobalClaimState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public abstract class WorldMixin {
    @Inject(method = "removeBlockEntity", at = @At("RETURN"))
    void removeBlockEntity(BlockPos pos, CallbackInfo ci) {
        World world = (World) (Object) this;
        GlobalClaimState.getWorldState((ServerWorld) world).removePosition(pos);
    }
}
