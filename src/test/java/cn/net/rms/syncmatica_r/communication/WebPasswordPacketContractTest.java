package cn.net.rms.syncmatica_r.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import cn.net.rms.syncmatica_r.Feature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class WebPasswordPacketContractTest {
    private final Path projectRoot = Path.of(System.getProperty("syncmatica.projectRoot"));

    @Test
    void passwordUpdatesNoLongerUseClientFeatureOrPackets() {
        assertNull(Feature.fromString("WEB_PASSWORD"));
        assertNull(PacketType.fromIdentifier(identifier("syncmatica_r", "web_password_open")));
        assertNull(PacketType.fromIdentifier(identifier("syncmatica_r", "web_password_update")));
        assertNull(PacketType.fromIdentifier(identifier("syncmatica_r", "web_password_result")));
        assertEquals("syncmatica_r:mesage", PacketType.MESSAGE.toIdentifier().toString());
    }

    @Test
    void communicationHandlersDoNotOpenOrSubmitPasswordGuis() throws IOException {
        final String server = read(
                "src/main/java/cn/net/rms/syncmatica_r/communication/ServerCommunicationManager.java");
        final String client = read(
                "src/main/java/cn/net/rms/syncmatica_r/communication/ClientCommunicationManager.java");

        assertFalse(server.contains("WEB_PASSWORD"));
        assertFalse(server.contains("handleWebPasswordUpdate"));
        assertFalse(client.contains("WEB_PASSWORD"));
        assertFalse(client.contains("openWebPasswordScreen"));
    }

    private String read(final String relativePath) throws IOException {
        return Files.readString(projectRoot.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private static net.minecraft.util.Identifier identifier(
            final String namespace,
            final String path
    ) {
//#if MC >= 12005
//$$         return net.minecraft.util.Identifier.of(namespace, path);
//#else
        return new net.minecraft.util.Identifier(namespace, path);
//#endif
    }
}
