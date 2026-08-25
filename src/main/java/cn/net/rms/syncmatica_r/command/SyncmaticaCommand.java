package cn.net.rms.syncmatica_r.command;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.Syncmatica;
import cn.net.rms.syncmatica_r.communication.PlacementAccessPolicy;
import cn.net.rms.syncmatica_r.communication.ServerCommunicationManager;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.material.StockingAreaDefinition;
import cn.net.rms.syncmatica_r.schematic.SchematicPeek;
import cn.net.rms.syncmatica_r.schematic.SchematicPeeker;
import cn.net.rms.syncmatica_r.service.MaterialService;
import cn.net.rms.syncmatica_r.util.SyncmaticaUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
//#if MC < 12001
import net.minecraft.text.LiteralText;
//#endif
//#if MC >= 12001
//$$ import net.minecraft.text.Text;
//#endif
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.mojang.brigadier.arguments.StringArgumentType.string;

public final class SyncmaticaCommand {
    private static final String COMMAND_PERMISSION = "syncmatica_r.command";
    private static final String LOAD_PERMISSION = "syncmatica_r.command.load";
    private static final int COMMAND_PERMISSION_LEVEL = 2;
    private static final String LITEMATIC_EXTENSION = ".litematic";
    private static final Map<String, CachedPeek> PEEK_CACHE = new HashMap<>();

    private SyncmaticaCommand() {
    }

