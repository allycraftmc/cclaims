package de.tert0.containerclaims;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Uuids;

import java.util.Collection;
import java.util.UUID;

public record ClaimComponent(UUID owner, ImmutableSet<UUID> trusted) {
    public static final Codec<ClaimComponent> CODEC = RecordCodecBuilder
            .create(instance ->
                    instance
                            .group(
                                    Uuids.INT_STREAM_CODEC.fieldOf("owner").forGetter(ClaimComponent::owner),
                                    Uuids.INT_STREAM_CODEC.listOf().xmap(ImmutableSet::copyOf, ImmutableCollection::asList)
                                            .fieldOf("trusted").forGetter(ClaimComponent::trusted)
                            )
                            .apply(instance, ClaimComponent::new)
            );

    public ClaimComponent withTrusted(ImmutableSet<UUID> trusted) {
        return new ClaimComponent(this.owner, trusted);
    }

    public ClaimComponent addTrusted(Collection<UUID> newEntries) {
        return this.withTrusted(
                ImmutableSet.<UUID>builder()
                        .addAll(this.trusted)
                        .addAll(newEntries)
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
}
