package cn.net.rms.syncmatica_r.extended_core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonArray;
import org.junit.jupiter.api.Test;

final class SubRegionDataTest {
    @Test
    void emptyModifiedDataSerializesAsAnEmptyArray() {
        final SubRegionData data = SubRegionData.fromJson(new JsonArray());

        assertEquals(new JsonArray(), data.toJson());
    }
}
