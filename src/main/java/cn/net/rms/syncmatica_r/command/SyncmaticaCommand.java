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
import cn.net.rms.syncmatica_r.service.ConfigOption;
import cn.net.rms.syncmatica_r.service.ConfigRegistry;
import cn.net.rms.syncmatica_r.util.SyncmaticaUtil;
import cn.net.rms.syncmatica_r.web.WebPasswordProtocol;
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
import java.io.IOException;
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
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;

public final class SyncmaticaCommand {
    private static final String COMMAND_PERMISSION = "syncmatica_r.command";
    private static final String LOAD_PERMISSION = "syncmatica_r.command.load";
    private static final String CONFIG_PERMISSION = "syncmatica_r.config";
    private static final int COMMAND_PERMISSION_LEVEL = 2;
    private static final String LITEMATIC_EXTENSION = ".litematic";
    private static final Map<String, CachedPeek> PEEK_CACHE = new HashMap<>();

    private SyncmaticaCommand() {
    }

    public static void register(final CommandDispatcher<ServerCommandSource> dispatcher) {
        final LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("syncmatica_r")
                .then(loadArgument())
                .then(configArgument())
                .then(webArgument())
                .then(projectArgument())
                .then(defaultArgument());
        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> webArgument() {
        return CommandManager.literal("web")
                .then(CommandManager.literal("setpassword")
                        .then(CommandManager.argument("password", greedyString())
                                .executes(SyncmaticaCommand::handleWebSetPassword)))
                .then(CommandManager.literal("disable")
                        .executes(SyncmaticaCommand::handleWebDisablePassword));
    }

    private static int handleWebSetPassword(
            final CommandContext<ServerCommandSource> commandContext
    ) {
        try {
            final char[] password =
                    commandContext.getArgument("password", String.class).toCharArray();
            return updateWebPassword(
                    commandContext,
                    WebPasswordProtocol.decode(WebPasswordProtocol.encodeSet(password))
            );
        } catch (final IllegalArgumentException invalidPassword) {
            commandContext.getSource().sendError(literal(invalidPassword.getMessage()));
            return 0;
        }
    }

    private static int handleWebDisablePassword(
            final CommandContext<ServerCommandSource> commandContext
    ) {
        return updateWebPassword(
                commandContext,
                WebPasswordProtocol.decode(WebPasswordProtocol.encodeDisable())
        );
    }

    private static int updateWebPassword(
            final CommandContext<ServerCommandSource> commandContext,
            final WebPasswordProtocol.Request request
    ) {
        final ServerCommandSource source = commandContext.getSource();
        final Entity entity = source.getEntity();
        if (!(entity instanceof ServerPlayerEntity)) {
            request.close();
            source.sendError(literal("This command is only available to players"));
            return 0;
        }
        final Context syncmaticaContext = Syncmatica.getContext(Syncmatica.SERVER_CONTEXT);
        if (syncmaticaContext == null
                || syncmaticaContext.getWebService() == null
                || !syncmaticaContext.getWebService().isPasswordUpdateAvailable()) {
            request.close();
            source.sendError(literal("Syncmatica_r web service is disabled"));
            return 0;
        }
        final ServerPlayerEntity player = (ServerPlayerEntity) entity;
        syncmaticaContext.getWebService().updatePassword(
                SyncmaticaUtil.getProfileId(player.getGameProfile()),
                SyncmaticaUtil.getProfileName(player.getGameProfile()),
                request,
                result -> sendWebPasswordResult(commandContext, result)
        );
        return 1;
    }

    private static void sendWebPasswordResult(
            final CommandContext<ServerCommandSource> context,
            final WebPasswordProtocol.Result result
    ) {
        switch (result) {
            case PASSWORD_SET:
                sendPrivateFeedback(context,
                        "Web password updated. Existing sessions were signed out.");
                break;
            case PASSWORD_DISABLED:
                sendPrivateFeedback(context,
                        "Web password disabled. Existing sessions were signed out.");
                break;
            case BUSY:
                context.getSource().sendError(literal(
                        "The authentication service is busy. Try again."));
                break;
            case UNAVAILABLE:
                context.getSource().sendError(literal(
                        "Web password updates are unavailable."));
                break;
            case INVALID_REQUEST:
                context.getSource().sendError(literal(
                        "Password must contain 10–128 valid UTF-8 characters."));
                break;
            default:
                context.getSource().sendError(literal(
                        "Could not update the Web password."));
        }
    }

    private static LiteralArgumentBuilder<ServerCommandSource> configArgument() {
        return CommandManager.literal("config")
                .requires(SyncmaticaCommand::hasConfigPermission)
                .then(CommandManager.literal("list")
                        .executes(context -> handleConfigList(context, null))
                        .then(CommandManager.argument("section", string())
                                .suggests(SyncmaticaCommand::suggestConfigSections)
                                .executes(context -> handleConfigList(
                                        context, context.getArgument("section", String.class)))))
                .then(configReadArgument("get", SyncmaticaCommand::handleConfigGet))
                .then(configReadArgument("reset", SyncmaticaCommand::handleConfigReset))
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("section", string())
                                .suggests(SyncmaticaCommand::suggestConfigSections)
                                .then(CommandManager.argument("key", string())
                                        .suggests(SyncmaticaCommand::suggestConfigKeys)
                                        .then(CommandManager.argument("value", string())
                                                .suggests(SyncmaticaCommand::suggestConfigValues)
                                                .executes(SyncmaticaCommand::handleConfigSet)))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> configReadArgument(
            final String operation,
            final com.mojang.brigadier.Command<ServerCommandSource> handler
    ) {
        return CommandManager.literal(operation)
                .then(CommandManager.argument("section", string())
                        .suggests(SyncmaticaCommand::suggestConfigSections)
                        .then(CommandManager.argument("key", string())
                                .suggests(SyncmaticaCommand::suggestConfigKeys)
                                .executes(handler)));
    }

    private static CompletableFuture<Suggestions> suggestConfigSections(
            final CommandContext<ServerCommandSource> context,
            final SuggestionsBuilder builder
    ) {
        final ConfigRegistry registry = configRegistry();
        if (registry != null) {
            registry.sections().forEach(builder::suggest);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestConfigKeys(
            final CommandContext<ServerCommandSource> context,
            final SuggestionsBuilder builder
    ) {
        final ConfigRegistry registry = configRegistry();
        if (registry != null) {
            registry.keys(context.getArgument("section", String.class)).forEach(builder::suggest);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestConfigValues(
            final CommandContext<ServerCommandSource> context,
            final SuggestionsBuilder builder
    ) {
        final ConfigRegistry registry = configRegistry();
        if (registry == null) {
            return builder.buildFuture();
        }
        final ConfigOption<?> option = registry.find(
                context.getArgument("section", String.class),
                context.getArgument("key", String.class)
        );
        if (option != null && option.getDefaultValue() instanceof Boolean) {
            builder.suggest("true");
            builder.suggest("false");
        }
        return builder.buildFuture();
    }

    private static ConfigRegistry configRegistry() {
        final Context context = Syncmatica.getContext(Syncmatica.SERVER_CONTEXT);
        return context == null ? null : context.getConfigRegistry();
    }

    private static ConfigCommandLogic configLogic() {
        final Context context = Syncmatica.getContext(Syncmatica.SERVER_CONTEXT);
        if (context == null || context.getConfigRegistry() == null || context.getConfigStore() == null) {
            throw new IllegalStateException("Syncmatica_r server configuration unavailable");
        }
        return new ConfigCommandLogic(context.getConfigRegistry(), context.getConfigStore());
    }

    private static int handleConfigList(
            final CommandContext<ServerCommandSource> context,
            final String section
    ) {
        try {
            final List<String> entries = configLogic().list(section);
            entries.forEach(entry -> sendPrivateFeedback(context, entry));
            return entries.isEmpty() ? 0 : 1;
        } catch (final IllegalArgumentException | IllegalStateException exception) {
            context.getSource().sendError(literal(exception.getMessage()));
            return 0;
        }
    }

    private static int handleConfigGet(final CommandContext<ServerCommandSource> context) {
        try {
            sendPrivateFeedback(context, configLogic().get(
                    context.getArgument("section", String.class),
                    context.getArgument("key", String.class)
            ));
            return 1;
        } catch (final IllegalArgumentException | IllegalStateException exception) {
            context.getSource().sendError(literal(exception.getMessage()));
            return 0;
        }
    }

    private static int handleConfigSet(final CommandContext<ServerCommandSource> context) {
        try {
            sendPrivateFeedback(context, configLogic().set(
                    context.getArgument("section", String.class),
                    context.getArgument("key", String.class),
                    context.getArgument("value", String.class)
            ));
            return 1;
        } catch (final IOException | IllegalArgumentException | IllegalStateException exception) {
            context.getSource().sendError(literal("Failed to update configuration: " + exception.getMessage()));
            return 0;
        }
    }

    private static int handleConfigReset(final CommandContext<ServerCommandSource> context) {
        try {
            sendPrivateFeedback(context, configLogic().reset(
                    context.getArgument("section", String.class),
                    context.getArgument("key", String.class)
            ));
            return 1;
        } catch (final IOException | IllegalArgumentException | IllegalStateException exception) {
            context.getSource().sendError(literal("Failed to reset configuration: " + exception.getMessage()));
            return 0;
        }
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

    private static void sendPrivateFeedback(
            final CommandContext<ServerCommandSource> context,
            final String message
    ) {
//#if MC >= 12001
//$$         context.getSource().sendFeedback(() -> literal(message), false);
//#else
        context.getSource().sendFeedback(literal(message), false);
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

    private static boolean hasConfigPermission(final ServerCommandSource source) {
        return Permissions.check(source, CONFIG_PERMISSION, COMMAND_PERMISSION_LEVEL);
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
