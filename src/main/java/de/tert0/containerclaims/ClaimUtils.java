package de.tert0.containerclaims;

import com.google.common.collect.ImmutableSet;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.UUID;

public class ClaimUtils {
    public static void claim(ClaimAccess claimAccess, UUID uuid, ServerWorld serverWorld) {
        BlockEntity blockEntity = (BlockEntity) claimAccess; // TODO

        claimAccess.container_claims$setClaim(new ClaimComponent(uuid, ImmutableSet.of()));
        GlobalClaimState.getWorldState(serverWorld).addPosition(blockEntity.getPos());
    }

    public static void unclaim(ClaimAccess claimAccess, ServerWorld serverWorld) {
        BlockEntity blockEntity = (BlockEntity) claimAccess; // TODO

        claimAccess.container_claims$setClaim(null);
        GlobalClaimState.getWorldState(serverWorld).removePosition(blockEntity.getPos());
    }

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
