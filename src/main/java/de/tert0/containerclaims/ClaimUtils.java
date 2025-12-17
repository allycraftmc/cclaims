package de.tert0.containerclaims;

import com.google.common.collect.ImmutableSet;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ClaimUtils {
    public static void markClaimed(ClaimAccess claimAccess, ServerLevel serverWorld) {
        BlockEntity blockEntity = (BlockEntity) claimAccess;
        GlobalClaimState.getWorldState(serverWorld).addPosition(blockEntity.getBlockPos());
    }

    public static void claim(ClaimAccess claimAccess, UUID uuid, ServerLevel serverWorld) {
        ClaimComponent claim = new ClaimComponent(uuid, Instant.now(), ImmutableSet.of(), ImmutableSet.of());

        claimAccess.cclaims$setClaim(claim);
        markClaimed(claimAccess, serverWorld);

        BlockEntity blockEntity = DoubleChestUtils.getNeighborBlockEntity(((BlockEntity) claimAccess).getBlockPos(), serverWorld);
        if(blockEntity != null) {
            ClaimAccess otherClaimAccess = (ClaimAccess) blockEntity;
            otherClaimAccess.cclaims$setClaim(claim);
            markClaimed(otherClaimAccess, serverWorld);
        }
    }

    public static void markUnclaimed(ClaimAccess claimAccess, ServerLevel serverWorld) {
        BlockEntity blockEntity = (BlockEntity) claimAccess;
        GlobalClaimState.getWorldState(serverWorld).removePosition(blockEntity.getBlockPos());
    }

    public static void unclaim(ClaimAccess claimAccess, ServerLevel serverWorld) {
        claimAccess.cclaims$setClaim(null);
        markUnclaimed(claimAccess, serverWorld);

        BlockEntity blockEntity = DoubleChestUtils.getNeighborBlockEntity(((BlockEntity) claimAccess).getBlockPos(), serverWorld);
        if(blockEntity != null) {
            ClaimAccess otherClaimAccess = (ClaimAccess) blockEntity;
            otherClaimAccess.cclaims$setClaim(null);
            markUnclaimed(otherClaimAccess, serverWorld);
        }
    }

    public static void trust(ClaimAccess claimAccess, Collection<UUID> entries) {
        ClaimComponent claim = claimAccess.cclaims$getClaim()
                .addTrusted(entries);
        claimAccess.cclaims$setClaim(claim);

        BlockEntity blockEntity = DoubleChestUtils.getNeighborBlockEntity(((BlockEntity) claimAccess).getBlockPos(), ((BlockEntity) claimAccess).getLevel());
        if(blockEntity != null) {
            ClaimAccess otherClaimAccess = (ClaimAccess) blockEntity;
            otherClaimAccess.cclaims$setClaim(claim);
        }
    }

    public static void trustGroup(ClaimAccess claimAccess, GroupComponent group) {
        ClaimComponent claim = claimAccess.cclaims$getClaim()
                .addTrustedGroups(ImmutableSet.of(group.uuid()));
        claimAccess.cclaims$setClaim(claim);

        BlockEntity blockEntity = DoubleChestUtils.getNeighborBlockEntity(((BlockEntity) claimAccess).getBlockPos(), ((BlockEntity) claimAccess).getLevel());
        if(blockEntity != null) {
            ClaimAccess otherClaimAccess = (ClaimAccess) blockEntity;
            otherClaimAccess.cclaims$setClaim(claim);
        }
    }

    public static void untrust(ClaimAccess claimAccess, Collection<UUID> entries) {
        ClaimComponent claim = claimAccess.cclaims$getClaim()
                .removeTrusted(entries);
        claimAccess.cclaims$setClaim(claim);

        BlockEntity blockEntity = DoubleChestUtils.getNeighborBlockEntity(((BlockEntity) claimAccess).getBlockPos(), ((BlockEntity) claimAccess).getLevel());
        if(blockEntity != null) {
            ClaimAccess otherClaimAccess = (ClaimAccess) blockEntity;
            otherClaimAccess.cclaims$setClaim(claim);
        }
    }

    public static void untrustGroup(ClaimAccess claimAccess, GroupComponent group) {
        ClaimComponent claim = claimAccess.cclaims$getClaim()
                .removeTrustedGroups(ImmutableSet.of(group.uuid()));
        claimAccess.cclaims$setClaim(claim);

        BlockEntity blockEntity = DoubleChestUtils.getNeighborBlockEntity(((BlockEntity) claimAccess).getBlockPos(), ((BlockEntity) claimAccess).getLevel());
        if(blockEntity != null) {
            ClaimAccess otherClaimAccess = (ClaimAccess) blockEntity;
            otherClaimAccess.cclaims$setClaim(claim);
        }
    }

    public static boolean isClaimed(ClaimAccess claimAccess) {
        return claimAccess.cclaims$getClaim() != null;
    }

    public static boolean isOwner(ClaimAccess claimAccess, UUID uuid) {
        return claimAccess.cclaims$getClaim().owner().equals(uuid);
    }

    public static boolean isOwnerOrAdmin(ClaimAccess claimAccess, Player player) {
        if(player instanceof AdminModeAccess adminModeAccess && adminModeAccess.cclaims$getAdminMode()) {
            return true;
        }
        return isOwner(claimAccess, player.getUUID());
    }

    public static boolean isTrusted(ClaimAccess claimAccess, UUID uuid) {
        return claimAccess.cclaims$getClaim().trusted().contains(uuid);
    }

    public static boolean isGroupTrusted(ClaimAccess claimAccess, GroupComponent group) {
        return claimAccess.cclaims$getClaim().trustedGroups().contains(group.uuid());
    }

    public static boolean isGroupTrusted(ClaimAccess claimAccess, ServerPlayer player) {
        return GroupState.getState(player.level().getServer()).getGroups().stream()
                .filter(g -> g.isMember(player.getUUID()))
                .anyMatch(g -> ClaimUtils.isGroupTrusted(claimAccess, g));
    }

    public static boolean canUse(ClaimAccess claimAccess, ServerPlayer player) {
        return isOwnerOrAdmin(claimAccess, player) || isTrusted(claimAccess, player.getUUID()) || isGroupTrusted(claimAccess, player);
    }
}
