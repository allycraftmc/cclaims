package de.tert0.containerclaims;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;

public record GroupSuggestionProvider(
        BiPredicate<GroupComponent, ServerPlayer> predicate) implements SuggestionProvider<CommandSourceStack> {

    public static GroupSuggestionProvider owner() {
        return new GroupSuggestionProvider(
                (group, player) -> group.owner().equals(player.getUUID()) || Permissions.check(player, "cclaim.group.admin", PermissionLevel.ADMINS)
        );
    }

    public static GroupSuggestionProvider member() {
        return new GroupSuggestionProvider(
                (group, player) -> group.isMember(player.getUUID()) || Permissions.check(player, "cclaim.group.admin", PermissionLevel.ADMINS)
        );
    }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) throws CommandSyntaxException {
        GroupState groupState = GroupState.getState(ctx.getSource().getServer());

        for (GroupComponent group : groupState.getGroups()) {
            if (!this.predicate.test(group, ctx.getSource().getPlayerOrException())) continue;

            builder.suggest(group.name());
        }

        return builder.buildFuture();
    }
}
