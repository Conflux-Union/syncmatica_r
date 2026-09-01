package cn.net.rms.syncmatica_r.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.IFileStorage;
import cn.net.rms.syncmatica_r.LocalLitematicState;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.SyncmaticManager;
import cn.net.rms.syncmatica_r.communication.CommunicationManager;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.exchange.Exchange;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.material.StockingAreaDefinition;
import com.google.gson.JsonObject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MaterialServiceConfigurationTest {
    @TempDir
    Path tempDir;

    @Test
    void publishesResourceLimitDefaultsAndClampsConfiguredSchematicSize() {
        final MaterialService service = new MaterialService();
        try {
            final JsonObject defaults = new JsonObject();
            service.getDefaultConfiguration(new JsonConfiguration(defaults));

            assertEquals(MaterialService.MAX_STOCKING_AREA_BLOCKS_DEFAULT,
                    defaults.get("max_stocking_area_blocks").getAsInt());
            assertEquals(MaterialService.MAX_SCHEMATIC_BLOCKS_DEFAULT,
                    defaults.get("max_schematic_blocks").getAsInt());

            final JsonObject configured = new JsonObject();
            configured.addProperty("max_schematic_megabytes", Integer.MAX_VALUE);
            service.configure(new JsonConfiguration(configured));

            assertEquals(64L * 1024L * 1024L, service.getMaxSchematicBytes());
            assertTrue(service.isEnabled());
        } finally {
            service.shutdown();
        }
    }

    @Test
    void allowsPlacementOwnersToManageStockingAreasByDefault() {
        final MaterialService service = new MaterialService();
        try {
            final JsonObject defaults = new JsonObject();
            service.getDefaultConfiguration(new JsonConfiguration(defaults));

            assertTrue(defaults.get("allow_owner_stocking_area_management").getAsBoolean());
        } finally {
            service.shutdown();
        }
    }

    @Test
    void canDisablePlacementOwnerStockingAreaManagement() {
        final MaterialService service = new MaterialService();
        try {
            final JsonObject configured = new JsonObject();
            configured.addProperty("allow_owner_stocking_area_management", false);
            service.configure(new JsonConfiguration(configured));

            assertFalse(service.isOwnerStockingAreaManagementEnabled());
        } finally {
            service.shutdown();
        }
    }

    @Test
    void extractionSettingsInvalidateInflightWorkAndQueueFreshWork() throws Exception {
        final Context context = newServerContext();
        try {
            final ServerPlacement placement = addPlacement(context, "build");
            UUID token = context.getMaterialService().pendingExtractionToken(placement.getId());
            assertNotNull(token);

            context.getConfigStore().set("materials", "include_container_contents", "true");
            UUID refreshed = context.getMaterialService().pendingExtractionToken(placement.getId());
            assertNotNull(refreshed);
            assertNotEquals(token, refreshed);

            context.getConfigStore().set("materials", "max_schematic_megabytes", "32");
            token = context.getMaterialService().pendingExtractionToken(placement.getId());
            assertNotEquals(refreshed, token);

            context.getConfigStore().set("materials", "max_schematic_blocks", "9000000");
            refreshed = context.getMaterialService().pendingExtractionToken(placement.getId());
            assertNotEquals(token, refreshed);
        } finally {
            context.shutdown();
        }
    }

    @Test
    void enablingMaterialsQueuesFreshExtractionForEveryAttachedPlacement() throws Exception {
        final Context context = newServerContext();
        try {
            final ServerPlacement first = addPlacement(context, "first");
            final ServerPlacement second = addPlacement(context, "second");
            final UUID firstToken = context.getMaterialService().pendingExtractionToken(first.getId());
            final UUID secondToken = context.getMaterialService().pendingExtractionToken(second.getId());

            context.getConfigStore().set("materials", "enabled", "false");
            context.getConfigStore().set("materials", "enabled", "true");

            assertNotEquals(firstToken,
                    context.getMaterialService().pendingExtractionToken(first.getId()));
            assertNotEquals(secondToken,
                    context.getMaterialService().pendingExtractionToken(second.getId()));
        } finally {
            context.shutdown();
        }
    }

    @Test
    void changingStockingLimitCancelsScansWithoutDiscardingDefinitions() throws Exception {
        final Context context = newServerContext();
        try {
            final ServerPlacement placement = addPlacement(context, "build");
            final StockingAreaDefinition area = new StockingAreaDefinition(
                    "minecraft:overworld", BlockPos.ORIGIN, new BlockPos(10, 10, 10));
            context.getMaterialService().setStockingArea(placement, area);
            context.getMaterialService().setDefaultStockingArea(area);
            context.getMaterialService().scanDefaultNow(null);
            assertTrue(context.getMaterialService().hasDefaultStockingScan());

            context.getConfigStore().set("materials", "max_stocking_area_blocks", "1024");

            assertSame(area, context.getMaterialService().getStockingArea(placement.getId()));
            assertSame(area, context.getMaterialService().getDefaultStockingArea());
            assertFalse(context.getMaterialService().hasDefaultStockingScan());
            assertFalse(context.getMaterialService().isStockingAreaAllowed(area));
        } finally {
            context.shutdown();
        }
    }

    private Context newServerContext() throws Exception {
        final Path schematic = tempDir.resolve("schematic.litematic");
        Files.write(schematic, new byte[] { 1 });
        final Context context = new Context(
                new StaticFileStorage(schematic.toFile()),
                new StubCommunicationManager(),
                new SyncmaticManager(),
                true,
                tempDir.resolve("litematics").toFile(),
                true,
                tempDir.toFile()
        );
        context.startup();
        return context;
    }

    private ServerPlacement addPlacement(final Context context, final String name) {
        final ServerPlacement placement = new ServerPlacement(
                UUID.randomUUID(), name, UUID.randomUUID(), PlayerIdentifier.MISSING_PLAYER);
        placement.move("minecraft:overworld", BlockPos.ORIGIN, BlockRotation.NONE, BlockMirror.NONE);
        context.getSyncmaticManager().addPlacement(placement);
        return placement;
    }

    private static final class StaticFileStorage implements IFileStorage {
        private final File file;

        private StaticFileStorage(final File file) {
            this.file = file;
        }

        @Override
        public LocalLitematicState getLocalState(final ServerPlacement placement) {
            return LocalLitematicState.LOCAL_LITEMATIC_PRESENT;
        }

        @Override
        public File createLocalLitematic(final ServerPlacement placement) {
            return file;
        }

        @Override
        public File getLocalLitematic(final ServerPlacement placement) {
            return file;
        }

        @Override
        public void setContext(final Context context) {
        }
    }

    private static final class StubCommunicationManager extends CommunicationManager {
        @Override
        protected void handle(final ExchangeTarget source, final Identifier id, final PacketByteBuf packetBuf) {
        }

        @Override
        protected void handleExchange(final Exchange exchange) {
        }
    }
}
