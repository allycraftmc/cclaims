package de.tert0.containerclaims;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jetbrains.annotations.Nullable;

public class DoubleChestUtils {
    public static @Nullable BlockEntity getNeighborBlockEntity(BlockPos pos, Level world, BlockState state) {
        if(!state.getBlock().equals(Blocks.CHEST) && !Blocks.COPPER_CHEST.asList().contains(state.getBlock())) return null;

        ChestType chestType = state.getValue(ChestBlock.TYPE);
        if(chestType == ChestType.SINGLE) return null;

        BlockPos otherPos = pos.relative(ChestBlock.getConnectedDirection(state)); // should work with copper chests too

        BlockEntity blockEntity = world.getBlockEntity(otherPos);
        if(blockEntity == null || !blockEntity.getType().equals(BlockEntityTypes.CHEST)) return null;

        return blockEntity;
    }

    public static @Nullable BlockEntity getNeighborBlockEntity(BlockPos pos, Level world) {
        return getNeighborBlockEntity(pos, world, world.getBlockState(pos));
    }
}