    public static void register(final CommandDispatcher<ServerCommandSource> dispatcher) {
        final LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("syncmatica_r")
                .then(loadArgument())
                .then(projectArgument())
                .then(defaultArgument());
        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> loadArgument() {
        return CommandManager.literal("load")
                .requires(SyncmaticaCommand::hasLoadPermission)
                .executes(SyncmaticaCommand::handleLoadAll)
                .then(CommandManager.argument("file", string())
                        .suggests(SyncmaticaCommand::suggestOrphanFiles)
                        .executes(SyncmaticaCommand::handleLoadSingle));
    }

    private static CompletableFuture<Suggestions> suggestOrphanFiles(final CommandContext<ServerCommandSource> context,
                                                                     final SuggestionsBuilder builder) {
        final Context syncmaticaContext = Syncmatica.getContext(Syncmatica.SERVER_CONTEXT);
        if (syncmaticaContext != null) {
            for (final File file : listOrphanCandidates(syncmaticaContext)) {
                final String base = removeLitematicExtension(file.getName());
                final SchematicPeek peek = peekCached(file);
                if (peek != null && peek.hasName()) {
                    builder.suggest(base, new LiteralMessage(peek.getName()));
                } else {
                    builder.suggest(base);
                }
            }
        }
        return builder.buildFuture();
    }

    private static int handleLoadAll(final CommandContext<ServerCommandSource> context) {
        final Context syncmaticaContext = Syncmatica.getContext(Syncmatica.SERVER_CONTEXT);
        if (syncmaticaContext == null) {
            context.getSource().sendError(literal("Syncmatica_r server context unavailable"));
            return 0;
        }
        final List<File> candidates = listOrphanCandidates(syncmaticaContext);
        if (candidates.isEmpty()) {
            sendFeedback(context, "No syncmatic file(s) found that need to be loaded");
            return 0;
        }
        int loaded = 0;
        for (final File file : candidates) {
            if (loadOrphanFile(context, syncmaticaContext, file)) {
                loaded++;
            }
        }
        sendFeedback(context, loaded + " syncmatic file(s) loaded");
        return loaded > 0 ? 1 : 0;
    }

    private static int handleLoadSingle(final CommandContext<ServerCommandSource> context) {
        final Context syncmaticaContext = Syncmatica.getContext(Syncmatica.SERVER_CONTEXT);
        if (syncmaticaContext == null) {
            context.getSource().sendError(literal("Syncmatica_r server context unavailable"));
            return 0;
        }
        String name = context.getArgument("file", String.class);
        if (name.endsWith(LITEMATIC_EXTENSION)) {
            name = removeLitematicExtension(name);
        }
        if (name.isEmpty() || !SyncmaticaUtil.sanitizeFileName(name).equals(name)) {
            context.getSource().sendError(literal("Invalid syncmatic file name: " + name));
            return 0;
        }
        final File file = new File(syncmaticaContext.getLitematicFolder(), name + LITEMATIC_EXTENSION);
        if (!file.isFile()) {
            context.getSource().sendError(literal("Syncmatic file not found: " + name + LITEMATIC_EXTENSION));
            return 0;
        }
        return loadOrphanFile(context, syncmaticaContext, file) ? 1 : 0;
    }

    /**
     * Registers a litematic file from the server folder as a shared placement.
     * The file is stored under its content hash so later downloads resolve;
     * files that already back a registered placement are skipped.
     */
    private static boolean loadOrphanFile(final CommandContext<ServerCommandSource> context,
                                          final Context syncmaticaContext,
                                          final File file) {
        final UUID hash;
        try (FileInputStream input = new FileInputStream(file)) {
            hash = SyncmaticaUtil.createChecksum(input);
        } catch (final Exception exception) {
            context.getSource().sendError(literal("Failed to read syncmatic file: " + file.getName()));
            return false;
        }
        if (hasPlacementHash(syncmaticaContext, hash)) {
            sendFeedback(context, "Skipping " + file.getName() + ": already registered");
            return false;
        }

        final File storedFile = new File(syncmaticaContext.getLitematicFolder(), hash + LITEMATIC_EXTENSION);
        if (!file.equals(storedFile) && !storedFile.exists()) {
            try {
                Files.move(file.toPath(), storedFile.toPath());
            } catch (final Exception exception) {
                context.getSource().sendError(literal("Failed to store syncmatic file: " + file.getName()));
                return false;
            }
        }

        final ServerCommandSource source = context.getSource();
        final Entity entity = source.getEntity();
        final ServerPlayerEntity player = entity instanceof ServerPlayerEntity ? (ServerPlayerEntity) entity : null;
        final PlayerIdentifier owner = player != null
                ? syncmaticaContext.getPlayerIdentifierProvider().createOrGet(player.getGameProfile())
                : PlayerIdentifier.MISSING_PLAYER;

        final ServerPlacement placement = new ServerPlacement(
                UUID.randomUUID(),
                removeLitematicExtension(file.getName()),
                hash,
                owner
        );
        final String dimension = source.getWorld().getRegistryKey().getValue().toString();
        final BlockPos position = player != null ? player.getBlockPos() : BlockPos.ORIGIN;
        placement.move(dimension, position, BlockRotation.NONE, BlockMirror.NONE);

        final ServerCommunicationManager comms =
                (ServerCommunicationManager) syncmaticaContext.getCommunicationManager();
        if (!comms.registerNewPlacement(placement)) {
            context.getSource().sendError(literal("Failed to register placement for " + file.getName()));
            return false;
        }
        sendFeedback(context, "Loaded server placement '" + placement.getName() + "'");
        return true;
    }

    private static List<File> listOrphanCandidates(final Context syncmaticaContext) {
        final File[] files = syncmaticaContext.getLitematicFolder()
                .listFiles((dir, name) -> name.endsWith(LITEMATIC_EXTENSION));
        if (files == null) {
            return Collections.emptyList();
        }
        final List<File> candidates = new ArrayList<>();
        for (final File file : files) {
            if (file.isFile() && !isRegisteredByName(syncmaticaContext, file)) {
                candidates.add(file);
            }
        }
        return candidates;
    }

    private static boolean isRegisteredByName(final Context syncmaticaContext, final File file) {
        try {
            final UUID hash = UUID.fromString(removeLitematicExtension(file.getName()));
            return hasPlacementHash(syncmaticaContext, hash);
        } catch (final IllegalArgumentException notAHashName) {
            return false;
        }
    }

    private static boolean hasPlacementHash(final Context syncmaticaContext, final UUID hash) {
        return syncmaticaContext.getSyncmaticManager().getAll().stream()
                .anyMatch(placement -> hash.equals(placement.getHash()));
    }

    private static String removeLitematicExtension(final String fileName) {
        return fileName.endsWith(LITEMATIC_EXTENSION)
                ? fileName.substring(0, fileName.length() - LITEMATIC_EXTENSION.length())
                : fileName;
    }

    private static SchematicPeek peekCached(final File file) {
        final String key = file.getAbsolutePath();
        final CachedPeek cached = PEEK_CACHE.get(key);
        if (cached != null && cached.lastModified == file.lastModified() && cached.length == file.length()) {
            return cached.peek;
        }
        final SchematicPeek peek = SchematicPeeker.peek(file);
        PEEK_CACHE.put(key, new CachedPeek(file.lastModified(), file.length(), peek));
        if (PEEK_CACHE.size() > 1024) {
            PEEK_CACHE.clear();
        }
        return peek;
    }

    private static void sendFeedback(final CommandContext<ServerCommandSource> context, final String message) {
//#if MC >= 12001
//$$         context.getSource().sendFeedback(() -> literal(message), true);
//#else
        context.getSource().sendFeedback(literal(message), true);
//#endif
    }

    private static final class CachedPeek {
        private final long lastModified;
        private final long length;
        private final SchematicPeek peek;

        private CachedPeek(final long lastModified, final long length, final SchematicPeek peek) {
            this.lastModified = lastModified;
            this.length = length;
            this.peek = peek;
        }
    }

    private static RequiredArgumentBuilder<ServerCommandSource, String> projectArgument() {
        return CommandManager.argument("project_name", string())
                .suggests((context, builder) -> {
                    final Context syncmaticaContext = Syncmatica.getContext(Syncmatica.SERVER_CONTEXT);
                    if (syncmaticaContext != null) {
                        syncmaticaContext.getSyncmaticManager().getAll().stream()
                                .map(ServerPlacement::getName)
                                .forEach(builder::suggest);
                    }
                    return builder.buildFuture();
                })
                .then(CommandManager.literal("setStockingarea")
                        .then(CommandManager.argument("pos1", BlockPosArgumentType.blockPos())
                                .then(CommandManager.argument("pos2", BlockPosArgumentType.blockPos())
                                        .executes(SyncmaticaCommand::handleSetStockingArea))))
                .then(CommandManager.literal("rescanBuild")
                        .requires(SyncmaticaCommand::hasCommandPermission)
                        .executes(SyncmaticaCommand::handleRescanBuild));
    }

    /**
     * Build progress is counted per chunk column and kept, on the grounds that a
     * block cannot change while its chunk is unloaded. Editing the world outside
     * the game breaks that assumption, and this throws the counts away so they
     * are taken again from what is actually there.
     */
    private static int handleRescanBuild(final CommandContext<ServerCommandSource> context) {
        final Context syncmaticaContext = Syncmatica.getContext(Syncmatica.SERVER_CONTEXT);
        if (syncmaticaContext == null || syncmaticaContext.getBuildService() == null) {
            context.getSource().sendError(literal("Syncmatica_r build service unavailable"));
            return 0;
        }
        final String projectName = context.getArgument("project_name", String.class);
        final Optional<ServerPlacement> placement = syncmaticaContext.getSyncmaticManager().getAll().stream()
                .filter(candidate -> candidate.getName().equals(projectName))
                .findFirst();
        if (!placement.isPresent()) {
            context.getSource().sendError(literal("Unknown Syncmatica_r project: " + projectName));
            return 0;
        }
        if (!syncmaticaContext.getBuildService().rescan(placement.get())) {
            context.getSource().sendError(literal("Build completion tracking is disabled"));
            return 0;
        }
        sendFeedback(context, "Build progress of '" + projectName + "' will be measured again");
        return 1;
    }

    private static int handleSetStockingArea(final CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        final Context syncmaticaContext = Syncmatica.getContext(Syncmatica.SERVER_CONTEXT);
        if (syncmaticaContext == null || syncmaticaContext.getMaterialService() == null) {
            context.getSource().sendError(literal("Syncmatica_r materials service unavailable"));
            return 0;
        }
        final MaterialService materialService = syncmaticaContext.getMaterialService();
        if (!materialService.isEnabled()) {
            context.getSource().sendError(literal("Material sharing is disabled"));
            return 0;
        }
        final String projectName = context.getArgument("project_name", String.class);
        final Optional<ServerPlacement> placement = syncmaticaContext.getSyncmaticManager().getAll().stream()
                .filter(candidate -> candidate.getName().equals(projectName))
                .findFirst();
        if (!placement.isPresent()) {
            context.getSource().sendError(literal("Unknown Syncmatica_r project: " + projectName));
            return 0;
        }
        if (!canManageStockingArea(context.getSource(), placement.get(), materialService)) {
            context.getSource().sendError(literal("You do not have permission to manage this stocking area"));
            return 0;
        }
        final BlockPos first = BlockPosArgumentType.getBlockPos(context, "pos1");
        final BlockPos second = BlockPosArgumentType.getBlockPos(context, "pos2");
        final String dimensionId = context.getSource().getWorld().getRegistryKey().getValue().toString();
        final StockingAreaDefinition definition = new StockingAreaDefinition(dimensionId, first, second);
        if (!materialService.isStockingAreaAllowed(definition)) {
            context.getSource().sendError(literal("Stocking area exceeds the configured block limit"));
            return 0;
        }
        materialService.setStockingArea(placement.get(), definition);

        materialService.scanNow(context.getSource().getServer(), placement.get());
//#if MC >= 12001
//$$         context.getSource().sendFeedback(() -> literal("Stocking area updated; scan scheduled for " + projectName), false);
//#else
        context.getSource().sendFeedback(literal("Stocking area updated; scan scheduled for " + projectName), false);
//#endif
        return 1;
    }

    private static boolean canManageStockingArea(final ServerCommandSource source,
                                                  final ServerPlacement placement,
                                                  final MaterialService materialService) {
        final Entity entity = source.getEntity();
        final ServerPlayerEntity player = entity instanceof ServerPlayerEntity
                ? (ServerPlayerEntity) entity
                : null;
        final UUID playerId = player == null ? null : SyncmaticaUtil.getProfileId(player.getGameProfile());
        final UUID ownerId = placement.getOwner() == null ? null : placement.getOwner().uuid;
        final boolean elevated = Permissions.check(
                source,
                PlacementAccessPolicy.MANAGE_PERMISSION,
                PlacementAccessPolicy.MANAGE_PERMISSION_LEVEL
        );
        return PlacementAccessPolicy.canManageStockingArea(
                playerId,
                ownerId,
                elevated,
                materialService.isOwnerStockingAreaManagementEnabled()
        );
    }

    private static LiteralArgumentBuilder<ServerCommandSource> defaultArgument() {

        return CommandManager.literal("default")
                .requires(SyncmaticaCommand::hasManagePermission)
                .then(CommandManager.literal("setStockingarea")
                        .then(CommandManager.argument("pos1", BlockPosArgumentType.blockPos())
                                .then(CommandManager.argument("pos2", BlockPosArgumentType.blockPos())
                                        .executes(SyncmaticaCommand::handleSetDefaultStockingArea))));
    }

    private static int handleSetDefaultStockingArea(final CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        final Context syncmaticaContext = Syncmatica.getContext(Syncmatica.SERVER_CONTEXT);
        if (syncmaticaContext == null || syncmaticaContext.getMaterialService() == null) {
            context.getSource().sendError(literal("Syncmatica_r materials service unavailable"));
            return 0;
        }
        final MaterialService materialService = syncmaticaContext.getMaterialService();
        if (!materialService.isEnabled()) {
            context.getSource().sendError(literal("Material sharing is disabled"));
            return 0;
        }
        final BlockPos first = BlockPosArgumentType.getBlockPos(context, "pos1");
        final BlockPos second = BlockPosArgumentType.getBlockPos(context, "pos2");
        final String dimensionId = context.getSource().getWorld().getRegistryKey().getValue().toString();
        final StockingAreaDefinition definition = new StockingAreaDefinition(dimensionId, first, second);
        if (!materialService.isStockingAreaAllowed(definition)) {
            context.getSource().sendError(literal("Stocking area exceeds the configured block limit"));
            return 0;
        }
        materialService.setDefaultStockingArea(definition);

        materialService.scanDefaultNow(context.getSource().getServer());
        syncmaticaContext.getSyncmaticManager().saveServerState();
//#if MC >= 12001
//$$         context.getSource().sendFeedback(() -> literal("Default stocking area updated; scan scheduled"), false);
//#else
        context.getSource().sendFeedback(literal("Default stocking area updated; scan scheduled"), false);
//#endif
        return 1;
    }

    private static boolean hasCommandPermission(final ServerCommandSource source) {
        return Permissions.check(source, COMMAND_PERMISSION, COMMAND_PERMISSION_LEVEL);
    }

    private static boolean hasLoadPermission(final ServerCommandSource source) {
        return hasCommandPermission(source)
                && Permissions.check(source, LOAD_PERMISSION, COMMAND_PERMISSION_LEVEL);
    }

    private static boolean hasManagePermission(final ServerCommandSource source) {
        return Permissions.check(
                source,
                PlacementAccessPolicy.MANAGE_PERMISSION,
                PlacementAccessPolicy.MANAGE_PERMISSION_LEVEL
        );
    }

    private static net.minecraft.text.Text literal(final String message) {
//#if MC >= 12001
//$$         return Text.literal(message);
//#else
        return new LiteralText(message);
//#endif
    }
}
