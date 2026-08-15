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
