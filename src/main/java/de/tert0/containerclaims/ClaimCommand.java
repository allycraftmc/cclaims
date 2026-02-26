package de.tert0.containerclaims;

import com.google.common.collect.ImmutableSet;
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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.CommonColors;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

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
                                                    argument("targets", GameProfileArgument.gameProfile())
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
                                                    argument("targets", GameProfileArgument.gameProfile())
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
                                            .requires(Permissions.require("cclaim.adminmode", PermissionLevel.ADMINS))
                                            .executes(ClaimCommand::adminmodeCommand)
                            )
                            .then(
                                    literal("list")
                                            .requires(Permissions.require("cclaim.list", PermissionLevel.GAMEMASTERS))
                                            .executes(ctx -> ClaimCommand.listCommand(ctx.getSource(), ctx.getSource().getPlayerOrException().level(), 1))
                                            .then(
                                                    argument("dimension", DimensionArgument.dimension())
                                                            .executes(ctx -> ClaimCommand.listCommand(ctx.getSource(), DimensionArgument.getDimension(ctx, "dimension"), 1))
                                                            .then(
                                                                    argument("page", IntegerArgumentType.integer(1))
                                                                            .executes(
                                                                                    ctx ->
                                                                                            ClaimCommand.listCommand(ctx.getSource(), DimensionArgument.getDimension(ctx, "dimension"), IntegerArgumentType.getInteger(ctx, "page"))
                                                                            )
                                                            )
                                                            .then(
                                                                    literal("all")
                                                                            .executes(
                                                                                    ctx ->
                                                                                            ClaimCommand.listCommand(ctx.getSource(), DimensionArgument.getDimension(ctx, "dimension"), -1)
                                                                            )
                                                            )
                                            )
                            )
                            .then(
                                    literal("debug")
                                            .requires(Permissions.require("cclaim.debug", PermissionLevel.OWNERS))
                                            .then(
                                                    literal("verify")
                                                            .requires(Permissions.require("cclaim.debug.verify", PermissionLevel.OWNERS))
                                                            .executes(ctx -> ClaimCommand.verifyCommand(ctx, ctx.getSource().getPlayerOrException().level(), false))
                                                            .then(
                                                                    argument("dimension", DimensionArgument.dimension())
                                                                            .executes(ctx -> ClaimCommand.verifyCommand(ctx, DimensionArgument.getDimension(ctx, "dimension"), false))
                                                                            .then(
                                                                                    literal("load")
                                                                                            .executes(ctx -> {
                                                                                                ctx.getSource().sendSuccess(() -> Component.literal("WARNING: This will load all chunks with registered claims in them. Be careful, especially in production!").withColor(CommonColors.YELLOW), false);
                                                                                                ctx.getSource().sendSuccess(() -> Component.literal("To confirm that you want to do this, run /cclaim debug verify <dimension> load confirm"), false);
                                                                                                return 0;
                                                                                            })
                                                                                            .then(
                                                                                                    literal("confirm")
                                                                                                            .executes(ctx -> ClaimCommand.verifyCommand(ctx, DimensionArgument.getDimension(ctx, "dimension"), true))
                                                                                            )
                                                                            )
                                                            )
                                            )
                            )
                            .then(
                                    literal("group")
                                            .then(
                                                    literal("create")
                                                            .requires(Permissions.require("cclaim.group.create", true))
                                                            .then(
                                                                    argument("group", StringArgumentType.word())
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
                                                    literal("join")
                                                            .then(
                                                                    argument("group", StringArgumentType.word())
                                                                            .suggests(GroupSuggestionProvider.owner())
                                                                            .then(
                                                                                    argument("targets", GameProfileArgument.gameProfile())
                                                                                            .executes(ClaimCommand::groupJoinCommand)
                                                                            )
                                                            )
                                            )
                                            .then(
                                                    literal("leave")
                                                            .then(
                                                                    argument("group", StringArgumentType.word())
                                                                            .suggests(GroupSuggestionProvider.owner())
                                                                            .then(
                                                                                    argument("targets", GameProfileArgument.gameProfile())
                                                                                            .executes(ClaimCommand::groupLeaveCommand)
                                                                            )
                                                            )
                                            )
                                            .then(
                                                    literal("transfer")
                                                            .requires(Permissions.require("cclaim.group.transfer", PermissionLevel.ADMINS))
                                                            .then(
                                                                    argument("group", StringArgumentType.word())
                                                                            .suggests(GroupSuggestionProvider.owner())
                                                                            .then(
                                                                                    argument("player", EntityArgument.player())
                                                                                            .executes(ClaimCommand::groupTransferCommand)
                                                                            )
                                                            )
                                            )
                            )
            );
        });
    }

    private static ClaimAccess getFocusedClaimAccess(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if(!(player.pick(player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), 1.0f, false) instanceof BlockHitResult result)) {
            throw NO_CONTAINER_FOCUSED.create();
        }
        BlockPos pos = result.getBlockPos();
        BlockEntity blockEntity = player.level().getBlockEntity(pos);
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

    private static void checkForOwnedClaim(ClaimAccess claimAccess, ServerPlayer player) throws CommandSyntaxException {
        if(!ClaimUtils.isClaimed(claimAccess)) {
            throw NOT_CLAIMED.create();
        }
        if(!ClaimUtils.isOwnerOrAdmin(claimAccess, player)) {
            throw NOT_OWNER.create();
        }
    }

    private static int helpCommand(CommandContext<CommandSourceStack> ctx) {
        MutableComponent text = Component.literal("");
        text.append(Component.literal("Container Claim Mod - Help\n").withColor(CommonColors.HIGH_CONTRAST_DIAMOND));
        text.append("-".repeat(20) + "\n");
        text.append("This can be used to claim container blocks like chests or barrels.\n");
        text.append("To claim a container block, look at the block and run ");
        text.append(
                Component.literal("/cclaim claim\n")
                        .withColor(CommonColors.YELLOW)
                        .withStyle(
                                style -> style.withClickEvent(new ClickEvent.SuggestCommand("/cclaim claim"))
                        )
        );
        text.append(
                Component.literal("/cclaim unlaim")
                        .withColor(CommonColors.YELLOW)
                        .withStyle(
                                style -> style.withClickEvent(new ClickEvent.SuggestCommand("/cclaim unclaim"))
                        )
        );
        text.append(" can be used to unclaim a container\n");
        text.append("You can allow others to use a claimed container using ");
        text.append(
                Component.literal("/cclaim trust <player>")
                        .withColor(CommonColors.YELLOW)
                        .withStyle(
                                style -> style.withClickEvent(new ClickEvent.SuggestCommand("/cclaim trust "))
                        )
        );
        text.append(". To revoke these permissions, you can use ");
        text.append(
                Component.literal("/cclaim untrust <player>")
                        .withColor(CommonColors.YELLOW)
                        .withStyle(
                                style -> style.withClickEvent(new ClickEvent.SuggestCommand("/cclaim untrust "))
                        )
        );
        text.append("\n");
        text.append("To get information about a claim, you can use ");
        text.append(
                Component.literal("/cclaim info")
                        .withColor(CommonColors.YELLOW)
                        .withStyle(
                                style -> style.withClickEvent(new ClickEvent.SuggestCommand("/cclaim info"))
                        )
        );
        text.append(".\n");

        ctx.getSource().sendSuccess(() -> text, false);
        return Command.SINGLE_SUCCESS;
    }

    private static int infoCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ClaimAccess claimAccess = getFocusedClaimAccess(ctx);
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        MutableComponent text = Component.literal("");
        text.append(Component.literal("Container Claim Info\n").withColor(CommonColors.HIGH_CONTRAST_DIAMOND));
        text.append("-".repeat(20) + "\n");

        if(!ClaimUtils.isClaimed(claimAccess)) {
            text.append(Component.literal("This container is not claimed!").withColor(CommonColors.SOFT_RED));
        } else if(!ClaimUtils.canUse(claimAccess, player) && !Permissions.check(player, "cclaim.info.admin", PermissionLevel.GAMEMASTERS)) {
            text.append(Component.literal("This container is claimed!").withColor(CommonColors.SOFT_YELLOW));
        } else {
            text.append("Owner: ");
            text.append(
                    Component.literal(getPlayerNameOrUuid(claimAccess.cclaims$getClaim().owner(), ctx.getSource().getServer()))
                            .withColor(CommonColors.GREEN)
            );
            text.append("\n");
            text.append("Trusted: ");

            Collection<UUID> trustedUuids = claimAccess.cclaims$getClaim().trusted();
            if(trustedUuids.isEmpty()) {
                text.append(Component.literal("¯\\_(ツ)_/¯").withColor(CommonColors.YELLOW));
            } else {
                for(UUID trustedUuid : trustedUuids) {
                    text.append(Component.nullToEmpty("\n  - "));
                    text.append(
                            Component.literal(getPlayerNameOrUuid(trustedUuid, ctx.getSource().getServer()))
                                    .withColor(CommonColors.SOFT_YELLOW)
                    );
                }
            }

            text.append("\n");
            text.append("Trusted Groups: ");

            GroupState groupState = GroupState.getState(ctx.getSource().getServer());
            Collection<UUID> trustedGroupUUIDs = claimAccess.cclaims$getClaim().trustedGroups();

            if(trustedGroupUUIDs.isEmpty()) {
                text.append(Component.literal("¯\\_(ツ)_/¯").withColor(CommonColors.YELLOW));
            } else {
                for(UUID trustedGroupUuid : trustedGroupUUIDs) {
                    text.append(Component.nullToEmpty("\n  - "));
                    text.append(
                            groupState.getGroups().stream()
                                    .filter(g -> g.uuid().equals(trustedGroupUuid))
                                    .findFirst()
                                    .map(group ->
                                            Component.literal(group.name())
                                                    .withColor(CommonColors.SOFT_YELLOW)
                                                    .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.nullToEmpty(
                                                            getPlayerNameOrUuid(group.owner(), ctx.getSource().getServer()) + " (" + group.members().size() + ")"
                                                    ))))
                                    )
                                    .orElse(
                                            Component.literal(trustedGroupUuid.toString())
                                                    .withColor(CommonColors.RED)
                                                    .withStyle(style -> style
                                                            .withHoverEvent(new HoverEvent.ShowText(Component.nullToEmpty("Group does not exist")))
                                                    )
                                    )
                    );
                }
            }

            if(Permissions.check(player, "cclaim.info.admin", PermissionLevel.GAMEMASTERS)) {
                String formattedTimestamp = DateTimeFormatter.ISO_DATE_TIME
                        .withZone(ZoneOffset.UTC)
                        .format(claimAccess.cclaims$getClaim().timestamp());

                text.append("\n");
                text.append("Timestamp: " + formattedTimestamp);
            }
        }

        ctx.getSource().sendSuccess(() -> text, false);
        return Command.SINGLE_SUCCESS;
    }

    private static int claimCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ClaimAccess claimAccess = getFocusedClaimAccess(ctx);
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        if(ClaimUtils.isClaimed(claimAccess)) {
            throw ALREADY_CLAIMED.create();
        }

        ClaimUtils.claim(claimAccess, player.getUUID(), player.level());
        ctx.getSource().sendSuccess(() -> Component.nullToEmpty("Claimed container"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int unclaimCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ClaimAccess claimAccess = getFocusedClaimAccess(ctx);
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        checkForOwnedClaim(claimAccess, player);

        ClaimUtils.unclaim(claimAccess, player.level());
        ctx.getSource().sendSuccess(() -> Component.nullToEmpty("Unclaimed container"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int trustCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ClaimAccess claimAccess = getFocusedClaimAccess(ctx);
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        checkForOwnedClaim(claimAccess, player);

        Collection<NameAndId> targets = GameProfileArgument.getGameProfiles(ctx, "targets");

        List<UUID> entries = new ArrayList<>();
        for(NameAndId target : targets) {
            entries.add(target.id());
            ctx.getSource().sendSuccess(() -> Component.nullToEmpty("Added " + target.name() + " as trusted player"), false);
        }
        ClaimUtils.trust(claimAccess, entries);

        if(targets.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.nullToEmpty("No targets found"), false);
        }

        return targets.size();
    }

    private static int trustGroupCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ClaimAccess claimAccess = getFocusedClaimAccess(ctx);
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        checkForOwnedClaim(claimAccess, player);

        String groupName = StringArgumentType.getString(ctx, "group");
        GroupState groupState = GroupState.getState(ctx.getSource().getServer());
        GroupComponent group = getGroup(groupState, groupName);

        ClaimUtils.trustGroup(claimAccess, group);

        ctx.getSource().sendSuccess(() -> Component.literal("Added " + group.name() + " as a trusted group"), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int untrustCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ClaimAccess claimAccess = getFocusedClaimAccess(ctx);
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        checkForOwnedClaim(claimAccess, player);

        Collection<NameAndId> targets = GameProfileArgument.getGameProfiles(ctx, "targets");

        List<UUID> entries = new ArrayList<>();
        for(NameAndId target : targets) {
            if(ClaimUtils.isTrusted(claimAccess, target.id())) {
                entries.add(target.id());
                ctx.getSource().sendSuccess(() -> Component.nullToEmpty("Removed " + target.name() + " as a trusted player"), false);
            } else {
                ctx.getSource().sendSuccess(() -> Component.nullToEmpty(target.name() + " was not trusted"), false);
            }
        }
        ClaimUtils.untrust(claimAccess, entries);

        if(entries.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.nullToEmpty("No player to untrust found"), false);
        }

        return entries.size();
    }

    private static int untrustGroupCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ClaimAccess claimAccess = getFocusedClaimAccess(ctx);
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        checkForOwnedClaim(claimAccess, player);

        String groupName = StringArgumentType.getString(ctx, "group");
        GroupState groupState = GroupState.getState(ctx.getSource().getServer());
        GroupComponent group = getGroup(groupState, groupName);

        if(!claimAccess.cclaims$getClaim().trustedGroups().contains(group.uuid())) {
            throw GROUP_NOT_TRUSTED.create();
        }

        ClaimUtils.untrustGroup(claimAccess, group);

        ctx.getSource().sendSuccess(() -> Component.literal("Removed " + group.name() + " as a trusted group"), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int adminmodeCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        AdminModeAccess adminModeAccess = (AdminModeAccess) player;

        adminModeAccess.cclaims$setAdminMode(!adminModeAccess.cclaims$getAdminMode());

        if(adminModeAccess.cclaims$getAdminMode()) {
            ctx.getSource().sendSuccess(() -> Component.nullToEmpty("Enabled Container Claim Admin Mode"), true);
        } else {
            ctx.getSource().sendSuccess(() -> Component.nullToEmpty("Disabled Container Claim Admin Mode"), true);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static MutableComponent getTextOfBlockPos(BlockPos pos, boolean copyable) {
        String formattedPos = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        MutableComponent text = Component.literal(formattedPos)
                .withStyle(
                        style -> style
                                .withHoverEvent(new HoverEvent.ShowText(Component.nullToEmpty("Click to teleport")))
                                .withClickEvent(new ClickEvent.RunCommand("/tp " + formattedPos))
                );

        if(copyable) {
            text.append(
                    Component.literal(" (Copy)")
                            .withColor(CommonColors.LIGHT_GRAY)
                            .withStyle(
                                    style -> style
                                            .withHoverEvent(new HoverEvent.ShowText(Component.nullToEmpty("Click to copy")))
                                            .withClickEvent(new ClickEvent.CopyToClipboard(formattedPos))
                            )
            );
        }

        return text;
    }

    @NotNull
    private static String getPlayerNameOrUuid(UUID uuid, MinecraftServer server) {
        return server.services().nameToIdCache()
                .get(uuid)
                .map(NameAndId::name)
                .orElse(uuid.toString());
    }

    private static int listCommand(CommandSourceStack source, ServerLevel serverWorld, int page) throws CommandSyntaxException {
        List<BlockPos> positions = GlobalClaimState.getWorldState(serverWorld).getPositions()
                .stream()
                .sorted() // TODO maybe some kind of 3d spiral around the origin
                .toList();
        Identifier dimension = serverWorld.dimensionTypeRegistration().unwrapKey()
                .orElseThrow(MISSING_DIMENSION_REGISTRY_KEY::create)
                .identifier();
        String dimensionName = dimension.getPath();

        int totalPageCount = Math.ceilDiv(positions.size(), LIST_PAGE_SIZE);

        MutableComponent text = Component.literal("");
        text.append(
                Component.literal("--- Container Claims - " + dimensionName+ " (" + positions.size() + ") ---")
                        .withColor(CommonColors.HIGH_CONTRAST_DIAMOND)
        );

        if(positions.isEmpty()) {
            text.append(
                    Component.literal("\nNo registered claims")
                            .withColor(CommonColors.SOFT_RED)
            );
        } else {
            if(page > totalPageCount) {
                throw PAGE_OUT_OF_BOUNDS.create();
            }
            if(page != -1) {
                positions = positions.subList((page - 1) * LIST_PAGE_SIZE, Math.min(page * LIST_PAGE_SIZE, positions.size()));
            }

            for(BlockPos pos : positions) {
                Optional<Component> extraText = Optional.empty();
                if(serverWorld.isLoaded(pos)) {
                    ClaimAccess claimAccess = (ClaimAccess) serverWorld.getBlockEntity(pos);
                    if(claimAccess != null) {
                        List<String> trustedNames = claimAccess.cclaims$getClaim().trusted().stream()
                                .map(uuid -> getPlayerNameOrUuid(uuid, source.getServer()))
                                .toList();

                        String ownerName = getPlayerNameOrUuid(claimAccess.cclaims$getClaim().owner(), source.getServer());
                        extraText = Optional.of(
                                Component.literal(" - " + ownerName)
                                        .withColor(CommonColors.YELLOW)
                                        .withStyle(style -> trustedNames.isEmpty() ? style :style.withHoverEvent(
                                                new HoverEvent.ShowText(Component.nullToEmpty(String.join("\n", trustedNames)))
                                        ))
                        );
                    }
                }

                text.append(Component.nullToEmpty("\n  - "));
                text.append(getTextOfBlockPos(pos, true).copy().withColor(CommonColors.GREEN));
                extraText.ifPresent(text::append);
            }

            if(page != -1) {
                Component btnPrev = (page > 1) ? Component.literal("<<")
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent.RunCommand("/cclaim list " + dimension + " " + (page - 1)))
                                .withHoverEvent(new HoverEvent.ShowText(Component.nullToEmpty("Previous Page")))
                        ) : Component.literal("<<");
                Component btnNext = (page + 1 <= totalPageCount) ? Component.literal(">>")
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent.RunCommand("/cclaim list " + dimension + " " + (page + 1)))
                                .withHoverEvent(new HoverEvent.ShowText(Component.nullToEmpty("Next Page")))
                        ) : Component.literal(">>");
                text.append(
                        Component.literal("\n----- ")
                                .withColor(CommonColors.HIGH_CONTRAST_DIAMOND)
                                .append(btnPrev)
                                .append(Component.nullToEmpty(" Page " + page + " of " + totalPageCount + " "))
                                .append(btnNext)
                                .append(" -----")
                );
            } else {
                text.append(Component.literal("\n" + "-".repeat(30)).withColor(CommonColors.HIGH_CONTRAST_DIAMOND));
            }
        }

        source.sendSuccess(() -> text, false);

        return Command.SINGLE_SUCCESS;
    }

    private static int verifyCommand(CommandContext<CommandSourceStack> ctx, ServerLevel serverWorld, boolean loadChunks) {
        Set<BlockPos> allPositions = GlobalClaimState.getWorldState(serverWorld).getPositions();

        Set<BlockPos> loadedPositions = allPositions.stream()
                .filter(pos -> serverWorld.isLoaded(pos) || loadChunks)
                .collect(Collectors.toSet());

        List<Tuple<BlockPos, Component>> problems = new ArrayList<>();
        for(BlockPos pos : loadedPositions) {
            ClaimAccess claimAccess = (ClaimAccess) serverWorld.getBlockEntity(pos);

            // Check Claim exists
            if(claimAccess == null || !ClaimUtils.isClaimed(claimAccess)) {
                problems.add(new Tuple<>(pos, Component.literal("Claim not found").withColor(CommonColors.RED)));
                continue;
            }

            // Check Double Chests
            ClaimAccess otherClaimAccess = (ClaimAccess) DoubleChestUtils.getNeighborBlockEntity(pos, serverWorld);
            if(otherClaimAccess != null) {
                if(!ClaimUtils.isClaimed(otherClaimAccess)) {
                    problems.add(new Tuple<>(pos, Component.literal("Double Chest not fully claimed").withColor(CommonColors.YELLOW)));
                    continue;
                }

                ClaimComponent claim = claimAccess.cclaims$getClaim();
                ClaimComponent otherClaim = otherClaimAccess.cclaims$getClaim();

                if(!claim.owner().equals(otherClaim.owner())) {
                    problems.add(new Tuple<>(pos, Component.literal("Double Chest owners do not match").withColor(CommonColors.YELLOW)));
                    continue;
                }

                if(!claim.trusted().equals(otherClaim.trusted())) {
                    problems.add(new Tuple<>(pos, Component.literal("Double Chest trusted players do not match").withColor(CommonColors.YELLOW)));
                    continue;
                }
            }
        }

        ctx.getSource().sendSuccess(() -> Component.nullToEmpty("Checked " + loadedPositions.size() + "/" + allPositions.size() + " positions"), false);

        if(problems.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No problems found").withColor(CommonColors.GREEN), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal(problems.size() + " problems found").withColor(CommonColors.RED), false);
            for(Tuple<BlockPos, Component> entry : problems) {
                ctx.getSource().sendSuccess(
                        () -> Component.literal("- ").append(getTextOfBlockPos(entry.getA(), false).withColor(CommonColors.GREEN)).append(": ").append(entry.getB()),
                        false
                );
            }
        }


        return Command.SINGLE_SUCCESS;
    }

    private static int groupCreateCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String groupName = StringArgumentType.getString(ctx, "group");

        GroupState groupState = GroupState.getState(ctx.getSource().getServer());

        if (groupState.getGroups().stream().anyMatch(g -> g.name().equals(groupName))) {
            throw GROUP_ALREADY_EXISTS.create();
        }

        long currentCount = groupState.getGroups().stream()
                .filter(g -> g.owner().equals(player.getUUID()))
                .count();

        if(currentCount >= 2 && !Permissions.check(player, "cclaim.group.admin", PermissionLevel.ADMINS)) { // TODO make configurable
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
                player.getUUID(),
                ImmutableSet.of()
        );

        groupState.addGroup(group);

        ctx.getSource().sendSuccess(() -> Component.literal("Successfully created group!").withColor(CommonColors.GREEN), false);

        return Command.SINGLE_SUCCESS;
    }

    private static GroupComponent getGroup(GroupState groupState, String groupName) throws CommandSyntaxException {
        return groupState.getGroups()
                .stream()
                .filter(g -> g.name().equals(groupName))
                .findFirst()
                .orElseThrow(GROUP_DOES_NOT_EXIST::create);
    }

    private static int groupDeleteCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        String groupName = StringArgumentType.getString(ctx, "group");
        GroupState groupState = GroupState.getState(ctx.getSource().getServer());
        GroupComponent group = getGroup(groupState, groupName);

        if(!group.owner().equals(player.getUUID()) && !Permissions.check(player, "cclaim.group.admin", PermissionLevel.ADMINS)) {
            throw PERMISSION_DENIED.create();
        }

        groupState.removeGroup(group);

        ctx.getSource().sendSuccess(() -> Component.literal("Successfully removed group").withColor(CommonColors.GREEN), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int groupInfoCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        String groupName = StringArgumentType.getString(ctx, "group");
        GroupState groupState = GroupState.getState(ctx.getSource().getServer());
        GroupComponent group = getGroup(groupState, groupName);

        if(!group.isMember(player) && !Permissions.check(player, "cclaim.group.admin", PermissionLevel.ADMINS)) {
            throw PERMISSION_DENIED.create();
        }

        MutableComponent text = Component.literal("");

        text.append(Component.literal("Container Claim Group Info\n").withColor(CommonColors.HIGH_CONTRAST_DIAMOND));
        text.append("-".repeat(20) + "\n");

        text.append("Name: ");
        text.append(
                Component.literal(group.name() + "\n")
                        .withColor(CommonColors.BLUE)
                        .withStyle(style -> style.withHoverEvent(
                                new HoverEvent.ShowText(Component.nullToEmpty(group.uuid().toString()))
                        ))
        );
        text.append("Owner: ");
        text.append(
                Component.literal(getPlayerNameOrUuid(group.owner(), ctx.getSource().getServer()) + "\n")
                        .withColor(CommonColors.GREEN)
        );

        text.append("Members: ");

        for(UUID uuid : group.members()) {
            text.append("\n  - ");
            text.append(
                    Component.literal(getPlayerNameOrUuid(uuid, ctx.getSource().getServer()))
                            .withColor(CommonColors.SOFT_YELLOW)
            );
        }

        if(group.members().isEmpty()) {
            text.append(Component.literal("¯\\_(ツ)_/¯").withColor(CommonColors.YELLOW));
        }

        ctx.getSource().sendSuccess(() -> text, false);

        return Command.SINGLE_SUCCESS;
    }

    private static int groupListCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        // TODO pagination
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        GroupState groupState = GroupState.getState(ctx.getSource().getServer());

        Collection<GroupComponent> groups = groupState.getGroups().stream()
                .filter(group -> group.isMember(player) || Permissions.check(player, "cclaim.group.admin", PermissionLevel.ADMINS))
                .toList();

        MutableComponent text = Component.literal("");
        text.append(
                Component.literal("--- Container Claim Groups (" + groups.size() + ") ---")
                        .withColor(CommonColors.HIGH_CONTRAST_DIAMOND)
        );
        for(GroupComponent group : groups) {
            String ownerName = getPlayerNameOrUuid(group.owner(), ctx.getSource().getServer());

            text.append(Component.nullToEmpty("\n  - "));
            text.append(
                    Component.literal(group.name())
                            .withColor(CommonColors.GREEN)
                            .withStyle(style -> style
                                    .withHoverEvent(new HoverEvent.ShowText(Component.nullToEmpty(ownerName + " (" + group.members().size() + ")")))
                            )
            );
        }

        if(groups.isEmpty()) {
            text.append(Component.literal("\nThere are no groups (that you can see)").withColor(CommonColors.SOFT_RED));
        }

        ctx.getSource().sendSuccess(() -> text, false);

        return Command.SINGLE_SUCCESS;
    }

    private static int groupJoinCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        String groupName = StringArgumentType.getString(ctx, "group");
        GroupState groupState = GroupState.getState(ctx.getSource().getServer());
        GroupComponent group = getGroup(groupState, groupName);

        if(!group.owner().equals(player.getUUID()) && !Permissions.check(player, "cclaim.group.admin", PermissionLevel.ADMINS)) {
            throw PERMISSION_DENIED.create();
        }

        Collection<NameAndId> gameProfiles = GameProfileArgument.getGameProfiles(ctx, "targets");

        groupState.modifyGroup(group.addMembers(gameProfiles.stream().map(NameAndId::id).toList()));

        for(NameAndId gameProfile : gameProfiles) {
            ctx.getSource().sendSuccess(() -> Component.nullToEmpty("Added " + gameProfile.name() + " to the group"), false);
        }

        return gameProfiles.size();
    }

    private static int groupLeaveCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        String groupName = StringArgumentType.getString(ctx, "group");
        GroupState groupState = GroupState.getState(ctx.getSource().getServer());
        GroupComponent group = getGroup(groupState, groupName);

        if(!group.owner().equals(player.getUUID()) && !Permissions.check(player, "cclaim.group.admin", PermissionLevel.ADMINS)) {
            throw PERMISSION_DENIED.create();
        }

        Collection<NameAndId> gameProfiles = GameProfileArgument.getGameProfiles(ctx, "targets");

        groupState.modifyGroup(group.removeMembers(gameProfiles.stream().map(NameAndId::id).toList()));

        int count = 0;
        for(NameAndId gameProfile : gameProfiles) {
            if(group.members().contains(gameProfile.id())) {
                ctx.getSource().sendSuccess(() -> Component.nullToEmpty("Removed " + gameProfile.name() + " from the group"), false);
                count++;
            } else {
                ctx.getSource().sendSuccess(() -> Component.nullToEmpty(gameProfile.name() + " was not a member of the group"), false);
            }
        }

        return count;
    }

    private static int groupTransferCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String groupName = StringArgumentType.getString(ctx, "group");
        GroupState groupState = GroupState.getState(ctx.getSource().getServer());
        GroupComponent group = getGroup(groupState, groupName);

        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

        if(group.owner().equals(target.getUUID())) {
            throw ALREADY_GROUP_OWNER.create();
        }

        groupState.modifyGroup(group.withOwner(target.getUUID()));

        ctx.getSource().sendSuccess(
                () -> Component.literal("Successfully transferred group " + group.name() + " to ")
                        .append(target.getName()),
                true
        );

        return Command.SINGLE_SUCCESS;
    }
}
