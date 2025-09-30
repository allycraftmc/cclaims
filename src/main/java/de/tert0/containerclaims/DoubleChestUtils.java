package de.tert0.containerclaims;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.enums.ChestType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class DoubleChestUtils {
    public static @Nullable BlockEntity getNeighborBlockEntity(BlockPos pos, World world, BlockState state) {
        if(!state.getBlock().equals(Blocks.CHEST) && !state.getBlock().equals(Blocks.COPPER_CHEST)) return null;

        ChestType chestType = state.get(ChestBlock.CHEST_TYPE);
        if(chestType == ChestType.SINGLE) return null;

        BlockPos otherPos = pos.offset(ChestBlock.getFacing(state)); // should work with copper chests too

        BlockEntity blockEntity = world.getBlockEntity(otherPos);
        if(blockEntity == null || !blockEntity.getType().equals(BlockEntityType.CHEST)) return null;

        return blockEntity;
    }

    public static @Nullable BlockEntity getNeighborBlockEntity(BlockPos pos, World world) {
        return getNeighborBlockEntity(pos, world, world.getBlockState(pos));
    }
}
