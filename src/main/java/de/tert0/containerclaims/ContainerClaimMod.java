package de.tert0.containerclaims;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;

import java.util.Set;

public class ContainerClaimMod implements ModInitializer {

    public static final String MOD_ID = "cclaims";
    public static final Identifier CLAIM_DATA_ID = Identifier.of(MOD_ID, "claim");
    // TODO Furnaces, Shulker boxes, brewing stand, Crafter, Dispenser/Dropper, Trapped Chest, ...
    public static final Set<BlockEntityType<?>> SUPPORTED_BLOCK_ENTITIES = Set.of(BlockEntityType.CHEST, BlockEntityType.BARREL, BlockEntityType.HOPPER);

    @Override
    public void onInitialize() {
        ClaimCommand.init();

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            ClaimAccess claimAccess = (ClaimAccess) blockEntity;
            if(blockEntity == null || !ClaimUtils.isClaimed(claimAccess)) return true;

            if(!ClaimUtils.isOwnerOrAdmin(claimAccess, player)) {
                player.sendMessage(Text.literal("This block is claimed!").withColor(Colors.RED), true);
                return false;
            }

            return true;
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            ClaimAccess claimAccess = (ClaimAccess) blockEntity;
            if(blockEntity == null || !ClaimUtils.isClaimed(claimAccess)) return;

            if(!ClaimUtils.isOwnerOrAdmin(claimAccess, player)) return;

            ClaimUtils.markUnclaimed(claimAccess, (ServerWorld) world);
        });
    }
}
