package de.tert0.containerclaims;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;

import java.util.*;

public class GlobalClaimState extends PersistentState {
    private final HashSet<BlockPos> positions;

    private static final Codec<GlobalClaimState> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    BlockPos.CODEC
                            .listOf()
                            .fieldOf("positions")
                            .xmap(HashSet::new, ArrayList::new)
                            .forGetter(globalClaimState -> globalClaimState.positions)
            ).apply(instance, GlobalClaimState::new)
    );

    private static final PersistentStateType<GlobalClaimState> STATE_TYPE = new PersistentStateType<>(
            ContainerClaimMod.MOD_ID,
            GlobalClaimState::createDefault,
            GlobalClaimState.CODEC,
            null
    );

    private GlobalClaimState(HashSet<BlockPos> positions) {
        this.positions = positions;
    }

    public ImmutableSet<BlockPos> getPositions() {
        return ImmutableSet.copyOf(this.positions);
    }

    public void addPosition(BlockPos pos) {
        this.positions.add(pos);
        this.markDirty();
    }

    public void removePosition(BlockPos pos) {
        this.positions.remove(pos);
        this.markDirty();
    }

    private static GlobalClaimState createDefault() {
        return new GlobalClaimState(new HashSet<>());
    }

    public static GlobalClaimState getWorldState(ServerWorld world) {
        PersistentStateManager persistentStateManager = world.getPersistentStateManager();

        return persistentStateManager.getOrCreate(STATE_TYPE);
    }
}
