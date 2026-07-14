package cn.net.rms.syncmatica_r.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

final class QuotaServiceTest {
    @Test
    void negativeConfiguredLimitIsClampedToZero() {
        final JsonObject configured = new JsonObject();
        configured.addProperty("limit", -1);
        final QuotaService service = new QuotaService();

        service.configure(new JsonConfiguration(configured));

        assertEquals(0, service.limit);
    }
}
