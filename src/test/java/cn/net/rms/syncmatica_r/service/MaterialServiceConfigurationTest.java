package cn.net.rms.syncmatica_r.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

final class MaterialServiceConfigurationTest {
    @Test
    void publishesResourceLimitDefaultsAndClampsConfiguredSchematicSize() {
        final MaterialService service = new MaterialService();
        try {
            final JsonObject defaults = new JsonObject();
            service.getDefaultConfiguration(new JsonConfiguration(defaults));

            assertEquals(MaterialService.MAX_STOCKING_AREA_BLOCKS_DEFAULT,
                    defaults.get("max_stocking_area_blocks").getAsInt());
            assertEquals(MaterialService.MAX_SCHEMATIC_BLOCKS_DEFAULT,
                    defaults.get("max_schematic_blocks").getAsInt());

            final JsonObject configured = new JsonObject();
            configured.addProperty("max_schematic_megabytes", Integer.MAX_VALUE);
            service.configure(new JsonConfiguration(configured));

            assertEquals(64L * 1024L * 1024L, service.getMaxSchematicBytes());
            assertTrue(service.isEnabled());
        } finally {
            service.shutdown();
        }
    }
}
