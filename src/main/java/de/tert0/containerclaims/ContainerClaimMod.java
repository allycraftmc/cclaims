package de.tert0.containerclaims;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.CommonColors;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;

import java.util.Set;

public class ContainerClaimMod implements ModInitializer {

    public static final String MOD_ID = "cclaims";
    public static final Identifier CLAIM_DATA_ID = Identifier.fromNamespaceAndPath(MOD_ID, "claim");
    // TODO Furnaces, Shulker boxes, Crafter, Dispenser/Dropper, Trapped Chest, ...
    public static final Set<BlockEntityType<?>> SUPPORTED_BLOCK_ENTITIES = Set.of(BlockEntityTypes.CHEST, BlockEntityTypes.BARREL, BlockEntityTypes.HOPPER, BlockEntityTypes.BREWING_STAND, BlockEntityTypes.BEACON, BlockEntityTypes.SHELF);

    @Override
    public void onInitialize() {
        ClaimCommand.init();

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            ClaimAccess claimAccess = (ClaimAccess) blockEntity;
            if(blockEntity == null || !ClaimUtils.isClaimed(claimAccess)) return true;

            if(!ClaimUtils.isOwnerOrAdmin(claimAccess, player)) {
                player.sendOverlayMessage(Component.literal("This block is claimed!").withColor(CommonColors.RED));
                return false;
            }

            return true;
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            ClaimAccess claimAccess = (ClaimAccess) blockEntity;
            if(blockEntity == null || !ClaimUtils.isClaimed(claimAccess)) return;

            if(!ClaimUtils.isOwnerOrAdmin(claimAccess, player)) return;

            ClaimUtils.markUnclaimed(claimAccess, (ServerLevel) world);
        });
    }
}
