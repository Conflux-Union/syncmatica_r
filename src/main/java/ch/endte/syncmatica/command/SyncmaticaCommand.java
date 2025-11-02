package ch.endte.syncmatica.command;

import ch.endte.syncmatica.Context;
import ch.endte.syncmatica.ServerPlacement;
import ch.endte.syncmatica.Syncmatica;
import ch.endte.syncmatica.material.StockingAreaDefinition;
import ch.endte.syncmatica.service.MaterialService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

import static com.mojang.brigadier.arguments.StringArgumentType.string;

public final class SyncmaticaCommand {
    private SyncmaticaCommand() {
    }

    public static void register(final CommandDispatcher<ServerCommandSource> dispatcher) {
        final LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("syncmatica")
                .requires(source -> source.hasPermissionLevel(2))
                .then(projectArgument())
                .then(defaultArgument());
        dispatcher.register(root);
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
                                        .executes(SyncmaticaCommand::handleSetStockingArea))));
    }

    private static int handleSetStockingArea(final CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        final Context syncmaticaContext = Syncmatica.getContext(Syncmatica.SERVER_CONTEXT);
        if (syncmaticaContext == null || syncmaticaContext.getMaterialService() == null) {
            context.getSource().sendError(new LiteralText("Syncmatica materials service unavailable"));
            return 0;
        }
        final MaterialService materialService = syncmaticaContext.getMaterialService();
        if (!materialService.isEnabled()) {
            context.getSource().sendError(new LiteralText("Material sharing is disabled"));
            return 0;
        }
        final String projectName = context.getArgument("project_name", String.class);
        final Optional<ServerPlacement> placement = syncmaticaContext.getSyncmaticManager().getAll().stream()
                .filter(candidate -> candidate.getName().equals(projectName))
                .findFirst();
        if (!placement.isPresent()) {
            context.getSource().sendError(new LiteralText("Unknown Syncmatica project: " + projectName));
            return 0;
        }
        final BlockPos first = BlockPosArgumentType.getBlockPos(context, "pos1");
        final BlockPos second = BlockPosArgumentType.getBlockPos(context, "pos2");
        final String dimensionId = context.getSource().getWorld().getRegistryKey().getValue().toString();
        final StockingAreaDefinition definition = new StockingAreaDefinition(dimensionId, first, second);
        materialService.setStockingArea(placement.get(), definition);

        materialService.scanNow(context.getSource().getServer(), placement.get());
        context.getSource().sendFeedback(new LiteralText("Stocking area updated for " + projectName), false);
        return 1;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> defaultArgument() {

        return CommandManager.literal("default")
                .then(CommandManager.literal("setStockingarea")
                        .then(CommandManager.argument("pos1", BlockPosArgumentType.blockPos())
                                .then(CommandManager.argument("pos2", BlockPosArgumentType.blockPos())
                                        .executes(SyncmaticaCommand::handleSetDefaultStockingArea))));
    }

    private static int handleSetDefaultStockingArea(final CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        final Context syncmaticaContext = Syncmatica.getContext(Syncmatica.SERVER_CONTEXT);
        if (syncmaticaContext == null || syncmaticaContext.getMaterialService() == null) {
            context.getSource().sendError(new LiteralText("Syncmatica materials service unavailable"));
            return 0;
        }
        final MaterialService materialService = syncmaticaContext.getMaterialService();
        if (!materialService.isEnabled()) {
            context.getSource().sendError(new LiteralText("Material sharing is disabled"));
            return 0;
        }
        final BlockPos first = BlockPosArgumentType.getBlockPos(context, "pos1");
        final BlockPos second = BlockPosArgumentType.getBlockPos(context, "pos2");
        final String dimensionId = context.getSource().getWorld().getRegistryKey().getValue().toString();
        final StockingAreaDefinition definition = new StockingAreaDefinition(dimensionId, first, second);
        materialService.setDefaultStockingArea(definition);

        materialService.scanDefaultNow(context.getSource().getServer());
        syncmaticaContext.getSyncmaticManager().saveServerState();
        context.getSource().sendFeedback(new LiteralText("Default stocking area updated"), false);
        return 1;
    }
}
