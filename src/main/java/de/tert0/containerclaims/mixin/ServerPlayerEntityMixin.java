package de.tert0.containerclaims.mixin;

import de.tert0.containerclaims.AdminModeAccess;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin implements AdminModeAccess {
    @Unique
    private boolean adminMode = false;

    public boolean container_claims$getAdminMode() {
        return this.adminMode;
    }

    public void container_claims$setAdminMode(boolean adminMode) {
        this.adminMode = adminMode;
    }
}
