package de.tert0.containerclaims;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;

public class GroupSuggestionProvider implements SuggestionProvider<ServerCommandSource> {
    private final BiPredicate<GroupComponent, ServerPlayerEntity> predicate;

    private GroupSuggestionProvider(BiPredicate<GroupComponent, ServerPlayerEntity> predicate) {
        this.predicate = predicate;
    }

    public static GroupSuggestionProvider owner() {
        return new GroupSuggestionProvider(
                (group, player) -> group.owner().equals(player.getUuid()) || Permissions.check(player, "cclaim.group.admin", 3)
        );
    }

    public static GroupSuggestionProvider member() {
        return new GroupSuggestionProvider(
                (group, player) -> group.isMember(player.getUuid()) || Permissions.check(player, "cclaim.group.admin", 3)
        );
    }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) throws CommandSyntaxException {
        GroupState groupState = GroupState.getState(ctx.getSource().getServer());

        for(GroupComponent group : groupState.getGroups()) {
            if(!this.predicate.test(group, ctx.getSource().getPlayerOrThrow())) continue;

            builder.suggest(group.name());
        }

        return builder.buildFuture();
    }
}
