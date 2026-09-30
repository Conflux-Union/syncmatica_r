package cn.net.rms.syncmatica_r.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class StockingAreaSelectionContractTest {
    private final Path projectRoot = Path.of(System.getProperty("syncmatica.projectRoot"));

    @Test
    void selectionHelperTargetsTheManagePacketAndNamedFeature() throws IOException {
        final String source = Files.readString(
                projectRoot.resolve(
                        "src/main/java/cn/net/rms/syncmatica_r/util/StockingAreaSelectionHelper.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("PacketType.STOCKING_AREA_MANAGE"),
                "selection uploads must use the manage packet");
        assertTrue(source.contains("Feature.NAMED_STOCKING_AREAS"),
                "the helper must fall back to hidden when the server predates named areas");
        assertTrue(!source.contains("PacketType.SET_STOCKING_AREA"),
                "the legacy coordinate packet must not be sent anymore");
    }
}
