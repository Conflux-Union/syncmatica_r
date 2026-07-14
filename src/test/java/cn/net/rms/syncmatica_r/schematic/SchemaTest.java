package cn.net.rms.syncmatica_r.schematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

final class SchemaTest {

    @Test
    void returnsExactMatchForKnownDataVersion() {
        assertEquals("1.20.1", Schema.getVersionString(3465));
        assertEquals("1.17.1", Schema.getVersionString(2730));
    }

    @Test
    void returnsClosestVersionAtOrBelowUnknownDataVersion() {
        assertEquals("1.20.1", Schema.getVersionString(3470));
        assertEquals("1.21.11", Schema.getVersionString(4700));
    }

    @Test
    void returnsNullBelowOldestKnownDataVersion() {
        assertNull(Schema.getVersionString(99));
        assertNull(Schema.getVersionString(-1));
    }

    @Test
    void clampsFarFutureDataVersionsToFutureMarker() {
        assertEquals("FUTURE", Schema.getVersionString(999_999));
    }
}
