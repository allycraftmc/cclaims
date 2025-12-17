package de.tert0.containerclaims;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

public record GroupComponent(UUID uuid, String name, UUID owner, ImmutableSet<@NotNull UUID> members) {
    public static final Codec<GroupComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("uuid").forGetter(GroupComponent::uuid),
            Codec.STRING.fieldOf("name").forGetter(GroupComponent::name),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(GroupComponent::owner),
            UUIDUtil.CODEC.listOf().fieldOf("members")
                    .xmap(ImmutableSet::copyOf, ImmutableCollection::asList)
                    .forGetter(GroupComponent::members)
    ).apply(instance, GroupComponent::new));

    public GroupComponent withOwner(UUID owner) {
        return new GroupComponent(
                this.uuid,
                this.name,
                owner,
                this.members
        );
    }

    public GroupComponent withMembers(ImmutableSet<@NotNull UUID> members) {
        return new GroupComponent(
                this.uuid,
                this.name,
                this.owner,
                members
        );
    }

    public GroupComponent addMembers(Collection<UUID> entries) {
        return this.withMembers(
                ImmutableSet.<UUID>builder()
                        .addAll(this.members)
                        .addAll(entries)
                        .build()
        );
    }

    public GroupComponent removeMembers(Collection<UUID> entries) {
        return this.withMembers(
                this.members.stream()
                        .filter(uuid -> !entries.contains(uuid))
                        .collect(ImmutableSet.toImmutableSet())
        );
    }

    public boolean isMember(Player player) {
        return this.isMember(player.getUUID());
    }

    public boolean isMember(UUID uuid) {
        return this.owner.equals(uuid) || this.members.contains(uuid);
    }
}
