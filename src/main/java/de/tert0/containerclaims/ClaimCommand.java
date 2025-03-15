package de.tert0.containerclaims;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.entity.attribute.EntityAttributes;
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
    private static final SimpleCommandExceptionType PAGE_OUT_OF_BOUNDS = new SimpleCommandExceptionType(new LiteralMessage("Page out of bounds"));
    private static final SimpleCommandExceptionType MISSING_DIMENSION_REGISTRY_KEY = new SimpleCommandExceptionType(new LiteralMessage("Internal Error. Dimension has no Registry Key"));

    private static final int LIST_PAGE_SIZE = 8;

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
                                            .executes(ctx -> ClaimCommand.listCommand(ctx.getSource(), ctx.getSource().getPlayerOrThrow().getServerWorld(), 1))
                                            .then(
                                                    argument("dimension", DimensionArgumentType.dimension())
                                                            .executes(ctx -> ClaimCommand.listCommand(ctx.getSource(), DimensionArgumentType.getDimensionArgument(ctx, "dimension"), 1))
                                                            .then(
                                                                    argument("page", IntegerArgumentType.integer(1))
                                                                            .executes(
                                                                                    ctx ->
                                                                                            ClaimCommand.listCommand(ctx.getSource(), DimensionArgumentType.getDimensionArgument(ctx, "dimension"), IntegerArgumentType.getInteger(ctx, "page"))
                                                                            )
                                                            )
                                                            .then(
                                                                    literal("all")
                                                                            .executes(
                                                                                    ctx ->
                                                                                            ClaimCommand.listCommand(ctx.getSource(), DimensionArgumentType.getDimensionArgument(ctx, "dimension"), -1)
                                                                            )
                                                            )
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

    private static int listCommand(ServerCommandSource source, ServerWorld serverWorld, int page) throws CommandSyntaxException {
        List<BlockPos> positions = GlobalClaimState.getWorldState(serverWorld).getPositions()
                .stream()
                .sorted() // TODO maybe some kind of 3d spiral around the origin
                .toList();
        Identifier dimension = serverWorld.getDimensionEntry().getKey()
                .orElseThrow(MISSING_DIMENSION_REGISTRY_KEY::create)
                .getValue();
        String dimensionName = dimension.getPath();

        int totalPageCount = Math.ceilDiv(positions.size(), LIST_PAGE_SIZE);

        MutableText text = Text.literal("");
        text.append(
                Text.literal("--- Container Claims - " + dimensionName+ " (" + positions.size() + ") ---")
                        .withColor(Colors.CYAN)
        );

        if(positions.isEmpty()) {
            text.append(
                    Text.literal("\nNo registered claims")
                            .withColor(Colors.LIGHT_RED)
            );
        } else {
            if(page > totalPageCount) {
                throw PAGE_OUT_OF_BOUNDS.create();
            }
            if(page != -1) {
                positions = positions.subList((page - 1) * LIST_PAGE_SIZE, Math.min(page * LIST_PAGE_SIZE, positions.size()));
            }

            for(BlockPos pos : positions) {
                Optional<Text> extraText = Optional.empty();
                if(serverWorld.isPosLoaded(pos)) {
                    ClaimAccess claimAccess = (ClaimAccess) serverWorld.getBlockEntity(pos);
                    if(claimAccess != null) {
                        UUID ownerUuid = claimAccess.cclaims$getClaim().owner();
                        List<String> trustedNames = claimAccess.cclaims$getClaim().trusted().stream()
                                .map(uuid -> serverWorld.getServer().getUserCache() != null ? serverWorld.getServer().getUserCache().getByUuid(uuid).map(GameProfile::getName).orElse(null) : null)
                                .filter(Objects::nonNull)
                                .toList();
                        extraText = Optional.ofNullable(serverWorld.getServer().getUserCache())
                                .flatMap(userCache -> userCache.getByUuid(ownerUuid))
                                .map(GameProfile::getName)
                                .map(name ->
                                        Text.literal(" - " + name)
                                                .withColor(Colors.YELLOW)
                                                .styled(
                                                        style ->
                                                                !trustedNames.isEmpty() ? style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of(String.join("\n", trustedNames)))) : style
                                                )
                                );
                    }
                }

                String formattedPosition = pos.getX() + " " + pos.getY() + " " + pos.getZ();
                text.append(Text.of("\n  - "));
                text.append(
                        Text.literal(formattedPosition)
                                .styled(
                                        style -> style
                                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + formattedPosition))
                                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of("Click to teleport")))
                                                .withColor(Colors.GREEN)
                                )
                );
                text.append(
                        Text.literal(" (Copy)")
                                .withColor(Colors.LIGHT_GRAY)
                                .styled(style -> style
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, formattedPosition))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of("Click to copy")))
                                )
                );
                extraText.ifPresent(text::append);
            }

            if(page != -1) {
                Text btnPrev = (page > 1) ? Text.literal("<<")
                        .styled(style -> style
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cclaim list " + dimension + " " + (page - 1)))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of("Previous Page")))
                        ) : Text.literal("<<");
                Text btnNext = (page + 1 <= totalPageCount) ? Text.literal(">>")
                        .styled(style -> style
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cclaim list " + dimension + " " + (page + 1)))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of("Next Page")))
                        ) : Text.literal(">>");
                text.append(
                        Text.literal("\n----- ")
                                .withColor(Colors.CYAN)
                                .append(btnPrev)
                                .append(Text.of(" Page " + page + " of " + totalPageCount + " "))
                                .append(btnNext)
                                .append(" -----")
                );
            } else {
                text.append(Text.literal("\n" + "-".repeat(30)).withColor(Colors.CYAN));
            }
        }

        source.sendFeedback(() -> text, false);

        return Command.SINGLE_SUCCESS;
    }
}
