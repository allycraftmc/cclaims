package de.tert0.containerclaims;

import com.google.common.collect.ImmutableSet;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

import java.util.*;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ClaimCommand {
    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralCommandNode<ServerCommandSource> commandNode = dispatcher.register(
                    literal("containerclaim")
                            .then(
                                    literal("info")
                                            .executes(ctx -> -1) // TODO
                            )
                            .then(
                                    literal("claim")
                                            .executes(ctx -> {
                                                ClaimAccess claimAccess = getFocusedClaimAccess(ctx);
                                                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

                                                if(ClaimUtils.isClaimed(claimAccess)) {
                                                    throw new SimpleCommandExceptionType(new LiteralMessage("The container is already claimed!")).create();
                                                }

                                                claimAccess.container_claims$setClaim(new ClaimComponent(player.getUuid(), ImmutableSet.of()));
                                                ctx.getSource().sendFeedback(() -> Text.of("Claimed container"), false);
                                                return 1;
                                            })
                            )
                            .then(
                                    literal("unclaim")
                                            .executes(ctx -> {
                                                ClaimAccess claimAccess = getFocusedClaimAccess(ctx);
                                                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

                                                checkForOwnedClaim(claimAccess, player);

                                                claimAccess.container_claims$setClaim(null);
                                                ctx.getSource().sendFeedback(() -> Text.of("Unclaimed container"), false);
                                                return 1;
                                            })
                            )
                            .then(literal("trust").then(argument("targets", GameProfileArgumentType.gameProfile()).executes(ctx -> {
                                ClaimAccess claimAccess = getFocusedClaimAccess(ctx);
                                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

                                checkForOwnedClaim(claimAccess, player);

                                Collection<GameProfile> targets = GameProfileArgumentType.getProfileArgument(ctx, "targets");

                                List<UUID> newEntries = new ArrayList<>();
                                for(GameProfile target : targets) {
                                    newEntries.add(target.getId());
                                    ctx.getSource().sendFeedback(() -> Text.of("Added " + target.getName() + " as trusted player"), false);
                                }
                                claimAccess.container_claims$setClaim(
                                        claimAccess.container_claims$getClaim()
                                                .addTrusted(newEntries)
                                );

                                if(targets.isEmpty()) {
                                    ctx.getSource().sendFeedback(() -> Text.of("No targets found"), false);
                                }

                                return targets.size();
                            })))
                            .then(literal("untrust").then(argument("targets", GameProfileArgumentType.gameProfile()).executes(ctx -> {
                                ClaimAccess claimAccess = getFocusedClaimAccess(ctx);

                                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

                                checkForOwnedClaim(claimAccess, player);

                                Collection<GameProfile> targets = GameProfileArgumentType.getProfileArgument(ctx, "targets");

                                List<UUID> entries = new ArrayList<>();
                                for(GameProfile target : targets) {
                                    if(ClaimUtils.isTrusted(claimAccess, target.getId())) {
                                        entries.add(target.getId());
                                        ctx.getSource().sendFeedback(() -> Text.of("Removed " + target.getName() + " as a trusted player"), false);
                                    } else {
                                        ctx.getSource().sendFeedback(() -> Text.of(target.getName() + " was not trusted"), false);
                                    }
                                }

                                claimAccess.container_claims$setClaim(
                                        claimAccess.container_claims$getClaim()
                                                .removeTrusted(entries)
                                );

                                if(entries.isEmpty()) {
                                    ctx.getSource().sendFeedback(() -> Text.of("No player to untrust found"), false);
                                }

                                return entries.size();
                            })))
                            .then(
                                    literal("adminmode")
                                            .requires(Permissions.require("cclaim.adminmode", 3))
                                            .executes(ctx -> {
                                                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                                AdminModeAccess adminModeAccess = (AdminModeAccess) player;

                                                adminModeAccess.container_claims$setAdminMode(!adminModeAccess.container_claims$getAdminMode());

                                                if(adminModeAccess.container_claims$getAdminMode()) {
                                                    ctx.getSource().sendFeedback(() -> Text.of("Enabled Container Claim Admin Mode"), true);
                                                } else {
                                                    ctx.getSource().sendFeedback(() -> Text.of("Disabled Container Claim Admin Mode"), true);
                                                }

                                                return Command.SINGLE_SUCCESS;
                                            })
                            )
            );

            // Alias
            dispatcher.register(literal("cclaim").redirect(commandNode));
        });
    }

    private static ClaimAccess getFocusedClaimAccess(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        if(!(player.raycast(5.0f, 1.0f, false) instanceof BlockHitResult result)) {
            throw new SimpleCommandExceptionType(new LiteralMessage("You have to look at a container block")).create();
        }
        BlockPos pos = result.getBlockPos();
        BlockEntity blockEntity = player.getServerWorld().getBlockEntity(pos);
        if(blockEntity == null) {
            throw new SimpleCommandExceptionType(new LiteralMessage("You have to look at a container block")).create();
        }
        if(!ContainerClaims.SUPPORTED_BLOCK_ENTITIES.contains(blockEntity.getType())) {
            throw new SimpleCommandExceptionType(new LiteralMessage("This block type is not supported")).create();
        }

        if((!(blockEntity instanceof ClaimAccess claimAccess))) {
            throw new SimpleCommandExceptionType(new LiteralMessage("Internal error. Please report this error")).create();
        }

        return claimAccess;
    }

    private static void checkForOwnedClaim(ClaimAccess claimAccess, ServerPlayerEntity player) throws CommandSyntaxException {
        if(!ClaimUtils.isClaimed(claimAccess)) {
            throw new SimpleCommandExceptionType(new LiteralMessage("The container is not claimed!")).create();
        }
        if(!ClaimUtils.isOwnerOrAdmin(claimAccess, player)) {
            throw new SimpleCommandExceptionType(new LiteralMessage("The container is not yours!")).create();
        }
    }
}
