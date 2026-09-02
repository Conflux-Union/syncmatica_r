package cn.net.rms.syncmatica_r.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.FileStorage;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.SyncmaticManager;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.material.MaterialKey;
import cn.net.rms.syncmatica_r.service.IServiceConfiguration;
import cn.net.rms.syncmatica_r.service.MaterialService;
import cn.net.rms.syncmatica_r.util.IdentifierUtil;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MaterialClaimPacketContractTest {
    private static final MaterialKey STONE =
            new MaterialKey(IdentifierUtil.require("minecraft:stone"), "");

    @TempDir
    Path tempDir;

    @Test
    void permissionDenialIsVisibleAndDoesNotMutateOrBroadcast() {
        final Fixture fixture = new Fixture();
        try {
            final Feedback feedback = new Feedback();

            MaterialClaimPacketAdapter.handleToggle(
                    fixture.context.getMaterialService(),
                    fixture.placement,
                    STONE,
                    () -> fixture.alice,
                    () -> false,
                    feedback
            );

            assertEquals(MessageType.ERROR, feedback.type);
            assertEquals("syncmatica_r.error.permission_denied", feedback.identifier);
            assertNull(fixture.context.getMaterialService().getClaimant(fixture.placement, STONE));
            assertEquals(0, fixture.communication.broadcastCount);
            assertNull(fixture.communication.visiblePlacement);
        } finally {
            fixture.close();
        }
    }

    @Test
    void conflictProducesTheLegacyWarningWithoutBroadcasting() {
        final Fixture fixture = new Fixture();
        try {
            fixture.context.getMaterialService().setClaim(
                    fixture.placement, STONE, fixture.alice, true);
            fixture.communication.reset();
            final Feedback feedback = new Feedback();

            MaterialClaimPacketAdapter.handleToggle(
                    fixture.context.getMaterialService(),
                    fixture.placement,
                    STONE,
                    () -> fixture.bob,
                    () -> true,
                    feedback
            );

            assertEquals(MessageType.WARNING, feedback.type);
            assertEquals("Already claimed by Alice", feedback.identifier);
            assertEquals(fixture.alice,
                    fixture.context.getMaterialService().getClaimant(fixture.placement, STONE));
            assertEquals(0, fixture.communication.broadcastCount);
            assertNull(fixture.communication.visiblePlacement);
        } finally {
            fixture.close();
        }
    }

    @Test
    void successfulToggleBroadcastsOneVisiblePlacementSnapshot() {
        final Fixture fixture = new Fixture();
        try {
            final Feedback feedback = new Feedback();

            MaterialClaimPacketAdapter.handleToggle(
                    fixture.context.getMaterialService(),
                    fixture.placement,
                    STONE,
                    () -> fixture.alice,
                    () -> true,
                    feedback
            );

            assertNull(feedback.type);
            assertEquals(1, fixture.communication.broadcastCount);
            assertTrue(fixture.communication.visiblePlacement.contains(fixture.alice.uuid.toString()));
            assertEquals(fixture.alice,
                    fixture.context.getMaterialService().getClaimant(fixture.placement, STONE));
        } finally {
            fixture.close();
        }
    }

    @Test
    void unknownDisabledAndDesiredStateNoOpsDoNotBroadcast() {
        final Fixture fixture = new Fixture();
        try {
            final Feedback feedback = new Feedback();
            final MaterialKey dirt =
                    new MaterialKey(IdentifierUtil.require("minecraft:dirt"), "");

            MaterialClaimPacketAdapter.handleToggle(
                    fixture.context.getMaterialService(),
                    fixture.placement,
                    dirt,
                    () -> fixture.alice,
                    () -> true,
                    feedback
            );
            assertEquals(0, fixture.communication.broadcastCount);

            fixture.context.getMaterialService().setClaim(
                    fixture.placement, STONE, fixture.alice, true);
            fixture.communication.reset();
            MaterialClaimPacketAdapter.handleDesiredState(
                    fixture.context.getMaterialService(),
                    fixture.placement,
                    STONE,
                    fixture.alice,
                    true,
                    true,
                    feedback
            );
            assertEquals(0, fixture.communication.broadcastCount);

            fixture.context.getMaterialService().configure(new DisablingConfiguration());
            MaterialClaimPacketAdapter.handleToggle(
                    fixture.context.getMaterialService(),
                    fixture.placement,
                    STONE,
                    () -> fixture.alice,
                    () -> true,
                    feedback
            );
            assertEquals(0, fixture.communication.broadcastCount);
            assertNull(fixture.communication.visiblePlacement);
        } finally {
            fixture.close();
        }
    }

    private final class Fixture {
        private final RecordingServerCommunicationManager communication =
                new RecordingServerCommunicationManager();
        private final Context context = new Context(
                new FileStorage(),
                communication,
                new SyncmaticManager(),
                true,
                tempDir.resolve(UUID.randomUUID().toString()).toFile(),
                true,
                tempDir.toFile()
        );
        private final PlayerIdentifier alice = player(context, "Alice");
        private final PlayerIdentifier bob = player(context, "Bob");
        private final ServerPlacement placement = placement(context);

        private void close() {
            context.shutdown();
        }
    }

    private static ServerPlacement placement(final Context context) {
        final ServerPlacement placement = new ServerPlacement(
                UUID.randomUUID(), "materials", UUID.randomUUID(), PlayerIdentifier.MISSING_PLAYER);
        placement.move("minecraft:overworld", BlockPos.ORIGIN, BlockRotation.NONE, BlockMirror.NONE);
        placement.getMaterialProgress().getOrCreate(STONE, 64);
        context.getSyncmaticManager().addPlacement(placement);
        return placement;
    }

    private static PlayerIdentifier player(final Context context, final String name) {
        return context.getPlayerIdentifierProvider().createOrGet(
                UUID.nameUUIDFromBytes(name.getBytes()), name);
    }

    private static final class Feedback implements MaterialClaimPacketAdapter.Feedback {
        private MessageType type;
        private String identifier;

        @Override
        public void send(final MessageType type, final String identifier) {
            this.type = type;
            this.identifier = identifier;
        }
    }

    private static final class RecordingServerCommunicationManager extends ServerCommunicationManager {
        private int broadcastCount;
        private String visiblePlacement;

        @Override
        public void broadcastPlacementUpdate(final ServerPlacement placement) {
            broadcastCount++;
            visiblePlacement = placement.toJson().toString();
        }

        private void reset() {
            broadcastCount = 0;
            visiblePlacement = null;
        }
    }

    private static final class DisablingConfiguration implements IServiceConfiguration {
        @Override
        public void loadBoolean(final String key, final java.util.function.Consumer<Boolean> loader) {
            if ("enabled".equals(key)) {
                loader.accept(false);
            }
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
}
