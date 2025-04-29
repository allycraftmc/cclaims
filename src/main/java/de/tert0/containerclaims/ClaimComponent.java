package de.tert0.containerclaims;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Uuids;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public record ClaimComponent(UUID owner, Instant timestamp, ImmutableSet<UUID> trusted, ImmutableSet<UUID> trustedGroups) {
    public static final Codec<ClaimComponent> CODEC = RecordCodecBuilder
            .create(instance ->
                    instance
                            .group(
                                    Uuids.INT_STREAM_CODEC.fieldOf("owner").forGetter(ClaimComponent::owner),
                                    Codec.LONG
                                            .xmap(Instant::ofEpochMilli, Instant::toEpochMilli)
                                            .fieldOf("timestamp").forGetter(ClaimComponent::timestamp),
                                    Uuids.INT_STREAM_CODEC.listOf()
                                            .xmap(ImmutableSet::copyOf, ImmutableCollection::asList)
                                            .fieldOf("trusted").forGetter(ClaimComponent::trusted),
                                    Uuids.INT_STREAM_CODEC.listOf()
                                            .xmap(ImmutableSet::copyOf, ImmutableCollection::asList)
                                            .optionalFieldOf("trusted_groups")
                                            .xmap(o -> o.orElse(ImmutableSet.of()), Optional::of) // like default value but saving the default value
                                            .forGetter(ClaimComponent::trustedGroups)
                            )
                            .apply(instance, ClaimComponent::new)
            );

    public ClaimComponent withTrusted(ImmutableSet<UUID> trusted) {
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

    public ClaimComponent withTrustedGroups(ImmutableSet<UUID> trustedGroups) {
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
