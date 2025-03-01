package de.tert0.containerclaims;

import java.util.UUID;

public class ClaimUtils {
    public static boolean isClaimed(ClaimAccess claimAccess) {
        return claimAccess.container_claims$getClaim() != null;
    }

    public static boolean isOwner(ClaimAccess claimAccess, UUID uuid) {
        return claimAccess.container_claims$getClaim().owner().equals(uuid);
    }

    public static boolean isTrusted(ClaimAccess claimAccess, UUID uuid) {
        return claimAccess.container_claims$getClaim().trusted().contains(uuid);
    }

    public static boolean isOwnerOrTrusted(ClaimAccess claimAccess, UUID uuid) {
        return isOwner(claimAccess, uuid) || isTrusted(claimAccess, uuid);
    }
}
