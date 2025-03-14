package de.tert0.containerclaims.mixin;

import de.tert0.containerclaims.AdminModeAccess;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin implements AdminModeAccess {
    @Unique
    private boolean adminMode = false;

    public boolean cclaims$getAdminMode() {
        return this.adminMode;
    }

    public void cclaims$setAdminMode(boolean adminMode) {
        this.adminMode = adminMode;
    }
}
