package de.tert0.containerclaims;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.Identifier;

import java.util.Set;

public class ContainerClaims implements ModInitializer {

    public static final String MOD_ID = "container-claims";
    public static final Identifier CLAIM_DATA_ID = Identifier.of(MOD_ID, "claim");
    // TODO Hopper, Furnaces, Shulker boxes, brewing stand, Crafter, Dispenser/Dropper, ...
    public static final Set<BlockEntityType<?>> SUPPORTED_BLOCK_ENTITIES = Set.of(BlockEntityType.CHEST, BlockEntityType.BARREL, BlockEntityType.TRAPPED_CHEST);

    @Override
    public void onInitialize() {
        ClaimCommand.init();

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            ClaimAccess claimAccess = (ClaimAccess) blockEntity;
            if(blockEntity == null || !ClaimUtils.isClaimed(claimAccess)) return true;

            return ClaimUtils.isOwnerOrAdmin(claimAccess, player);
        });
    }
}
