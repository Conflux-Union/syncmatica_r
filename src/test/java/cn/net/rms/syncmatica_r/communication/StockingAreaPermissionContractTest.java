package cn.net.rms.syncmatica_r.communication;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class StockingAreaPermissionContractTest {
    private final Path projectRoot = Path.of(System.getProperty("syncmatica.projectRoot"));

    @Test
    void networkRequestsUseOwnerAwareStockingAreaPolicy() throws IOException {
        final String source = Files.readString(
                projectRoot.resolve(
                        "src/main/java/cn/net/rms/syncmatica_r/communication/ServerCommunicationManager.java"),
                StandardCharsets.UTF_8
        );

        // The pre-registry coordinate packet keeps its id recognized so a
        // 0.4.x button press gets an explicit rejection instead of silence;
        // the handler only drains the payload and answers, it never applies
        // the pushed coordinates.
        final String packetTypes = Files.readString(
                projectRoot.resolve(
                        "src/main/java/cn/net/rms/syncmatica_r/communication/PacketType.java"),
                StandardCharsets.UTF_8
        );
        assertTrue(
                packetTypes.contains("SET_STOCKING_AREA"),
                "the legacy packet id must stay routable so old clients get an answer");
        assertTrue(
                source.contains("type == PacketType.SET_STOCKING_AREA"),
                "the server must keep a dispatch branch for legacy coordinate pushes");
        final int legacyHandler = source.indexOf("private void handleSetStockingArea");
        assertTrue(legacyHandler >= 0, "the legacy push must keep its handler");
        final String legacyHandlerBody = source.substring(
                legacyHandler, source.indexOf("\n    }", legacyHandler));
        assertTrue(
                legacyHandlerBody.contains("syncmatica_r.error.stocking_area.unsupported"),
                "a legacy push must be answered with the unsupported error");
        final String helper = Files.readString(
                projectRoot.resolve(
                        "src/main/java/cn/net/rms/syncmatica_r/util/StockingAreaSelectionHelper.java"),
                StandardCharsets.UTF_8
        );
        assertTrue(
                helper.contains("syncmatica_r.error.stocking_area.unsupported"),
                "clients talking to pre-registry servers must still get the upgrade hint");
        assertTrue(
                source.contains("PlacementAccessPolicy.canManageStockingArea"),
                "the network path must use the shared owner-aware access policy");
        assertTrue(
                source.contains("materialService.isOwnerStockingAreaManagementEnabled()"),
                "the network path must honor the materials owner-management setting");
    }

    @Test
    void registryCommandsUseCreatorAwarePolicy() throws IOException {
        final String commandSource = Files.readString(
                projectRoot.resolve(
                        "src/main/java/cn/net/rms/syncmatica_r/command/SyncmaticaCommand.java"),
                StandardCharsets.UTF_8
        );
        assertTrue(
                commandSource.contains("PlacementAccessPolicy.canManageStockingAreaEntry"),
                "area edit/delete must check the creator-aware policy");
        assertTrue(
                commandSource.contains("deleteStockingArea"),
                "delete must go through the reference-checking service method");
        assertTrue(
                commandSource.contains("bindStockingArea"),
                "binding must go through the service bind method");
    }

    @Test
    void forceDeleteRescansThePlacementsItUnbinds() throws IOException {
        final String commandSource = Files.readString(
                projectRoot.resolve(
                        "src/main/java/cn/net/rms/syncmatica_r/command/SyncmaticaCommand.java"),
                StandardCharsets.UTF_8
        );
        // Scoped to the delete handler body: the edit handler's rescan call
        // must not satisfy this contract on its own.
        final int handlerStart = commandSource.indexOf("handleDeleteStockingArea(final CommandContext");
        final int handlerEnd = commandSource.indexOf("private static", handlerStart);
        assertTrue(handlerStart >= 0 && handlerEnd > handlerStart,
                "the delete handler must exist in SyncmaticaCommand");
        final String handlerBody = commandSource.substring(handlerStart, handlerEnd);
        assertTrue(
                handlerBody.contains("rescanPlacements("),
                "force-deleting must rescan the placements the delete unbound");
    }
}
