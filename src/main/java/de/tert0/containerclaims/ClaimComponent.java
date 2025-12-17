package de.tert0.containerclaims;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public record ClaimComponent(UUID owner, Instant timestamp, ImmutableSet<@NotNull UUID> trusted, ImmutableSet<@NotNull UUID> trustedGroups) {
    public static final Codec<ClaimComponent> CODEC = RecordCodecBuilder
            .create(instance ->
                    instance
                            .group(
                                    UUIDUtil.CODEC.fieldOf("owner").forGetter(ClaimComponent::owner),
                                    Codec.LONG
                                            .xmap(Instant::ofEpochMilli, Instant::toEpochMilli)
                                            .fieldOf("timestamp").forGetter(ClaimComponent::timestamp),
                                    UUIDUtil.CODEC.listOf()
                                            .xmap(ImmutableSet::copyOf, ImmutableCollection::asList)
                                            .fieldOf("trusted").forGetter(ClaimComponent::trusted),
                                    UUIDUtil.CODEC.listOf()
                                            .xmap(ImmutableSet::copyOf, ImmutableCollection::asList)
                                            .optionalFieldOf("trusted_groups")
                                            .xmap(o -> o.orElse(ImmutableSet.of()), Optional::of) // like default value but saving the default value
                                            .forGetter(ClaimComponent::trustedGroups)
                            )
                            .apply(instance, ClaimComponent::new)
            );

    public ClaimComponent withTrusted(ImmutableSet<@NotNull UUID> trusted) {
        return new ClaimComponent(this.owner, this.timestamp, trusted, this.trustedGroups);
    }

    public ClaimComponent addTrusted(Collection<UUID> entries) {
        return this.withTrusted(
                ImmutableSet.<UUID>builder()
                        .addAll(this.trusted)
                        .addAll(entries)
                        .build()
        );
    }

    public ClaimComponent removeTrusted(Collection<UUID> entries) {
        return this.withTrusted(
                this.trusted.stream()
                        .filter(uuid -> !entries.contains(uuid))
                        .collect(ImmutableSet.toImmutableSet())
        );
    }

    public ClaimComponent withTrustedGroups(ImmutableSet<@NotNull UUID> trustedGroups) {
        return new ClaimComponent(this.owner, this.timestamp, this.trusted, trustedGroups);
    }

    public ClaimComponent addTrustedGroups(Collection<UUID> entries) {
        return this.withTrustedGroups(
                ImmutableSet.<UUID>builder()
                        .addAll(this.trustedGroups)
                        .addAll(entries)
                        .build()
        );
    }

    public ClaimComponent removeTrustedGroups(Collection<UUID> entries) {
        return this.withTrustedGroups(
                this.trustedGroups.stream()
                        .filter(uuid -> !entries.contains(uuid))
                        .collect(ImmutableSet.toImmutableSet())
        );
    }

    public ClaimComponent fixup(MinecraftServer server) {
        if(this.trustedGroups.isEmpty()) return this;
        Collection<UUID> groupUuids = GroupState.getState(server)
                .getGroups()
                .stream()
                .map(GroupComponent::uuid)
                .collect(Collectors.toSet());

        return this.withTrustedGroups(
                this.trustedGroups.stream()
                        .filter(groupUuids::contains)
                        .collect(ImmutableSet.toImmutableSet())
        );
    }
}
