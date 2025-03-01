package de.tert0.containerclaims;

import net.minecraft.entity.player.PlayerEntity;

import java.util.UUID;

public class ClaimUtils {
    public static boolean isClaimed(ClaimAccess claimAccess) {
        return claimAccess.container_claims$getClaim() != null;
    }

    public static boolean isOwner(ClaimAccess claimAccess, UUID uuid) {
        return claimAccess.container_claims$getClaim().owner().equals(uuid);
    }

    public static boolean isOwnerOrAdmin(ClaimAccess claimAccess, PlayerEntity player) {
        if(player instanceof AdminModeAccess adminModeAccess && adminModeAccess.container_claims$getAdminMode()) {
            return true;
        }
        return isOwner(claimAccess, player.getUuid());
    }

    public static boolean isTrusted(ClaimAccess claimAccess, UUID uuid) {
        return claimAccess.container_claims$getClaim().trusted().contains(uuid);
    }

    public static boolean canUse(ClaimAccess claimAccess, PlayerEntity player) {
        return isOwnerOrAdmin(claimAccess, player) || isTrusted(claimAccess, player.getUuid());
    }
}
