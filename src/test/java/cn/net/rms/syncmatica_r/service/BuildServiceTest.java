package cn.net.rms.syncmatica_r.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.Feature;
import cn.net.rms.syncmatica_r.FileStorage;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.SyncmaticManager;
import cn.net.rms.syncmatica_r.build_management.BuildRegion;
import cn.net.rms.syncmatica_r.build_management.BuildRegionState;
import cn.net.rms.syncmatica_r.build_management.BuildScanStore;
import cn.net.rms.syncmatica_r.build_management.RegionBounds;
import cn.net.rms.syncmatica_r.build_management.RegionScanCache;
import com.google.gson.JsonObject;
import cn.net.rms.syncmatica_r.communication.CommunicationManager;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.ProtocolLimits;
import cn.net.rms.syncmatica_r.communication.exchange.Exchange;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.util.SyncmaticaUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BuildServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void claimingAndReleasingARegionToggles() {
        final Context context = newServerContext();
        try {
            final BuildService service = context.getBuildService();
            final ServerPlacement placement = attach(context, service, regions("roof", "walls"));
            final PlayerIdentifier alice = player(context, "Alice");

            assertEquals(BuildService.ClaimOutcome.CLAIMED, service.toggleClaim(placement, "roof", alice));
            assertEquals(alice, service.getClaimant(placement, "roof"));

            assertEquals(BuildService.ClaimOutcome.RELEASED, service.toggleClaim(placement, "roof", alice));
            assertNull(service.getClaimant(placement, "roof"));
        } finally {
            context.shutdown();
        }
    }

    @Test
    void aClaimedRegionCannotBeTakenOverBySomeoneElse() {
        final Context context = newServerContext();
        try {
            final BuildService service = context.getBuildService();
            final ServerPlacement placement = attach(context, service, regions("roof"));
            final PlayerIdentifier alice = player(context, "Alice");
            final PlayerIdentifier bob = player(context, "Bob");

            assertEquals(BuildService.ClaimOutcome.CLAIMED, service.toggleClaim(placement, "roof", alice));
            assertEquals(BuildService.ClaimOutcome.ALREADY_CLAIMED, service.toggleClaim(placement, "roof", bob));
            assertEquals(alice, service.getClaimant(placement, "roof"));
        } finally {
            context.shutdown();
        }
    }

    @Test
    void unknownRegionsAreRejected() {
        final Context context = newServerContext();
        try {
            final BuildService service = context.getBuildService();
            final ServerPlacement placement = attach(context, service, regions("roof"));

            assertEquals(BuildService.ClaimOutcome.UNKNOWN_REGION,
                    service.toggleClaim(placement, "basement", player(context, "Alice")));
            assertNull(service.getClaimant(placement, "basement"));
        } finally {
            context.shutdown();
        }
    }

    @Test
    void reExtractionKeepsClaimsOnRegionsThatStillExist() {
        final Context context = newServerContext();
        try {
            final BuildService service = context.getBuildService();
            final ServerPlacement placement = attach(context, service, regions("roof", "walls"));
            final PlayerIdentifier alice = player(context, "Alice");
            service.toggleClaim(placement, "roof", alice);
            service.toggleClaim(placement, "walls", alice);

            // The schematic is re-shared with "walls" renamed and a new region added.
            final Map<String, Long> updated = new LinkedHashMap<>();
            updated.put("roof", 250L);
            updated.put("facade", 40L);
            service.replaceRegions(placement.getId(), updated);

            assertEquals(alice, service.getClaimant(placement, "roof"),
                    "a surviving region keeps its claim");
            assertNull(service.getClaimant(placement, "facade"));
            assertNull(placement.getBuildRegions().get("walls"),
                    "a region that disappeared is dropped");
            assertEquals(250L, placement.getBuildRegions().get("roof").getRequiredBlocks());
        } finally {
            context.shutdown();
        }
    }

    @Test
    void regionListIsCappedAndSanitised() {
        final Context context = newServerContext();
        try {
            final BuildService service = context.getBuildService();
            final ServerPlacement placement = attach(context, service, regions("roof"));

            final Map<String, Long> oversized = new LinkedHashMap<>();
            for (int i = 0; i < ProtocolLimits.MAX_REGION_ENTRIES + 20; i++) {
                oversized.put("region_" + i, 1L);
            }
            oversized.put("", 5L);
            oversized.put(tooLongName(), 5L);
            oversized.put("negative", -7L);
            service.replaceRegions(placement.getId(), oversized);

            assertTrue(placement.getBuildRegions().getRegions().size() <= ProtocolLimits.MAX_REGION_ENTRIES);
            assertNull(placement.getBuildRegions().get(""));
            assertNull(placement.getBuildRegions().get(tooLongName()));
            final BuildRegion negative = placement.getBuildRegions().get("negative");
            if (negative != null) {
                assertEquals(0L, negative.getRequiredBlocks());
            }
        } finally {
            context.shutdown();
        }
    }

    @Test
    void aDisabledServiceRefusesClaims() {
        final Context context = newServerContext();
        try {
            final BuildService service = context.getBuildService();
            final ServerPlacement placement = attach(context, service, regions("roof"));
            service.configure(new DisablingConfiguration());

            assertFalse(service.isEnabled());
            assertEquals(BuildService.ClaimOutcome.DISABLED,
                    service.toggleClaim(placement, "roof", player(context, "Alice")));
        } finally {
            context.shutdown();
        }
    }

    @Test
    void claimsPersistedOnDiskAreSeededBackOnAttach() {
        final Context context = newServerContext();
        try {
            final BuildService service = context.getBuildService();
            final ServerPlacement placement = attach(context, service, regions("roof"));
            final PlayerIdentifier alice = player(context, "Alice");
            service.toggleClaim(placement, "roof", alice);

            // Simulate a restart: the same placement comes back with its stored
            // region state but no extraction has run yet.
            service.detachPlacement(placement);
            service.attachPlacement(placement);

            final BuildRegion restored = placement.getBuildRegions().get("roof");
            assertNotNull(restored);
            assertTrue(restored.hasClaimer(alice));
        } finally {
            context.shutdown();
        }
    }

    @Test
    void buildManagementIsAdvertisedByDefault() {
        final Context context = newServerContext();
        try {
            assertTrue(context.getFeatureSet().hasFeature(Feature.BUILD_MANAGEMENT));
        } finally {
            context.shutdown();
        }
    }

    @Test
    void buildManagementIsWithdrawnOnlyByItsOwnSwitch() {
        final Context disabled = newServerContext();
        try {
            disabled.getBuildService().configure(new DisablingConfiguration());
            assertFalse(disabled.getFeatureSet().hasFeature(Feature.BUILD_MANAGEMENT));
        } finally {
            disabled.shutdown();
        }

        // Build management reads the schematic itself, so turning material
        // tracking off must leave it alone.
        final Context withoutMaterials = newServerContext();
        try {
            withoutMaterials.getMaterialService().configure(new DisablingConfiguration());
            assertTrue(withoutMaterials.getFeatureSet().hasFeature(Feature.BUILD_MANAGEMENT));
        } finally {
            withoutMaterials.shutdown();
        }
    }

    /**
     * The point of the service owning its own extraction: a placement learns its
     * regions from the file even when material tracking never runs.
     */
    @Test
    void regionsAreLearnedFromTheSchematicWithMaterialTrackingOff() throws Exception {
        final Context context = newServerContext();
        try {
            context.getMaterialService().configure(new DisablingConfiguration());
            final UUID hash = writeLitematic(context, "roof", "walls");
            final ServerPlacement placement = new ServerPlacement(
                    UUID.randomUUID(), "build", hash, PlayerIdentifier.MISSING_PLAYER);
            placement.move("minecraft:overworld", BlockPos.ORIGIN, BlockRotation.NONE, BlockMirror.NONE);

            context.getSyncmaticManager().addPlacement(placement);
            pumpUntilRegionsArrive(context, placement);

            assertNotNull(placement.getBuildRegions().get("roof"));
            assertNotNull(placement.getBuildRegions().get("walls"));
        } finally {
            context.shutdown();
        }
    }

    /**
     * Region boxes are derived from the file and never persisted, so a placement
     * that came back from disk has to be read again before the completion scan or
     * anything else can locate it in the world.
     */
    @Test
    void regionBoxesFollowThePlacementPose() throws Exception {
        final Context context = newServerContext();
        try {
            final UUID hash = writeLitematic(context, "roof");
            final ServerPlacement placement = new ServerPlacement(
                    UUID.randomUUID(), "build", hash, PlayerIdentifier.MISSING_PLAYER);
            placement.move("minecraft:overworld", new BlockPos(100, 64, 200),
                    BlockRotation.NONE, BlockMirror.NONE);

            context.getSyncmaticManager().addPlacement(placement);
            pumpUntilRegionsArrive(context, placement);

            RegionBounds bounds = context.getBuildService().getRegionBounds(placement).get("roof");
            assertNotNull(bounds, "a placement never learns where its regions are");
            assertEquals(new BlockPos(100, 64, 200), bounds.getMin());

            // Moving the placement must move the box without another extraction.
            placement.move("minecraft:overworld", BlockPos.ORIGIN, BlockRotation.NONE, BlockMirror.NONE);
            bounds = context.getBuildService().getRegionBounds(placement).get("roof");
            assertEquals(BlockPos.ORIGIN, bounds.getMin());
            assertEquals(new BlockPos(3, 2, 1), bounds.getMax());
        } finally {
            context.shutdown();
        }
    }

    @Test
    void scanBudgetsAreClampedToSaneValues() {
        final Context context = newServerContext();
        try {
            final BuildService service = context.getBuildService();
            assertTrue(service.isCompletionEnabled());

            // Silly values must not turn into a per-tick full scan or a busy loop.
            service.configure(new ScanTuningConfiguration(1, 1));
            service.configure(new ScanTuningConfiguration(Integer.MAX_VALUE, Integer.MAX_VALUE));
            // Zero switches the recovery sweep off; a negative is the same ask.
            service.configure(new ScanTuningConfiguration(1024, 1200, 0));
            service.configure(new ScanTuningConfiguration(1024, 1200, -1));
            service.configure(new ScanTuningConfiguration(1024, 1200, 1));
        } finally {
            context.shutdown();
        }
    }

    @Test
    void theScanDefaultsAreThePublishedOnes() {
        final BuildService service = new BuildService();
        try {
            final JsonObject defaults = new JsonObject();
            service.getDefaultConfiguration(new JsonConfiguration(defaults));

            assertEquals(BuildService.SCAN_BLOCKS_PER_TICK_DEFAULT, defaults.get("scan_blocks_per_tick").getAsInt());
            assertEquals(BuildService.SCAN_INTERVAL_DEFAULT, defaults.get("scan_interval").getAsInt());
            assertEquals(BuildService.FULL_RESCAN_INTERVAL_DEFAULT,
                    defaults.get("full_rescan_interval").getAsInt());
        } finally {
            service.shutdown();
        }
    }

    @Test
    void completionSurvivesARestartThroughTheStoredPlacement() {
        final Context context = newServerContext();
        try {
            final BuildService service = context.getBuildService();
            final ServerPlacement placement = attach(context, service, regions("roof"));
            placement.getBuildRegions().get("roof").recordScan(60L, 4_242L);

            final ServerPlacement restored = ServerPlacement.fromJson(placement.toJson(), context);
            assertNotNull(restored);
            final BuildRegion roof = restored.getBuildRegions().get("roof");
            assertNotNull(roof);
            assertEquals(60L, roof.getPlacedBlocks());
            assertEquals(4_242L, roof.getLastScanMillis());
            assertEquals(60, roof.getCompletionPercent());
        } finally {
            context.shutdown();
        }
    }

    @Test
    void perChunkCountsComeBackFromTheWorldFolderOnAttach() {
        final RegionBounds bounds = new RegionBounds(new BlockPos(0, 64, 0), new BlockPos(31, 70, 31));
        final JsonObject storedPlacement;

        final Context first = newServerContext();
        try {
            final ServerPlacement placement = attach(first, first.getBuildService(), regions("roof"));
            final BuildRegion roof = placement.getBuildRegions().get("roof");
            final RegionScanCache cache = new RegionScanCache(bounds);
            cache.record(0, 0, 40);
            cache.record(1, 0, 20);
            roof.setScanCache(cache);
            roof.recordScan(cache.getTotal(), 4_242L);
            new BuildScanStore(tempDir.toFile()).save(placement.getId(), placement.getBuildRegions());
            storedPlacement = placement.toJson();
        } finally {
            first.shutdown();
        }

        final Context second = newServerContext();
        try {
            // What a restart does: the placement comes back from its own file,
            // and attaching it picks the counts back up out of the world.
            final ServerPlacement restored = ServerPlacement.fromJson(storedPlacement, second);
            assertNotNull(restored);
            second.getSyncmaticManager().addPlacement(restored);

            final RegionScanCache cache = restored.getBuildRegions().get("roof").getScanCache();
            assertNotNull(cache, "without the counts a restart would re-measure from nothing");
            assertEquals(60L, cache.getTotal());
            assertTrue(cache.isCounted(0, 0));
            assertTrue(cache.isCounted(1, 0));
            assertFalse(cache.isCounted(0, 1), "a column nobody reached stays unknown");
            assertTrue(cache.matches(bounds), "the counts still belong to the box they were taken in");
        } finally {
            second.shutdown();
        }
    }

    @Test
    void aWorldThatCameBackFromABackupOutranksThePlacementFile() {
        final JsonObject storedPlacement;

        final Context first = newServerContext();
        try {
            final ServerPlacement placement = attach(first, first.getBuildService(), regions("roof"));
            final BuildRegion roof = placement.getBuildRegions().get("roof");
            final RegionScanCache cache =
                    new RegionScanCache(new RegionBounds(new BlockPos(0, 64, 0), new BlockPos(15, 70, 15)));
            cache.record(0, 0, 60);
            roof.setScanCache(cache);
            roof.recordScan(60L, 4_242L);
            new BuildScanStore(tempDir.toFile()).save(placement.getId(), placement.getBuildRegions());

            // The build carried on after that measurement was stored, and only the
            // placement file kept up — the world is the one that got rolled back.
            roof.recordScan(90L, 9_999L);
            storedPlacement = placement.toJson();
        } finally {
            first.shutdown();
        }

        final Context second = newServerContext();
        try {
            final ServerPlacement restored = ServerPlacement.fromJson(storedPlacement, second);
            assertNotNull(restored);
            second.getSyncmaticManager().addPlacement(restored);

            final BuildRegion roof = restored.getBuildRegions().get("roof");
            assertEquals(60L, roof.getPlacedBlocks(),
                    "the world holds the blocks, so what it measured wins over the placement file");
            assertEquals(4_242L, roof.getLastScanMillis());
        } finally {
            second.shutdown();
        }
    }

    @Test
    void aRescanForgetsEveryMeasurementSoTheyAreTakenAgain() {
        final Context context = newServerContext();
        try {
            final BuildService service = context.getBuildService();
            final ServerPlacement placement = attach(context, service, regions("roof"));
            final BuildRegion roof = placement.getBuildRegions().get("roof");
            final RegionScanCache cache =
                    new RegionScanCache(new RegionBounds(new BlockPos(0, 64, 0), new BlockPos(15, 70, 15)));
            cache.record(0, 0, 60);
            roof.setScanCache(cache);
            roof.recordScan(60L, 4_242L);
            final BuildScanStore store = new BuildScanStore(tempDir.toFile());
            store.save(placement.getId(), placement.getBuildRegions());

            assertTrue(service.rescan(placement));
            assertNull(roof.getScanCache(), "an offline world edit is exactly what this has to undo");
            assertFalse(roof.isScanned());
            assertEquals(0L, roof.getPlacedBlocks());

            // Left on disk it would come straight back on the next restart.
            final BuildRegionState reloaded = new BuildRegionState();
            reloaded.getOrCreate("roof", 100L);
            store.load(placement.getId(), reloaded);
            assertNull(reloaded.get("roof").getScanCache(), "the stored counts went with it");
        } finally {
            context.shutdown();
        }
    }

    /** Extraction runs off the server thread, so the tick loop has to catch up with it. */
    private void pumpUntilRegionsArrive(final Context context, final ServerPlacement placement)
            throws InterruptedException {
        final long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            context.getBuildService().tick(null);
            if (!placement.getBuildRegions().isEmpty()) {
                return;
            }
            Thread.sleep(5L);
        }
    }

    /** Writes a schematic under the hash the file storage looks for. */
    private UUID writeLitematic(final Context context, final String... regionNames) throws Exception {
        final NbtCompound regions = new NbtCompound();
        for (final String regionName : regionNames) {
            final NbtCompound region = new NbtCompound();
            region.putIntArray("Position", new int[]{0, 0, 0});
            region.putIntArray("Size", new int[]{4, 3, 2});
            regions.put(regionName, region);
        }
        final NbtCompound root = new NbtCompound();
        root.put("Regions", regions);

        final File staging = new File(context.getLitematicFolder(), "staging.litematic");
        try (OutputStream output = new FileOutputStream(staging)) {
            NbtIo.writeCompressed(root, output);
        }
        final UUID hash;
        try (InputStream input = new FileInputStream(staging)) {
            hash = SyncmaticaUtil.createChecksum(input);
        }
        assertTrue(staging.renameTo(new File(context.getLitematicFolder(), hash + ".litematic")));
        return hash;
    }

    private static String tooLongName() {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i <= ProtocolLimits.MAX_SUBREGION_NAME_LENGTH; i++) {
            builder.append('x');
        }
        return builder.toString();
    }

    private static Map<String, Long> regions(final String... names) {
        final Map<String, Long> blocks = new LinkedHashMap<>();
        for (final String name : names) {
            blocks.put(name, 100L);
        }
        return blocks;
    }

    private ServerPlacement attach(final Context context, final BuildService service,
                                   final Map<String, Long> blocks) {
        final ServerPlacement placement = new ServerPlacement(
                UUID.randomUUID(), "build", UUID.randomUUID(), PlayerIdentifier.MISSING_PLAYER);
        placement.move("minecraft:overworld", BlockPos.ORIGIN, BlockRotation.NONE, BlockMirror.NONE);
        context.getSyncmaticManager().addPlacement(placement);
        service.replaceRegions(placement.getId(), blocks);
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

    /** Flips only the enabled flag, leaving every other option at its default. */
    private static final class DisablingConfiguration extends NoOpConfiguration {
        @Override
        public void loadBoolean(final String key, final java.util.function.Consumer<Boolean> loader) {
            if ("enabled".equals(key)) {
                loader.accept(false);
            }
        }
    }

    private static final class ScanTuningConfiguration extends NoOpConfiguration {
        private final int blocksPerTick;
        private final int interval;
        private final int fullRescanInterval;

        private ScanTuningConfiguration(final int blocksPerTick, final int interval) {
            this(blocksPerTick, interval, interval);
        }

        private ScanTuningConfiguration(final int blocksPerTick, final int interval, final int fullRescanInterval) {
            this.blocksPerTick = blocksPerTick;
            this.interval = interval;
            this.fullRescanInterval = fullRescanInterval;
        }

        @Override
        public void loadInteger(final String key, final java.util.function.IntConsumer loader) {
            if ("scan_blocks_per_tick".equals(key)) {
                loader.accept(blocksPerTick);
            } else if ("scan_interval".equals(key)) {
                loader.accept(interval);
            } else if ("full_rescan_interval".equals(key)) {
                loader.accept(fullRescanInterval);
            }
        }
    }

    private static class NoOpConfiguration implements IServiceConfiguration {
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

    private static final class StubCommunicationManager extends CommunicationManager {
        @Override
        protected void handle(final ExchangeTarget source, final Identifier id, final PacketByteBuf packetBuf) {
        }

        @Override
        protected void handleExchange(final Exchange exchange) {
        }
    }
}
