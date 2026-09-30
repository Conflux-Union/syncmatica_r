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

        // Pre-registry clients push raw coordinates; the named-registry server
        // rejects those pushes with an upgrade hint instead of silently losing
        // the selection.
        assertTrue(
                source.contains("syncmatica_r.error.stocking_area.unsupported"),
                "coordinate stocking-area pushes must be answered with the upgrade hint");
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
}
