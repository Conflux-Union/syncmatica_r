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
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.string;

/**
 * Registers the /syncmatica command surface.
 */
public final class SyncmaticaCommand {
    private SyncmaticaCommand() {
    }

    public static void register(final CommandDispatcher<ServerCommandSource> dispatcher) {
        final LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("syncmatica")
                .requires(source -> source.hasPermissionLevel(2))
                .then(projectArgument());
        dispatcher.register(root);
    }

    private static RequiredArgumentBuilder<ServerCommandSource, String> projectArgument() {
        return CommandManager.argument("project_name", string())
                .then(CommandManager.literal("setStockingarea")
                        .then(vectorArgument("x1", "y1", "z1")
                                .then(vectorArgument("x2", "y2", "z2")
                                        .executes(SyncmaticaCommand::handleSetStockingArea))));
    }

    private static RequiredArgumentBuilder<ServerCommandSource, Integer> vectorArgument(final String xName, final String yName, final String zName) {
        return CommandManager.argument(xName, integer())
                .then(CommandManager.argument(yName, integer())
                        .then(CommandManager.argument(zName, integer())));
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
        final BlockPos first = new BlockPos(
                context.getArgument("x1", Integer.class),
                context.getArgument("y1", Integer.class),
                context.getArgument("z1", Integer.class)
        );
        final BlockPos second = new BlockPos(
                context.getArgument("x2", Integer.class),
                context.getArgument("y2", Integer.class),
                context.getArgument("z2", Integer.class)
        );
        final String dimensionId = context.getSource().getWorld().getRegistryKey().getValue().toString();
        final StockingAreaDefinition definition = new StockingAreaDefinition(dimensionId, first, second);
        materialService.setStockingArea(placement.get(), definition);
        // Trigger a synchronous scan to honour the command contract.
        materialService.scanNow(context.getSource().getServer(), placement.get());
        context.getSource().sendFeedback(new LiteralText("Stocking area updated for " + projectName), false);
        return 1;
    }
}
