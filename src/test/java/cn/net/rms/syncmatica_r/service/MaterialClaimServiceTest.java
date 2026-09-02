package cn.net.rms.syncmatica_r.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.FileStorage;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.SyncmaticManager;
import cn.net.rms.syncmatica_r.communication.CommunicationManager;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.exchange.Exchange;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.material.MaterialKey;
import cn.net.rms.syncmatica_r.util.IdentifierUtil;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MaterialClaimServiceTest {
    private static final MaterialKey STONE = new MaterialKey(IdentifierUtil.require("minecraft:stone"), "");

    @TempDir
    Path tempDir;

    @Test
    void desiredClaimAndReleaseAreIdempotent() {
        final Context context = newServerContext();
        try {
            final ServerPlacement placement = placement(context);
            final MaterialService service = context.getMaterialService();
            final PlayerIdentifier alice = player(context, "Alice");

            assertEquals(MaterialService.ClaimOutcome.CLAIMED,
                    service.setClaim(placement, STONE, alice, true));
            final long claimedAt = placement.getLastModifiedAtMillis();
            assertEquals(MaterialService.ClaimOutcome.ALREADY_CLAIMED,
                    service.setClaim(placement, STONE, alice, true));
            assertEquals(claimedAt, placement.getLastModifiedAtMillis());

            assertEquals(MaterialService.ClaimOutcome.RELEASED,
                    service.setClaim(placement, STONE, alice, false));
            final long releasedAt = placement.getLastModifiedAtMillis();
            assertEquals(MaterialService.ClaimOutcome.ALREADY_RELEASED,
                    service.setClaim(placement, STONE, alice, false));
            assertEquals(releasedAt, placement.getLastModifiedAtMillis());
            assertNull(service.getClaimant(placement, STONE));
        } finally {
            context.shutdown();
        }
    }

    @Test
    void anotherPlayersClaimCannotBeTakenOver() {
        final Context context = newServerContext();
        try {
            final ServerPlacement placement = placement(context);
            final MaterialService service = context.getMaterialService();
            final PlayerIdentifier alice = player(context, "Alice");
            final PlayerIdentifier bob = player(context, "Bob");

            assertEquals(MaterialService.ClaimOutcome.CLAIMED,
                    service.setClaim(placement, STONE, alice, true));
            assertEquals(MaterialService.ClaimOutcome.CLAIMED_BY_OTHER,
                    service.setClaim(placement, STONE, bob, true));
            assertEquals(alice, service.getClaimant(placement, STONE));
        } finally {
            context.shutdown();
        }
    }

    @Test
    void unknownMaterialsAndDisabledServiceAreRejected() {
        final Context context = newServerContext();
        try {
            final ServerPlacement placement = placement(context);
            final PlayerIdentifier alice = player(context, "Alice");
            final MaterialKey dirt = new MaterialKey(IdentifierUtil.require("minecraft:dirt"), "");

            assertEquals(MaterialService.ClaimOutcome.UNKNOWN_MATERIAL,
                    context.getMaterialService().setClaim(placement, dirt, alice, true));

            context.getMaterialService().configure(new DisablingConfiguration());
            assertEquals(MaterialService.ClaimOutcome.DISABLED,
                    context.getMaterialService().setClaim(placement, STONE, alice, true));
        } finally {
            context.shutdown();
        }
    }

    @Test
    void onlyChangedClaimsNotifyTheManagerPersistenceSeam() {
        final Context context = newServerContext();
        try {
            final ServerPlacement placement = placement(context);
            final PlayerIdentifier alice = player(context, "Alice");
            final PlayerIdentifier bob = player(context, "Bob");
            final AtomicInteger updates = new AtomicInteger();
            context.getSyncmaticManager().addServerPlacementConsumer(updated -> {
                assertEquals(placement, updated);
                updates.incrementAndGet();
            });

            assertEquals(MaterialService.ClaimOutcome.CLAIMED,
                    context.getMaterialService().setClaim(placement, STONE, alice, true));
            assertEquals(1, updates.get());
            assertEquals(alice, placement.getLastModifiedBy());
            assertTrue(placement.getLastModifiedAtMillis() > 0L);

            assertEquals(MaterialService.ClaimOutcome.ALREADY_CLAIMED,
                    context.getMaterialService().setClaim(placement, STONE, alice, true));
            assertEquals(MaterialService.ClaimOutcome.CLAIMED_BY_OTHER,
                    context.getMaterialService().setClaim(placement, STONE, bob, true));
            assertEquals(MaterialService.ClaimOutcome.UNKNOWN_MATERIAL,
                    context.getMaterialService().setClaim(
                            placement,
                            new MaterialKey(IdentifierUtil.require("minecraft:dirt"), ""),
                            alice,
                            true
                    ));
            assertEquals(1, updates.get(),
                    "idempotent, conflicting, and unknown writes must not dirty the placement");

            assertEquals(MaterialService.ClaimOutcome.RELEASED,
                    context.getMaterialService().setClaim(placement, STONE, alice, false));
            assertEquals(2, updates.get());
            assertEquals(MaterialService.ClaimOutcome.ALREADY_RELEASED,
                    context.getMaterialService().setClaim(placement, STONE, alice, false));

            context.getMaterialService().configure(new DisablingConfiguration());
            assertEquals(MaterialService.ClaimOutcome.DISABLED,
                    context.getMaterialService().setClaim(placement, STONE, alice, true));
            assertEquals(2, updates.get(),
                    "idempotent release and disabled writes must not dirty the placement");
        } finally {
            context.shutdown();
        }
    }

    private ServerPlacement placement(final Context context) {
        final ServerPlacement placement = new ServerPlacement(
                UUID.randomUUID(), "materials", UUID.randomUUID(), PlayerIdentifier.MISSING_PLAYER);
        placement.move("minecraft:overworld", BlockPos.ORIGIN, BlockRotation.NONE, BlockMirror.NONE);
        placement.getMaterialProgress().getOrCreate(STONE, 64);
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

    private static final class StubCommunicationManager extends CommunicationManager {
        @Override
        protected void handle(final ExchangeTarget source, final Identifier id, final PacketByteBuf packetBuf) {
        }

        @Override
        protected void handleExchange(final Exchange exchange) {
        }
    }
}
