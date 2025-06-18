package de.tert0.containerclaims;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;

import java.util.ArrayList;

public class GroupState extends PersistentState {
    private static final Codec<GroupState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GroupComponent.CODEC
                    .listOf()
                    .fieldOf("groups")
                    .xmap(ArrayList::new, ImmutableList::copyOf)
                    .forGetter(groupState -> groupState.groups)
    ).apply(instance, GroupState::new));

    private static final PersistentStateType<GroupState> STATE_TYPE = new PersistentStateType<>(
            ContainerClaimMod.MOD_ID + "_groups",
            GroupState::createDefault,
            GroupState.CODEC,
            null
    );

    private final ArrayList<GroupComponent> groups;

    private GroupState(ArrayList<GroupComponent> groups) {
        this.groups = groups;
    }

    private static GroupState createDefault() {
        return new GroupState(new ArrayList<>());
    }

    public static GroupState getState(MinecraftServer server) {
        ServerWorld serverWorld = server.getWorld(World.OVERWORLD);
        if(serverWorld == null) {
           throw new IllegalStateException("Overworld has to be available for this mod to work");
        }
        return serverWorld.getPersistentStateManager().getOrCreate(STATE_TYPE);
    }

    public ImmutableList<GroupComponent> getGroups() {
        return ImmutableList.copyOf(this.groups);
    }

    public void addGroup(GroupComponent group) {
        this.groups.add(group);
        this.markDirty();
    }

    public void removeGroup(GroupComponent group) {
        this.groups.remove(group);
        this.markDirty();
    }

    public void modifyGroup(GroupComponent group) {
        this.groups.removeIf(g -> g.uuid().equals(group.uuid()));
        this.groups.add(group);
        this.markDirty();
    }
}
