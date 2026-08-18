package cn.net.rms.syncmatica_r.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.FileStorage;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.SyncmaticManager;
import cn.net.rms.syncmatica_r.Syncmatica;
import cn.net.rms.syncmatica_r.communication.CommunicationManager;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.exchange.Exchange;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.material.MaterialKey;
import cn.net.rms.syncmatica_r.material.MaterialProgressEntry;
import cn.net.rms.syncmatica_r.util.IdentifierUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class SyncmaticaMaterialApiTest {

    private static final Path CLIENT_CONFIG_ROOT = Paths.get("config");

    private boolean clientConfigExisted;

    @BeforeEach
    void clearContextsBeforeTest() {
        clientConfigExisted = Files.exists(CLIENT_CONFIG_ROOT);
        Syncmatica.shutdown();
    }

    @AfterEach
    void clearContextsAfterTest() throws IOException {
        Syncmatica.shutdown();
        if (clientConfigExisted || !Files.exists(CLIENT_CONFIG_ROOT)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(CLIENT_CONFIG_ROOT)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void exposesTheOutstandingAmountClaimedByTheRequestedPlayer() {
        final SyncmaticManager manager = newClientManager();
        final UUID playerId = UUID.randomUUID();
        final ServerPlacement placement = newPlacement("tower");
        final MaterialProgressEntry stone = placement.getMaterialProgress().getOrCreate(
                new MaterialKey(IdentifierUtil.require("minecraft:stone"), ""),
                100
        );
        stone.setStockingSupplied(30);
        stone.addClaimer(new PlayerIdentifier(playerId, "Builder"));
        manager.addPlacement(placement);

        final List<MaterialRequirement> requirements =
                SyncmaticaMaterialApi.getClaimedMaterialRequirements(playerId);

        assertEquals(1, requirements.size());
        assertEquals("minecraft:stone", requirements.get(0).itemId());
        assertEquals("", requirements.get(0).variant());
        assertEquals(70, requirements.get(0).missingAmount());
    }

    @Test
    void aggregatesMatchingClaimsAndExcludesMaterialsThePlayerDoesNotOwe() {
        final SyncmaticManager manager = newClientManager();
        final UUID playerId = UUID.randomUUID();
        final UUID otherPlayerId = UUID.randomUUID();
        final ServerPlacement first = newPlacement("first");
        addMaterial(first, "minecraft:stone", "", 100, 40, playerId);
        addMaterial(first, "minecraft:dirt", "", 20, 20, playerId);
        addMaterial(first, "minecraft:glass", "", 12, 0, otherPlayerId);
        manager.addPlacement(first);

        final ServerPlacement second = newPlacement("second");
        addMaterial(second, "minecraft:stone", "", 30, 10, playerId);
        addMaterial(second, "minecraft:oak_planks", "", 15, 0, playerId);
        manager.addPlacement(second);

        final List<MaterialRequirement> requirements =
                SyncmaticaMaterialApi.getClaimedMaterialRequirements(playerId);

        assertEquals(2, requirements.size());
        assertEquals(new MaterialRequirement("minecraft:oak_planks", "", 15), requirements.get(0));
        assertEquals(new MaterialRequirement("minecraft:stone", "", 80), requirements.get(1));
    }

    @Test
    void returnsAnImmutableSnapshotAndCapsAggregatedAmounts() {
        final SyncmaticManager manager = newClientManager();
        final UUID playerId = UUID.randomUUID();
        final ServerPlacement first = newPlacement("first");
        addMaterial(first, "minecraft:stone", "", Integer.MAX_VALUE, 0, playerId);
        manager.addPlacement(first);
        final ServerPlacement second = newPlacement("second");
        addMaterial(second, "minecraft:stone", "", 1, 0, playerId);
        manager.addPlacement(second);

        final List<MaterialRequirement> snapshot =
                SyncmaticaMaterialApi.getClaimedMaterialRequirements(playerId);
        first.getMaterialProgress().clear();

        assertEquals(1, snapshot.size());
        assertEquals(Integer.MAX_VALUE, snapshot.get(0).missingAmount());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.add(new MaterialRequirement("minecraft:dirt", "", 1)));
    }

    private SyncmaticManager newClientManager() {
        final SyncmaticManager manager = new SyncmaticManager();
        final Context context = Syncmatica.initClient(
                new StubCommunicationManager(),
                new FileStorage(),
                manager
        );
        context.startup();
        return manager;
    }

    private ServerPlacement newPlacement(final String fileName) {
        return new ServerPlacement(
                UUID.randomUUID(),
                fileName,
                UUID.randomUUID(),
                PlayerIdentifier.MISSING_PLAYER
        );
    }

    private void addMaterial(final ServerPlacement placement,
                             final String itemId,
                             final String variant,
                             final int required,
                             final int supplied,
                             final UUID claimantId) {
        final MaterialProgressEntry entry = placement.getMaterialProgress().getOrCreate(
                new MaterialKey(IdentifierUtil.require(itemId), variant),
                required
        );
        entry.setStockingSupplied(supplied);
        entry.addClaimer(new PlayerIdentifier(claimantId, "Builder"));
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
