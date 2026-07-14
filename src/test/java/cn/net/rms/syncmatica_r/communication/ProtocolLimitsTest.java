package cn.net.rms.syncmatica_r.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class ProtocolLimitsTest {
    @Test
    void acceptsValuesInsideProtocolBounds() {
        assertEquals(4, ProtocolLimits.requireCount(4, 8, "count"));
        assertEquals(1, ProtocolLimits.requireIndex(1, 2, "index"));
        assertEquals(ProtocolLimits.MAX_PACKET_BYTES,
                ProtocolLimits.requirePacketSize(ProtocolLimits.MAX_PACKET_BYTES));
        assertEquals(128, ProtocolLimits.requireTransferChunk(128, 128));
        assertTrue(ProtocolLimits.isNestedContainerDepthAllowed(ProtocolLimits.MAX_NESTED_CONTAINER_DEPTH));
        assertFalse(ProtocolLimits.isNestedContainerDepthAllowed(ProtocolLimits.MAX_NESTED_CONTAINER_DEPTH + 1));
    }

    @Test
    void rejectsNegativeOrOversizedCounts() {
        assertThrows(IllegalArgumentException.class, () -> ProtocolLimits.requireCount(-1, 8, "count"));
        assertThrows(IllegalArgumentException.class, () -> ProtocolLimits.requireCount(9, 8, "count"));
    }

    @Test
    void rejectsInvalidIndexesAndTransferChunks() {
        assertThrows(IllegalArgumentException.class, () -> ProtocolLimits.requireIndex(2, 2, "index"));
        assertThrows(IllegalArgumentException.class,
                () -> ProtocolLimits.requirePacketSize(ProtocolLimits.MAX_PACKET_BYTES + 1));
        assertThrows(IllegalArgumentException.class, () -> ProtocolLimits.requireTransferChunk(0, 32));
        assertThrows(IllegalArgumentException.class, () -> ProtocolLimits.requireTransferChunk(64, 32));
        assertThrows(IllegalArgumentException.class, () -> ProtocolLimits.requireTransferChunk(1, 32));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProtocolLimits.requireTransferChunk(ProtocolLimits.MAX_TRANSFER_CHUNK_BYTES + 1, Integer.MAX_VALUE)
        );
    }
}
