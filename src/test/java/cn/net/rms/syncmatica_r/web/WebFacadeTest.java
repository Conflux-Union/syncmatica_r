package cn.net.rms.syncmatica_r.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.FileStorage;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.SyncmaticManager;
import cn.net.rms.syncmatica_r.build_management.BuildRegion;
import cn.net.rms.syncmatica_r.communication.CommunicationManager;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.exchange.Exchange;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.material.MaterialKey;
import cn.net.rms.syncmatica_r.service.BuildService;
import cn.net.rms.syncmatica_r.service.IServiceConfiguration;
import cn.net.rms.syncmatica_r.service.MaterialService;
import cn.net.rms.syncmatica_r.util.IdentifierUtil;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WebFacadeTest {
    private static final MaterialKey STONE = new MaterialKey(IdentifierUtil.require("minecraft:stone"), "");

    @TempDir
    Path tempDir;

    @Test
    void projectAndDetailSnapshotsContainOnlyImmutableWebData() {
        final Context context = newServerContext();
        try {
            final PlayerIdentifier owner = player(context, "Owner");
            final ServerPlacement placement = placement(context, owner);
            final WebFacade facade = new WebFacade(context, "minecraft:overworld"::equals);

            final List<WebDtos.ProjectSummary> projects = facade.listProjects();
            final WebDtos.ProjectDetail detail = facade.getProject(placement.getId()).orElseThrow();

            assertEquals(placement.getId().toString(), projects.get(0).id());
            assertEquals("project", detail.name());
            assertEquals("minecraft:overworld", detail.position().dimension());
            assertEquals(owner.uuid.toString(), detail.owner().id());
            assertThrows(UnsupportedOperationException.class,
                    () -> projects.add(projects.get(0)));

            placement.setDisplayName("changed");
            owner.updatePlayerName("Changed Owner");
            assertEquals("project", detail.name());
            assertEquals("Owner", detail.owner().name());
            assertFalse(detail.getClass().getRecordComponents()[0].getType()
                    .isAssignableFrom(ServerPlacement.class));
        } finally {
            context.shutdown();
        }
    }

    @Test
    void materialAndSummarySnapshotsCalculateProgressAndDoNotLeakEntries() {
        final Context context = newServerContext();
        try {
            final ServerPlacement first = placement(context, player(context, "Owner"));
            first.getMaterialProgress().get(STONE).setStockingSupplied(25);
            final ServerPlacement second = placement(context, player(context, "Other"));
            second.getMaterialProgress().get(STONE).setStockingSupplied(75);
            final WebFacade facade = new WebFacade(
                    context,
                    "minecraft:overworld"::equals,
                    unused -> "block.minecraft.stone");

            final WebDtos.Material material = facade.getMaterials(first.getId()).get(0);
            final WebDtos.MaterialSummary summary = facade.getMaterialSummary().get(0);

            assertEquals(100L, material.required());
            assertEquals(25L, material.supplied());
            assertEquals(75L, material.missing());
            assertEquals(25, material.progressPercent());
            assertEquals("block.minecraft.stone", material.translationKey());
            assertEquals("Stone", material.fallbackName());
            assertEquals(200L, summary.required());
            assertEquals(100L, summary.supplied());
            assertEquals(50, summary.progressPercent());
            assertEquals("block.minecraft.stone", summary.translationKey());
            assertEquals("Stone", summary.fallbackName());
            assertFalse(((Object) material) instanceof cn.net.rms.syncmatica_r.material.MaterialProgressEntry);

            first.getMaterialProgress().get(STONE).setStockingSupplied(100);
            assertEquals(25L, material.supplied(), "the response must not be a live view");
        } finally {
            context.shutdown();
        }
    }

    @Test
    void buildProgressUsesOverflowSafeArithmetic() {
        final Context context = newServerContext();
        try {
            final ServerPlacement placement = placement(context, player(context, "Owner"));
            final Map<String, Long> regions = new LinkedHashMap<>();
            regions.put("huge", Long.MAX_VALUE);
            context.getBuildService().replaceRegions(placement.getId(), regions);
            final BuildRegion huge = placement.getBuildRegions().get("huge");
            huge.recordScan(Long.MAX_VALUE / 2L, 42L);

            final WebDtos.BuildRegion region = new WebFacade(
                    context, "minecraft:overworld"::equals).getBuildRegions(placement.getId()).get(0);

            assertEquals(Long.MAX_VALUE, region.requiredBlocks());
            assertEquals(Long.MAX_VALUE / 2L, region.placedBlocks());
            assertEquals(49, region.progressPercent());
            assertTrue(region.scanned());
            assertFalse(((Object) region) instanceof BuildRegion);
        } finally {
            context.shutdown();
        }
    }

    @Test
    void ordinaryPlayerClaimMutationsUseDesiredStateOperations() {
        final Context context = newServerContext();
        try {
            final ServerPlacement placement = placement(context, player(context, "Owner"));
            final PlayerIdentifier alice = player(context, "Alice");
            context.getBuildService().replaceRegions(placement.getId(), Map.of("roof", 100L));
            final WebFacade facade = new WebFacade(context, "minecraft:overworld"::equals);

            assertEquals(MaterialService.ClaimOutcome.CLAIMED,
                    facade.setMaterialClaim(placement.getId(), STONE, alice, true));
            assertEquals(MaterialService.ClaimOutcome.ALREADY_CLAIMED,
                    facade.setMaterialClaim(placement.getId(), STONE, alice, true));
            assertEquals(BuildService.ClaimOutcome.CLAIMED,
                    facade.setBuildClaim(placement.getId(), "roof", alice, true));
            assertEquals(BuildService.ClaimOutcome.ALREADY_CLAIMED,
                    facade.setBuildClaim(placement.getId(), "roof", alice, true));
        } finally {
            context.shutdown();
        }
    }

    @Test
    void myClaimsAggregateOnlyTheSessionPlayerAcrossProjects() {
        final Context context = newServerContext();
        try {
            final ServerPlacement first = placement(context, player(context, "Owner"));
            first.setDisplayName("alpha");
            first.getMaterialProgress().get(STONE).setStockingSupplied(25);
            final ServerPlacement second = placement(context, player(context, "Other"));
            second.setDisplayName("beta");
            context.getBuildService().replaceRegions(first.getId(), Map.of("roof", 100L));
            final PlayerIdentifier alice = player(context, "Alice");
            final PlayerIdentifier bob = player(context, "Bob");
            final WebFacade facade = new WebFacade(
                    context,
                    "minecraft:overworld"::equals,
                    unused -> "block.minecraft.stone");

            assertEquals(MaterialService.ClaimOutcome.CLAIMED,
                    facade.setMaterialClaim(first.getId(), STONE, alice, true));
            assertEquals(MaterialService.ClaimOutcome.CLAIMED,
                    facade.setMaterialClaim(second.getId(), STONE, bob, true));
            assertEquals(BuildService.ClaimOutcome.CLAIMED,
                    facade.setBuildClaim(first.getId(), "roof", alice, true));

            final WebDtos.MyClaims claims = facade.getMyClaims(alice.uuid);
            final WebDtos.MyClaims stranger = facade.getMyClaims(
                    UUID.nameUUIDFromBytes("Stranger".getBytes()));

            assertEquals(1, claims.materials().size());
            assertEquals(first.getId().toString(), claims.materials().get(0).projectId());
            assertEquals("alpha", claims.materials().get(0).projectName());
            assertEquals("minecraft:stone", claims.materials().get(0).itemId());
            assertEquals("Stone", claims.materials().get(0).fallbackName());
            assertEquals(75L, claims.materials().get(0).missing());
            assertEquals(25, claims.materials().get(0).progressPercent());
            assertEquals(1, claims.regions().size());
            assertEquals("roof", claims.regions().get(0).name());
            assertEquals(first.getId().toString(), claims.regions().get(0).projectId());
            assertTrue(stranger.materials().isEmpty());
            assertTrue(stranger.regions().isEmpty());
            assertThrows(UnsupportedOperationException.class,
                    () -> claims.materials().add(claims.materials().get(0)));
        } finally {
            context.shutdown();
        }
    }

    @Test
    void stockingAreaUsesOwnerPolicyAndRejectsUnloadedDimensions() {
        final Context context = newServerContext();
        try {
            final PlayerIdentifier owner = player(context, "Owner");
            final PlayerIdentifier stranger = player(context, "Stranger");
            final ServerPlacement placement = placement(context, owner);
            final WebFacade facade = new WebFacade(context, "minecraft:overworld"::equals);

            assertEquals(WebFacade.StockingAreaOutcome.FORBIDDEN,
                    facade.setStockingArea(placement.getId(), stranger, false,
                            "minecraft:overworld", 0, 0, 0, 1, 1, 1));
            assertEquals(WebFacade.StockingAreaOutcome.DIMENSION_NOT_LOADED,
                    facade.setStockingArea(placement.getId(), owner, false,
                            "minecraft:the_nether", 0, 0, 0, 1, 1, 1));
            assertNull(placement.getStockingArea());

            assertEquals(WebFacade.StockingAreaOutcome.UPDATED,
                    facade.setStockingArea(placement.getId(), owner, false,
                            "minecraft:overworld", 1, 2, 3, 4, 5, 6));
            final WebDtos.StockingArea area = facade.getStockingArea(placement.getId()).orElseThrow();
            assertEquals("minecraft:overworld", area.dimension());
            assertEquals(1, area.minX());
            assertEquals(6, area.maxZ());
            assertNotNull(placement.getStockingArea());
        } finally {
            context.shutdown();
        }
    }

    @Test
    void stockingAreaReusesLimitsAndConfigurableOwnerPolicy() {
        final Context context = newServerContext();
        try {
            final PlayerIdentifier owner = player(context, "Owner");
            final ServerPlacement placement = placement(context, owner);
            final WebFacade facade = new WebFacade(context, "minecraft:overworld"::equals);

            assertEquals(WebFacade.StockingAreaOutcome.TOO_LARGE,
                    facade.setStockingArea(placement.getId(), owner, false,
                            "minecraft:overworld", Integer.MIN_VALUE, 0, Integer.MIN_VALUE,
                            Integer.MAX_VALUE, 0, Integer.MAX_VALUE));

            context.getMaterialService().configure(new OwnerManagementDisabledConfiguration());
            assertEquals(WebFacade.StockingAreaOutcome.FORBIDDEN,
                    facade.setStockingArea(placement.getId(), owner, false,
                            "minecraft:overworld", 0, 0, 0, 1, 1, 1));
            assertEquals(WebFacade.StockingAreaOutcome.UPDATED,
                    facade.setStockingArea(placement.getId(), owner, true,
                            "minecraft:overworld", 0, 0, 0, 1, 1, 1));
        } finally {
            context.shutdown();
        }
    }

    @Test
    void disabledFeaturesKeepExplicitOutcomes() {
        final Context context = newServerContext();
        try {
            final PlayerIdentifier owner = player(context, "Owner");
            final ServerPlacement placement = placement(context, owner);
            context.getBuildService().replaceRegions(placement.getId(), Map.of("roof", 100L));
            context.getMaterialService().configure(new DisabledConfiguration());
            context.getBuildService().configure(new DisabledConfiguration());
            final WebFacade facade = new WebFacade(context, dimension -> true);

            assertEquals(MaterialService.ClaimOutcome.DISABLED,
                    facade.setMaterialClaim(placement.getId(), STONE, owner, true));
            assertEquals(BuildService.ClaimOutcome.DISABLED,
                    facade.setBuildClaim(placement.getId(), "roof", owner, true));
            assertEquals(WebFacade.StockingAreaOutcome.DISABLED,
                    facade.setStockingArea(placement.getId(), owner, false,
                            "minecraft:overworld", 0, 0, 0, 1, 1, 1));
        } finally {
            context.shutdown();
        }
    }

    private ServerPlacement placement(final Context context, final PlayerIdentifier owner) {
        final ServerPlacement placement = new ServerPlacement(
                UUID.randomUUID(), "project", UUID.randomUUID(), owner);
        placement.move("minecraft:overworld", new BlockPos(10, 20, 30),
                BlockRotation.NONE, BlockMirror.NONE);
        placement.getMaterialProgress().getOrCreate(STONE, 100);
        context.getSyncmaticManager().addPlacement(placement);
        return placement;
    }

    private PlayerIdentifier player(final Context context, final String name) {
        return context.getPlayerIdentifierProvider().createOrGet(UUID.nameUUIDFromBytes(name.getBytes()), name);
    }

    private Context newServerContext() {
        return new Context(
                new FileStorage(),
                new StubCommunicationManager(),
                new SyncmaticManager(),
                true,
                tempDir.resolve("litematics").toFile(),
                true,
                tempDir.toFile()
        );
    }

    private static class EmptyConfiguration implements IServiceConfiguration {
        @Override
        public void loadBoolean(final String key, final java.util.function.Consumer<Boolean> loader) {
        }

        @Override
        public void saveBoolean(final String key, final Boolean value) {
        }

        @Override
        public void loadInteger(final String key, final java.util.function.IntConsumer loader) {
        }

        @Override
        public void saveInteger(final String key, final Integer value) {
        }
    }

    private static final class DisabledConfiguration extends EmptyConfiguration {
        @Override
        public void loadBoolean(final String key, final java.util.function.Consumer<Boolean> loader) {
            if ("enabled".equals(key)) {
                loader.accept(false);
            }
        }
    }

    private static final class OwnerManagementDisabledConfiguration extends EmptyConfiguration {
        @Override
        public void loadBoolean(final String key, final java.util.function.Consumer<Boolean> loader) {
            if ("allow_owner_stocking_area_management".equals(key)) {
                loader.accept(false);
            }
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
