package de.tert0.containerclaims;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.jetbrains.annotations.NotNull;

public class GlobalClaimState extends SavedData {
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

    private static final SavedDataType<@NotNull GlobalClaimState> STATE_TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(ContainerClaimMod.MOD_ID, "claims"),
            GlobalClaimState::createDefault,
            GlobalClaimState.CODEC,
            null
    );

    private GlobalClaimState(HashSet<BlockPos> positions) {
        this.positions = positions;
    }

    public ImmutableSet<@NotNull BlockPos> getPositions() {
        return ImmutableSet.copyOf(this.positions);
    }

    public void addPosition(BlockPos pos) {
        this.positions.add(pos);
        this.setDirty();
    }

    public void removePosition(BlockPos pos) {
        this.positions.remove(pos);
        this.setDirty();
    }

    private static GlobalClaimState createDefault() {
        return new GlobalClaimState(new HashSet<>());
    }

    public static GlobalClaimState getWorldState(ServerLevel world) {
        SavedDataStorage persistentStateManager = world.getDataStorage();

        return persistentStateManager.computeIfAbsent(STATE_TYPE);
    }
}
