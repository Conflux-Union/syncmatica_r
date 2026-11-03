package cn.net.rms.syncmatica_r.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

final class PlacementAccessServiceConfigurationTest {

    @Test
    void movingOtherPlayersPlacementsIsDeniedByDefault() {
        final PlacementAccessService service = new PlacementAccessService();

        final JsonObject defaults = new JsonObject();
        service.getDefaultConfiguration(new JsonConfiguration(defaults));

        assertEquals(PlacementAccessService.ALLOW_ANYONE_TO_MOVE_DEFAULT,
                defaults.get("allow_anyone_to_move").getAsBoolean());
        assertFalse(service.isAllowAnyoneToMove());
    }

    @Test
    void configureReadsTheToggleFromFileValues() {
        final PlacementAccessService service = new PlacementAccessService();

        final JsonObject enabled = new JsonObject();
        enabled.addProperty("allow_anyone_to_move", true);
        service.configure(new JsonConfiguration(enabled));
        assertTrue(service.isAllowAnyoneToMove());

        final JsonObject disabled = new JsonObject();
        disabled.addProperty("allow_anyone_to_move", false);
        service.configure(new JsonConfiguration(disabled));
        assertFalse(service.isAllowAnyoneToMove());
    }

    @Test
    void missingKeyKeepsTheDefaultValue() {
        final PlacementAccessService service = new PlacementAccessService();

        service.configure(new JsonConfiguration(new JsonObject()));

        assertFalse(service.isAllowAnyoneToMove());
    }

    @Test
    void registersThePlacementSectionToggle() {
        final PlacementAccessService service = new PlacementAccessService();

        final ConfigRegistry registry = new ConfigRegistry();
        service.registerConfigOptions(registry);

        final ConfigOption<?> option = registry.find("placement", "allow_anyone_to_move");
        assertEquals(Boolean.FALSE, option.getDefaultValue());
    }
}
