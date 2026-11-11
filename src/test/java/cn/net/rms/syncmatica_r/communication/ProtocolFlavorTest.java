package cn.net.rms.syncmatica_r.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ProtocolFlavorTest {

    @Test
    void legacyNamespaceMapsToLegacyFlavor() {
        assertEquals(ProtocolFlavor.LEGACY, ProtocolFlavor.fromNamespace("syncmatica"));
    }

    @Test
    void ownNamespaceMapsToNewFlavor() {
        assertEquals(ProtocolFlavor.NEW, ProtocolFlavor.fromNamespace("syncmatica_r"));
    }

    @Test
    void unknownNamespaceDefaultsToNewFlavor() {
        assertEquals(ProtocolFlavor.NEW, ProtocolFlavor.fromNamespace("minecraft"));
        assertEquals(ProtocolFlavor.NEW, ProtocolFlavor.fromNamespace(""));
        assertEquals(ProtocolFlavor.NEW, ProtocolFlavor.fromNamespace(null));
    }
}
