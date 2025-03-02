package de.tert0.containerclaims;

import com.google.common.collect.ImmutableSet;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import org.slf4j.Logger;

import java.util.*;

public class GlobalClaimState extends PersistentState {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Set<BlockPos> positions;

    private static final Type<GlobalClaimState> STATE_TYPE = new Type<>(
            GlobalClaimState::createDefault,
            GlobalClaimState::createFromNbt,
            null
    );

    private static final Codec<Set<BlockPos>> CODEC_POSITIONS = BlockPos.CODEC.listOf()
            .xmap(HashSet::new, ArrayList::new);

    private GlobalClaimState(Set<BlockPos> positions) {
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

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtElement nbtPositions = CODEC_POSITIONS
                .encodeStart(NbtOps.INSTANCE, this.positions)
                .resultOrPartial(LOGGER::error)
                .orElseThrow();

        nbt.put("positions", nbtPositions);
        return nbt;
    }

    private static GlobalClaimState createFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        return new GlobalClaimState(
                CODEC_POSITIONS.parse(NbtOps.INSTANCE, nbt.get("positions"))
                        .resultOrPartial(LOGGER::error)
                        .orElseThrow()
        );
    }

    private static GlobalClaimState createDefault() {
        return new GlobalClaimState(new HashSet<>());
    }

    public static GlobalClaimState getWorldState(ServerWorld world) {
        PersistentStateManager persistentStateManager = world.getPersistentStateManager();

        return persistentStateManager.getOrCreate(STATE_TYPE, ContainerClaimMod.MOD_ID);
    }
}
