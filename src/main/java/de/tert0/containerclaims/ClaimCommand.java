package de.tert0.containerclaims;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;
import net.minecraft.util.UserCache;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ClaimCommand {
    private static final SimpleCommandExceptionType NO_CONTAINER_FOCUSED = new SimpleCommandExceptionType(new LiteralMessage("You have to look at a container block"));
    private static final SimpleCommandExceptionType BLOCK_TYPE_NOT_SUPPORTED = new SimpleCommandExceptionType(new LiteralMessage("This block type is not supported"));
    private static final SimpleCommandExceptionType INTERNAL_ERROR = new SimpleCommandExceptionType(new LiteralMessage("Internal error. Please report this error"));
    private static final SimpleCommandExceptionType NOT_CLAIMED = new SimpleCommandExceptionType(new LiteralMessage("The container is not claimed!"));
    private static final SimpleCommandExceptionType NOT_OWNER = new SimpleCommandExceptionType(new LiteralMessage("The container is not yours!"));
    private static final SimpleCommandExceptionType ALREADY_CLAIMED = new SimpleCommandExceptionType(new LiteralMessage("The container is already claimed!"));

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    literal("cclaim")
                            .executes(ClaimCommand::helpCommand)
                            .then(
                                    literal("help")
                                            .executes(ClaimCommand::helpCommand)
                            )
                            .then(
                                    literal("info")
                                            .executes(ClaimCommand::infoCommand)
                            )
                            .then(
                                    literal("claim")
                                            .executes(ClaimCommand::claimCommand)
                            )
                            .then(
                                    literal("unclaim")
                                            .executes(ClaimCommand::unclaimCommand)
                            )
                            .then(
                                    literal("trust")
                                            .then(
                                                    argument("targets", GameProfileArgumentType.gameProfile())
                                                            .executes(ClaimCommand::trustCommand)
                                            )
                            )
                            .then(
                                    literal("untrust")
                                            .then(
                                                    argument("targets", GameProfileArgumentType.gameProfile())
                                                            .executes(ClaimCommand::untrustCommand)
                                            )
                            )
                            .then(
                                    literal("adminmode")
                                            .requires(Permissions.require("cclaim.adminmode", 3))
                                            .executes(ClaimCommand::adminmodeCommand)
                            )
                            .then(
                                    literal("list")
                                            .requires(Permissions.require("cclaim.list", 2))
                                            .executes(ctx -> ClaimCommand.listCommand(ctx, ctx.getSource().getPlayerOrThrow().getServerWorld()))
                                            .then(
                                                    argument("dimension", DimensionArgumentType.dimension())
                                                            .requires(Permissions.require("cclaim.list", 2))
                                                            .executes(ctx -> ClaimCommand.listCommand(ctx, DimensionArgumentType.getDimensionArgument(ctx, "dimension")))
                                            )
                            )
            );
        });
    }

    private static ClaimAccess getFocusedClaimAccess(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        if(!(player.raycast(player.getAttributeValue(EntityAttributes.BLOCK_INTERACTION_RANGE), 1.0f, false) instanceof BlockHitResult result)) {
            throw NO_CONTAINER_FOCUSED.create();
        }
        BlockPos pos = result.getBlockPos();
        BlockEntity blockEntity = player.getServerWorld().getBlockEntity(pos);
        if(blockEntity == null) {
            throw NO_CONTAINER_FOCUSED.create();
        }
        if(!ContainerClaimMod.SUPPORTED_BLOCK_ENTITIES.contains(blockEntity.getType())) {
            throw BLOCK_TYPE_NOT_SUPPORTED.create();
        }

        if((!(blockEntity instanceof ClaimAccess claimAccess))) {
            throw INTERNAL_ERROR.create();
        }

        return claimAccess;
    }

    private static void checkForOwnedClaim(ClaimAccess claimAccess, ServerPlayerEntity player) throws CommandSyntaxException {
        if(!ClaimUtils.isClaimed(claimAccess)) {
            throw NOT_CLAIMED.create();
        }
        if(!ClaimUtils.isOwnerOrAdmin(claimAccess, player)) {
            throw NOT_OWNER.create();
        }
    }

    private static int helpCommand(CommandContext<ServerCommandSource> ctx) {
        MutableText text = Text.literal("");
        text.append(Text.literal("Container Claim Mod - Help\n").withColor(Colors.CYAN));
        text.append("-".repeat(20) + "\n");
        text.append("This can be used to claim container blocks like chests or barrels.\n");
        text.append("To claim a container block, look at the block and run ");
        text.append(
                Text.literal("/cclaim claim\n")
                        .withColor(Colors.YELLOW)
                        .styled(
                                style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/cclaim claim"))
                        )
        );
        text.append(
                Text.literal("/cclaim unlaim")
                        .withColor(Colors.YELLOW)
                        .styled(
                                style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/cclaim unclaim"))
                        )
        );
        text.append(" can be used to unclaim a container\n");
        text.append("You can allow others to use a claimed container using ");
        text.append(
                Text.literal("/cclaim trust <player>")
                        .withColor(Colors.YELLOW)
                        .styled(
                                style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/cclaim trust "))
                        )
        );
        text.append(". To revoke these permissions, you can use ");
        text.append(
                Text.literal("/cclaim untrust <player>")
                        .withColor(Colors.YELLOW)
                        .styled(
                                style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/cclaim untrust "))
                        )
        );
        text.append("\n");
        text.append("To get information about a claim, you can use ");
        text.append(
                Text.literal("/cclaim info")
                        .withColor(Colors.YELLOW)
                        .styled(
                                style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/cclaim info"))
                        )
        );
        text.append(".\n");

        ctx.getSource().sendFeedback(() -> text, false);
        return Command.SINGLE_SUCCESS;
    }

    private static int infoCommand(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ClaimAccess claimAccess = getFocusedClaimAccess(ctx);
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

        MutableText text = Text.literal("");
        text.append(Text.literal("Container Claim Info\n").withColor(Colors.CYAN));
        text.append("-".repeat(20) + "\n");

        if(!ClaimUtils.isClaimed(claimAccess)) {
            text.append(Text.literal("This container is not claimed!").withColor(Colors.LIGHT_RED));
        } else if(!ClaimUtils.canUse(claimAccess, player) && !Permissions.check(player, "cclaim.info.admin", 2)) {
            text.append(Text.literal("This container is claimed!").withColor(Colors.LIGHT_YELLOW));
        } else {
            UserCache userCache = ctx.getSource().getServer().getUserCache();

            text.append("Owner: ");
            UUID ownerUuid = claimAccess.cclaims$getClaim().owner();
            text.append(Text.literal(
                    Optional.ofNullable(userCache)
                            .flatMap(uc -> uc.getByUuid(ownerUuid))
                            .map(GameProfile::getName)
                            .orElse(ownerUuid.toString())
            ).withColor(Colors.GREEN));
            text.append("\n");
            text.append("Trusted: ");

            Collection<UUID> trustedUuids = claimAccess.cclaims$getClaim().trusted();
            if(trustedUuids.isEmpty()) {
                text.append(Text.literal("¯\\_(ツ)_/¯").withColor(Colors.YELLOW));
            } else {
                for(UUID trustedUuid : trustedUuids) {
                    text.append(Text.of("\n  - "));
                    text.append(Text.literal(
                            Optional.ofNullable(userCache)
                                    .flatMap(uc -> uc.getByUuid(trustedUuid))
                                    .map(GameProfile::getName)
                                    .orElse(trustedUuid.toString())
                    ).withColor(Colors.LIGHT_YELLOW));
                }
            }

            if(Permissions.check(player, "cclaim.info.admin", 2)) {
                String formattedTimestamp = DateTimeFormatter.ISO_DATE_TIME
                        .withZone(ZoneOffset.UTC)
                        .format(claimAccess.cclaims$getClaim().timestamp());

                text.append("\n");
                text.append("Timestamp: " + formattedTimestamp);
            }
        }

        ctx.getSource().sendFeedback(() -> text, false);
        return Command.SINGLE_SUCCESS;
    }

    private static int claimCommand(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ClaimAccess claimAccess = getFocusedClaimAccess(ctx);
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

        if(ClaimUtils.isClaimed(claimAccess)) {
            throw ALREADY_CLAIMED.create();
        }

        ClaimUtils.claim(claimAccess, player.getUuid(), player.getServerWorld());
        ctx.getSource().sendFeedback(() -> Text.of("Claimed container"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int unclaimCommand(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ClaimAccess claimAccess = getFocusedClaimAccess(ctx);
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        checkForOwnedClaim(claimAccess, player);

        ClaimUtils.unclaim(claimAccess, player.getServerWorld());
        ctx.getSource().sendFeedback(() -> Text.of("Unclaimed container"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int trustCommand(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ClaimAccess claimAccess = getFocusedClaimAccess(ctx);
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        checkForOwnedClaim(claimAccess, player);

        Collection<GameProfile> targets = GameProfileArgumentType.getProfileArgument(ctx, "targets");

        List<UUID> entries = new ArrayList<>();
        for(GameProfile target : targets) {
            entries.add(target.getId());
            ctx.getSource().sendFeedback(() -> Text.of("Added " + target.getName() + " as trusted player"), false);
        }
        ClaimUtils.trust(claimAccess, entries);

        if(targets.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.of("No targets found"), false);
        }

        return targets.size();
    }

    private static int untrustCommand(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
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
        ClaimUtils.untrust(claimAccess, entries);

        if(entries.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.of("No player to untrust found"), false);
        }

        return entries.size();
    }

    private static int adminmodeCommand(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        AdminModeAccess adminModeAccess = (AdminModeAccess) player;

        adminModeAccess.cclaims$setAdminMode(!adminModeAccess.cclaims$getAdminMode());

        if(adminModeAccess.cclaims$getAdminMode()) {
            ctx.getSource().sendFeedback(() -> Text.of("Enabled Container Claim Admin Mode"), true);
        } else {
            ctx.getSource().sendFeedback(() -> Text.of("Disabled Container Claim Admin Mode"), true);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int listCommand(CommandContext<ServerCommandSource> ctx, ServerWorld serverWorld) throws CommandSyntaxException {
        Collection<BlockPos> positions = GlobalClaimState.getWorldState(serverWorld).getPositions();
        String dimensionName = serverWorld.getDimensionEntry().getKey()
                .map(RegistryKey::getValue)
                .map(Identifier::getPath)
                .orElse("[unknown]");

        MutableText text = Text.literal("");
        text.append(
                Text.literal("Container Claim List - " + dimensionName + "\n")
                        .withColor(Colors.CYAN)
        );
        text.append("-".repeat(20) + "\n");
        text.append(
                Text.literal("Note: This list is not necessarily complete and can be wrong")
                        .withColor(Colors.YELLOW)
        );

        if(positions.isEmpty()) {
            text.append(
                    Text.literal("\nNo registered claims")
                            .withColor(Colors.LIGHT_RED)
            );
        } else {
            for(BlockPos pos : positions) {
                String formattedPosition = pos.getX() + " " + pos.getY() + " " + pos.getZ();
                text.append(Text.of("\n  - "));
                text.append(
                        Text.literal(formattedPosition)
                                .styled(
                                        style -> style
                                                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp " + formattedPosition))
                                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of("Click to teleport")))
                                                .withColor(Colors.GREEN)
                                )
                );
            }
        }

        ctx.getSource().sendFeedback(() -> text, false);

        return Command.SINGLE_SUCCESS;
    }
}
