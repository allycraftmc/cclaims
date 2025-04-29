package de.tert0.containerclaims;

import com.google.common.collect.ImmutableSet;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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

    private static final SimpleCommandExceptionType GROUP_ALREADY_EXISTS = new SimpleCommandExceptionType(new LiteralMessage("This group already exists"));
    private static final SimpleCommandExceptionType GROUP_NAME_INVALID_CHARACTER = new SimpleCommandExceptionType(new LiteralMessage("Group names have to only contain lowercase letter, numbers and underscores"));
    private static final SimpleCommandExceptionType GROUP_NAME_INVALID_LENGTH = new SimpleCommandExceptionType(new LiteralMessage("Group names have to be between 3 and 16 characters long"));
    private static final SimpleCommandExceptionType GROUP_DOES_NOT_EXIST = new SimpleCommandExceptionType(new LiteralMessage("This group does not exist"));
    private static final DynamicCommandExceptionType GROUP_LIMIT_REACHED = new DynamicCommandExceptionType(
            limit -> new LiteralMessage("You are not allowed to create more than " + limit + " groups")
    );
    private static final SimpleCommandExceptionType GROUP_NOT_TRUSTED = new SimpleCommandExceptionType(new LiteralMessage("The group is not trusted"));
    private static final SimpleCommandExceptionType ALREADY_GROUP_OWNER = new SimpleCommandExceptionType(new LiteralMessage("The player is already the owner of the group"));


    private static final SimpleCommandExceptionType PERMISSION_DENIED = new SimpleCommandExceptionType(new LiteralMessage("Permission denied"));

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
                                            .then(
                                                    literal("group")
                                                            .then(
                                                                    argument("group", StringArgumentType.word())
                                                                            .suggests(GroupSuggestionProvider.member())
                                                                            .executes(ClaimCommand::trustGroupCommand)
                                                            )
                                            )
                            )
                            .then(
                                    literal("untrust")
                                            .then(
                                                    argument("targets", GameProfileArgumentType.gameProfile())
                                                            .executes(ClaimCommand::untrustCommand)
                                            )
                                            .then(
                                                    literal("group")
                                                            .then(
                                                                    argument("group", StringArgumentType.word())
                                                                            .suggests(GroupSuggestionProvider.member())
                                                                            .executes(ClaimCommand::untrustGroupCommand)
                                                            )
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
                                            .executes(ctx -> ClaimCommand.listCommand(ctx.getSource(), ctx.getSource().getPlayerOrThrow().getWorld(), 1))
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
                            .then(
                                    literal("debug")
                                            .requires(Permissions.require("cclaim.debug", 4))
                                            .then(
                                                    literal("verify")
                                                            .requires(Permissions.require("cclaim.debug.verify", 4))
                                                            .executes(ctx -> ClaimCommand.verifyCommand(ctx, ctx.getSource().getPlayerOrThrow().getWorld(), false))
                                                            .then(
                                                                    argument("dimension", DimensionArgumentType.dimension())
                                                                            .executes(ctx -> ClaimCommand.verifyCommand(ctx, DimensionArgumentType.getDimensionArgument(ctx, "dimension"), false))
                                                                            .then(
                                                                                    literal("load")
                                                                                            .executes(ctx -> {
                                                                                                ctx.getSource().sendFeedback(() -> Text.literal("WARNING: This will load all chunks with registered claims in them. Be careful, especially in production!").withColor(Colors.YELLOW), false);
                                                                                                ctx.getSource().sendFeedback(() -> Text.literal("To confirm that you want to do this, run /cclaim debug verify <dimension> load confirm"), false);
                                                                                                return 0;
                                                                                            })
                                                                                            .then(
                                                                                                    literal("confirm")
                                                                                                            .executes(ctx -> ClaimCommand.verifyCommand(ctx, DimensionArgumentType.getDimensionArgument(ctx, "dimension"), true))
                                                                                            )
                                                                            )
                                                            )
                                            )
                            )
                            .then(
                                    literal("group")
                                            .then(
                                                    literal("create")
                                                            .then(
                                                                    argument("group", StringArgumentType.word())
                                                                            .requires(Permissions.require("cclaim.group.create", true))
                                                                            .executes(ClaimCommand::groupCreateCommand)
                                                            )
                                            )
                                            .then(
                                                    literal("delete")
                                                            .then(
                                                                    argument("group", StringArgumentType.word())
                                                                            .suggests(GroupSuggestionProvider.owner())
                                                                            .executes(ClaimCommand::groupDeleteCommand)
                                                            )
                                            )
                                            .then(
                                                    literal("info")
                                                            .then(
                                                                    argument("group", StringArgumentType.word())
                                                                            .suggests(GroupSuggestionProvider.member())
                                                                            .executes(ClaimCommand::groupInfoCommand)
                                                            )
                                            )
                                            .then(
                                                    literal("list")
                                                            .executes(ClaimCommand::groupListCommand)
                                            )
                                            .then(
                                                    literal("add")
                                                            .then(
                                                                    argument("group", StringArgumentType.word())
                                                                            .suggests(GroupSuggestionProvider.owner())
                                                                            .then(
                                                                                    argument("targets", GameProfileArgumentType.gameProfile())
                                                                                            .executes(ClaimCommand::groupAddMemberCommand)
                                                                            )
                                                            )
                                            )
                                            .then(
                                                    literal("remove")
                                                            .then(
                                                                    argument("group", StringArgumentType.word())
                                                                            .suggests(GroupSuggestionProvider.owner())
                                                                            .then(
                                                                                    argument("targets", GameProfileArgumentType.gameProfile())
                                                                                            .executes(ClaimCommand::groupRemoveMemberCommand)
                                                                            )
                                                            )
                                            )
                                            .then(
                                                    literal("transfer")
                                                            .requires(Permissions.require("cclaim.group.transfer", 3))
                                                            .then(
                                                                    argument("group", StringArgumentType.word())
                                                                            .suggests(GroupSuggestionProvider.owner())
                                                                            .then(
                                                                                    argument("player", EntityArgumentType.player())
                                                                                            .executes(ClaimCommand::groupTransferCommand)
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
        BlockEntity blockEntity = player.getWorld().getBlockEntity(pos);
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
                                style -> style.withClickEvent(new ClickEvent.SuggestCommand("/cclaim claim"))
                        )
        );
        text.append(
                Text.literal("/cclaim unlaim")
                        .withColor(Colors.YELLOW)
                        .styled(
                                style -> style.withClickEvent(new ClickEvent.SuggestCommand("/cclaim unclaim"))
                        )
        );
        text.append(" can be used to unclaim a container\n");
        text.append("You can allow others to use a claimed container using ");
        text.append(
                Text.literal("/cclaim trust <player>")
                        .withColor(Colors.YELLOW)
                        .styled(
                                style -> style.withClickEvent(new ClickEvent.SuggestCommand("/cclaim trust "))
                        )
        );
        text.append(". To revoke these permissions, you can use ");
        text.append(
                Text.literal("/cclaim untrust <player>")
                        .withColor(Colors.YELLOW)
                        .styled(
                                style -> style.withClickEvent(new ClickEvent.SuggestCommand("/cclaim untrust "))
                        )
        );
        text.append("\n");
        text.append("To get information about a claim, you can use ");
        text.append(
                Text.literal("/cclaim info")
                        .withColor(Colors.YELLOW)
                        .styled(
                                style -> style.withClickEvent(new ClickEvent.SuggestCommand("/cclaim info"))
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
            text.append("Owner: ");
            text.append(
                    Text.literal(getPlayerNameOrUuid(claimAccess.cclaims$getClaim().owner(), ctx.getSource().getServer()))
                            .withColor(Colors.GREEN)
            );
            text.append("\n");
            text.append("Trusted: ");

            Collection<UUID> trustedUuids = claimAccess.cclaims$getClaim().trusted();
            if(trustedUuids.isEmpty()) {
                text.append(Text.literal("¯\\_(ツ)_/¯").withColor(Colors.YELLOW));
            } else {
                for(UUID trustedUuid : trustedUuids) {
                    text.append(Text.of("\n  - "));
                    text.append(
                            Text.literal(getPlayerNameOrUuid(trustedUuid, ctx.getSource().getServer()))
                                    .withColor(Colors.LIGHT_YELLOW)
                    );
                }
            }

            text.append("\n");
            text.append("Trusted Groups: ");

            GroupState groupState = GroupState.getState(ctx.getSource().getServer());
            Collection<UUID> trustedGroupUUIDs = claimAccess.cclaims$getClaim().trustedGroups();

            if(trustedGroupUUIDs.isEmpty()) {
                text.append(Text.literal("¯\\_(ツ)_/¯").withColor(Colors.YELLOW));
            } else {
                for(UUID trustedGroupUuid : trustedGroupUUIDs) {
                    text.append(Text.of("\n  - "));
                    text.append(
                            groupState.getGroups().stream()
                                    .filter(g -> g.uuid().equals(trustedGroupUuid))
                                    .findFirst()
                                    .map(group ->
                                            Text.literal(group.name())
                                                    .withColor(Colors.LIGHT_YELLOW)
                                                    .styled(style -> style.withHoverEvent(new HoverEvent.ShowText(Text.of(
                                                            getPlayerNameOrUuid(group.owner(), ctx.getSource().getServer()) + " (" + group.members().size() + ")"
                                                    ))))
                                    )
                                    .orElse(
                                            Text.literal(trustedGroupUuid.toString())
                                                    .withColor(Colors.RED)
                                                    .styled(style -> style
                                                            .withHoverEvent(new HoverEvent.ShowText(Text.of("Group does not exist")))
                                                    )
                                    )
                    );
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

        ClaimUtils.claim(claimAccess, player.getUuid(), player.getWorld());
        ctx.getSource().sendFeedback(() -> Text.of("Claimed container"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int unclaimCommand(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ClaimAccess claimAccess = getFocusedClaimAccess(ctx);
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        checkForOwnedClaim(claimAccess, player);

        ClaimUtils.unclaim(claimAccess, player.getWorld());
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
            ctx.getSource().sendFeedback(() -> Text.of("Added " + target.getName() + " as a trusted player"), false);
        }
        ClaimUtils.trust(claimAccess, entries);

        if(targets.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.of("No targets found"), false);
        }

        return targets.size();
    }

    private static int trustGroupCommand(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ClaimAccess claimAccess = getFocusedClaimAccess(ctx);
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        checkForOwnedClaim(claimAccess, player);

        String groupName = StringArgumentType.getString(ctx, "group");
        GroupState groupState = GroupState.getState(ctx.getSource().getServer());
        GroupComponent group = getGroup(groupState, groupName);

        ClaimUtils.trustGroup(claimAccess, group);

        ctx.getSource().sendFeedback(() -> Text.literal("Added " + group.name() + " as a trusted group"), false);

        return Command.SINGLE_SUCCESS;
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

    private static int untrustGroupCommand(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ClaimAccess claimAccess = getFocusedClaimAccess(ctx);
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        checkForOwnedClaim(claimAccess, player);

        String groupName = StringArgumentType.getString(ctx, "group");
        GroupState groupState = GroupState.getState(ctx.getSource().getServer());
        GroupComponent group = getGroup(groupState, groupName);

        if(!claimAccess.cclaims$getClaim().trustedGroups().contains(group.uuid())) {
            throw GROUP_NOT_TRUSTED.create();
        }

        ClaimUtils.untrustGroup(claimAccess, group);

        ctx.getSource().sendFeedback(() -> Text.literal("Removed " + group.name() + " as a trusted group"), false);

        return Command.SINGLE_SUCCESS;
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

    private static MutableText getTextOfBlockPos(BlockPos pos, boolean copyable) {
        String formattedPos = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        MutableText text = Text.literal(formattedPos)
                .styled(
                        style -> style
                                .withHoverEvent(new HoverEvent.ShowText(Text.of("Click to teleport")))
                                .withClickEvent(new ClickEvent.RunCommand("/tp " + formattedPos))
                );

        if(copyable) {
            text.append(
                    Text.literal(" (Copy)")
                            .withColor(Colors.LIGHT_GRAY)
                            .styled(
                                    style -> style
                                            .withHoverEvent(new HoverEvent.ShowText(Text.of("Click to copy")))
                                            .withClickEvent(new ClickEvent.CopyToClipboard(formattedPos))
                            )
            );
        }

        return text;
    }

    @NotNull
    private static String getPlayerNameOrUuid(UUID uuid, MinecraftServer server) {
        return Optional.ofNullable(server.getUserCache())
                .flatMap(userCache -> userCache.getByUuid(uuid))
                .map(GameProfile::getName)
                .orElse(uuid.toString());
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
                        List<String> trustedNames = claimAccess.cclaims$getClaim().trusted().stream()
                                .map(uuid -> getPlayerNameOrUuid(uuid, source.getServer()))
                                .toList();

                        String ownerName = getPlayerNameOrUuid(claimAccess.cclaims$getClaim().owner(), source.getServer());
                        extraText = Optional.of(
                                Text.literal(" - " + ownerName)
                                        .withColor(Colors.YELLOW)
                                        .styled(style -> trustedNames.isEmpty() ? style :style.withHoverEvent(
                                                new HoverEvent.ShowText(Text.of(String.join("\n", trustedNames)))
                                        ))
                        );
                    }
                }

                text.append(Text.of("\n  - "));
                text.append(getTextOfBlockPos(pos, true).copy().withColor(Colors.GREEN));
                extraText.ifPresent(text::append);
            }

            if(page != -1) {
                Text btnPrev = (page > 1) ? Text.literal("<<")
                        .styled(style -> style
                                .withClickEvent(new ClickEvent.RunCommand("/cclaim list " + dimension + " " + (page - 1)))
                                .withHoverEvent(new HoverEvent.ShowText(Text.of("Previous Page")))
                        ) : Text.literal("<<");
                Text btnNext = (page + 1 <= totalPageCount) ? Text.literal(">>")
                        .styled(style -> style
                                .withClickEvent(new ClickEvent.RunCommand("/cclaim list " + dimension + " " + (page + 1)))
                                .withHoverEvent(new HoverEvent.ShowText(Text.of("Next Page")))
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

    private static int verifyCommand(CommandContext<ServerCommandSource> ctx, ServerWorld serverWorld, boolean loadChunks) {
        Set<BlockPos> allPositions = GlobalClaimState.getWorldState(serverWorld).getPositions();

        Set<BlockPos> loadedPositions = allPositions.stream()
                .filter(pos -> serverWorld.isPosLoaded(pos) || loadChunks)
                .collect(Collectors.toSet());

        List<Pair<BlockPos, Text>> problems = new ArrayList<>();
        for(BlockPos pos : loadedPositions) {
            ClaimAccess claimAccess = (ClaimAccess) serverWorld.getBlockEntity(pos);

            // Check Claim exists
            if(claimAccess == null || !ClaimUtils.isClaimed(claimAccess)) {
                problems.add(new Pair<>(pos, Text.literal("Claim not found").withColor(Colors.RED)));
                continue;
            }

            // Check Double Chests
            ClaimAccess otherClaimAccess = (ClaimAccess) DoubleChestUtils.getNeighborBlockEntity(pos, serverWorld);
            if(otherClaimAccess != null) {
                if(!ClaimUtils.isClaimed(otherClaimAccess)) {
                    problems.add(new Pair<>(pos, Text.literal("Double Chest not fully claimed").withColor(Colors.YELLOW)));
                    continue;
                }

                ClaimComponent claim = claimAccess.cclaims$getClaim();
                ClaimComponent otherClaim = otherClaimAccess.cclaims$getClaim();

                if(!claim.owner().equals(otherClaim.owner())) {
                    problems.add(new Pair<>(pos, Text.literal("Double Chest owners do not match").withColor(Colors.YELLOW)));
                    continue;
                }

                if(!claim.trusted().equals(otherClaim.trusted())) {
                    problems.add(new Pair<>(pos, Text.literal("Double Chest trusted players do not match").withColor(Colors.YELLOW)));
                    continue;
                }
            }
        }

        ctx.getSource().sendFeedback(() -> Text.of("Checked " + loadedPositions.size() + "/" + allPositions.size() + " positions"), false);

        if(problems.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("No problems found").withColor(Colors.GREEN), false);
        } else {
            ctx.getSource().sendFeedback(() -> Text.literal(problems.size() + " problems found").withColor(Colors.RED), false);
            for(Pair<BlockPos, Text> entry : problems) {
                ctx.getSource().sendFeedback(
                        () -> Text.literal("- ").append(getTextOfBlockPos(entry.getLeft(), false).withColor(Colors.GREEN)).append(": ").append(entry.getRight()),
                        false
                );
            }
        }


        return Command.SINGLE_SUCCESS;
    }

    private static int groupCreateCommand(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        String groupName = StringArgumentType.getString(ctx, "group");

        GroupState groupState = GroupState.getState(ctx.getSource().getServer());

        if (groupState.getGroups().stream().anyMatch(g -> g.name().equals(groupName))) {
            throw GROUP_ALREADY_EXISTS.create();
        }

        long currentCount = groupState.getGroups().stream()
                .filter(g -> g.owner().equals(player.getUuid()))
                .count();

        if(currentCount >= 2 && !Permissions.check(player, "cclaim.group.admin", 3)) { // TODO make configurable
            throw GROUP_LIMIT_REACHED.create(2);
        }

        if (!groupName.chars().allMatch(raw -> {
            char c = (char) raw;
            return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
        })) {
            throw GROUP_NAME_INVALID_CHARACTER.create();
        }

        if (groupName.length() > 16 || groupName.length() < 3) {
            throw GROUP_NAME_INVALID_LENGTH.create();
        }

        GroupComponent group = new GroupComponent(
                UUID.randomUUID(),
                groupName,
                player.getUuid(),
                ImmutableSet.of()
        );

        groupState.addGroup(group);

        ctx.getSource().sendFeedback(() -> Text.literal("Successfully created group!").withColor(Colors.GREEN), false);

        return Command.SINGLE_SUCCESS;
    }

    private static GroupComponent getGroup(GroupState groupState, String groupName) throws CommandSyntaxException {
        return groupState.getGroups()
                .stream()
                .filter(g -> g.name().equals(groupName))
                .findFirst()
                .orElseThrow(GROUP_DOES_NOT_EXIST::create);
    }

    private static int groupDeleteCommand(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

        String groupName = StringArgumentType.getString(ctx, "group");
        GroupState groupState = GroupState.getState(ctx.getSource().getServer());
        GroupComponent group = getGroup(groupState, groupName);

        if(!group.owner().equals(player.getUuid()) && !Permissions.check(player, "cclaim.group.admin", 3)) {
            throw PERMISSION_DENIED.create();
        }

        groupState.removeGroup(group);

        ctx.getSource().sendFeedback(() -> Text.literal("Successfully removed group").withColor(Colors.GREEN), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int groupInfoCommand(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

        String groupName = StringArgumentType.getString(ctx, "group");
        GroupState groupState = GroupState.getState(ctx.getSource().getServer());
        GroupComponent group = getGroup(groupState, groupName);

        if(!group.isMember(player) && !Permissions.check(player, "cclaim.group.admin", 3)) {
            throw PERMISSION_DENIED.create();
        }

        MutableText text = Text.literal("");

        text.append(Text.literal("Container Claim Group Info\n").withColor(Colors.CYAN));
        text.append("-".repeat(20) + "\n");

        text.append("Name: ");
        text.append(
                Text.literal(group.name() + "\n")
                        .withColor(Colors.BLUE)
                        .styled(style -> style.withHoverEvent(
                                new HoverEvent.ShowText(Text.of(group.uuid().toString()))
                        ))
        );
        text.append("Owner: ");
        text.append(
                Text.literal(getPlayerNameOrUuid(group.owner(), ctx.getSource().getServer()) + "\n")
                        .withColor(Colors.GREEN)
        );

        text.append("Members: ");

        for(UUID uuid : group.members()) {
            text.append("\n  - ");
            text.append(
                    Text.literal(getPlayerNameOrUuid(uuid, ctx.getSource().getServer()))
                            .withColor(Colors.LIGHT_YELLOW)
            );
        }

        if(group.members().isEmpty()) {
            text.append(Text.literal("¯\\_(ツ)_/¯").withColor(Colors.YELLOW));
        }

        ctx.getSource().sendFeedback(() -> text, false);

        return Command.SINGLE_SUCCESS;
    }

    private static int groupListCommand(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        // TODO pagination
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

        GroupState groupState = GroupState.getState(ctx.getSource().getServer());

        Collection<GroupComponent> groups = groupState.getGroups().stream()
                .filter(group -> group.isMember(player) || Permissions.check(player, "cclaim.group.admin", 3))
                .toList();

        MutableText text = Text.literal("");
        text.append(
                Text.literal("--- Container Claim Groups (" + groups.size() + ") ---")
                        .withColor(Colors.CYAN)
        );
        for(GroupComponent group : groups) {
            String ownerName = getPlayerNameOrUuid(group.owner(), ctx.getSource().getServer());

            text.append(Text.of("\n  - "));
            text.append(
                    Text.literal(group.name())
                            .withColor(Colors.GREEN)
                            .styled(style -> style
                                    .withHoverEvent(new HoverEvent.ShowText(Text.of(ownerName + " (" + group.members().size() + ")")))
                            )
            );
        }

        if(groups.isEmpty()) {
            text.append(Text.literal("\nThere are no groups (that you can see)").withColor(Colors.LIGHT_RED));
        }

        ctx.getSource().sendFeedback(() -> text, false);

        return Command.SINGLE_SUCCESS;
    }

    private static int groupAddMemberCommand(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

        String groupName = StringArgumentType.getString(ctx, "group");
        GroupState groupState = GroupState.getState(ctx.getSource().getServer());
        GroupComponent group = getGroup(groupState, groupName);

        if(!group.owner().equals(player.getUuid()) && !Permissions.check(player, "cclaim.group.admin", 3)) {
            throw PERMISSION_DENIED.create();
        }

        Collection<GameProfile> gameProfiles = GameProfileArgumentType.getProfileArgument(ctx, "targets");

        groupState.modifyGroup(group.addMembers(gameProfiles.stream().map(GameProfile::getId).toList()));

        for(GameProfile gameProfile : gameProfiles) {
            ctx.getSource().sendFeedback(() -> Text.of("Added " + gameProfile.getName() + " to the group"), false);
        }

        return gameProfiles.size();
    }

    private static int groupRemoveMemberCommand(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

        String groupName = StringArgumentType.getString(ctx, "group");
        GroupState groupState = GroupState.getState(ctx.getSource().getServer());
        GroupComponent group = getGroup(groupState, groupName);

        if(!group.owner().equals(player.getUuid()) && !Permissions.check(player, "cclaim.group.admin", 3)) {
            throw PERMISSION_DENIED.create();
        }

        Collection<GameProfile> gameProfiles = GameProfileArgumentType.getProfileArgument(ctx, "targets");

        groupState.modifyGroup(group.removeMembers(gameProfiles.stream().map(GameProfile::getId).toList()));

        int count = 0;
        for(GameProfile gameProfile : gameProfiles) {
            if(group.members().contains(gameProfile.getId())) {
                ctx.getSource().sendFeedback(() -> Text.of("Removed " + gameProfile.getName() + " from the group"), false);
                count++;
            } else {
                ctx.getSource().sendFeedback(() -> Text.of(gameProfile.getName() + " was not a member of the group"), false);
            }
        }

        return count;
    }

    private static int groupTransferCommand(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        String groupName = StringArgumentType.getString(ctx, "group");
        GroupState groupState = GroupState.getState(ctx.getSource().getServer());
        GroupComponent group = getGroup(groupState, groupName);

        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");

        if(group.owner().equals(target.getUuid())) {
            throw ALREADY_GROUP_OWNER.create();
        }

        groupState.modifyGroup(group.withOwner(target.getUuid()));

        ctx.getSource().sendFeedback(
                () -> Text.literal("Successfully transferred group " + group.name() + " to ")
                        .append(target.getName()),
                true
        );

        return Command.SINGLE_SUCCESS;
    }
}
