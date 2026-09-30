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

        // The pre-registry coordinate packet is fully retired: the server no
        // longer recognizes its id, and only the client helper still surfaces
        // the upgrade hint for servers that predate named areas.
        final String packetTypes = Files.readString(
                projectRoot.resolve(
                        "src/main/java/cn/net/rms/syncmatica_r/communication/PacketType.java"),
                StandardCharsets.UTF_8
        );
        assertTrue(
                !packetTypes.contains("SET_STOCKING_AREA"),
                "the legacy coordinate packet id must not be resurrected");
        assertTrue(
                !source.contains("handleSetStockingArea"),
                "the server must not answer legacy coordinate pushes");
        final String helper = Files.readString(
                projectRoot.resolve(
                        "src/main/java/cn/net/rms/syncmatica_r/util/StockingAreaSelectionHelper.java"),
                StandardCharsets.UTF_8
        );
        assertTrue(
                helper.contains("syncmatica_r.error.stocking_area.unsupported"),
                "clients talking to pre-registry servers must still get the upgrade hint");
        // Transitional: the owner-aware helper below has no caller until the
        // packet paths are rebuilt on the registry API; these assertions keep
        // the shared policy plumbing pinned for that rebuild.
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
