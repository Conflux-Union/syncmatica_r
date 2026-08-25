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

        assertTrue(
                source.contains("canManageStockingArea(source, placement, materialService)"),
                "placement stocking-area requests must use their dedicated access check");
        assertTrue(
                source.contains("PlacementAccessPolicy.canManageStockingArea"),
                "the network path must use the shared owner-aware access policy");
        assertTrue(
                source.contains("materialService.isOwnerStockingAreaManagementEnabled()"),
                "the network path must honor the materials owner-management setting");
    }
}
