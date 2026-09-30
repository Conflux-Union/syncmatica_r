package cn.net.rms.syncmatica_r;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.material.StockingAreaDefinition;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.UUID;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

final class ServerPlacementStockingRefTest {

    private static ServerPlacement placement() {
        final ServerPlacement placement = new ServerPlacement(
                UUID.randomUUID(), "castle", UUID.randomUUID(), PlayerIdentifier.MISSING_PLAYER);
        placement.move("minecraft:overworld", BlockPos.ORIGIN, BlockRotation.NONE, BlockMirror.NONE);
        return placement;
    }

    @Test
    void writesRefAndNotEmbeddedArea() {
        final ServerPlacement placement = placement();
        final UUID ref = UUID.randomUUID();
        placement.setStockingAreaRef(ref);
        final JsonObject json = placement.toJson();
        assertEquals(ref.toString(), json.get("stockingAreaRef").getAsString());
        assertNull(json.get("stockingArea"));
    }

    @Test
    void omitsRefWhenUnset() {
        assertNull(placement().toJson().get("stockingAreaRef"));
    }

    @Test
    void readsRefAndKeepsLegacyEmbeddedAreaAside() {
        final String json = "{\"id\":\"00000000-0000-0000-0000-000000000001\","
                + "\"file_name\":\"castle\",\"hash\":\"00000000-0000-0000-0000-000000000002\","
                + "\"origin\":{\"dimension\":\"minecraft:overworld\",\"position\":[0,64,0]},"
                + "\"rotation\":\"NONE\",\"mirror\":\"NONE\","
                + "\"stockingAreaRef\":\"00000000-0000-0000-0000-0000000000aa\"}";
        final ServerPlacement placement = ServerPlacement.fromJson(
                new JsonParser().parse(json).getAsJsonObject(), null);
        assertEquals("00000000-0000-0000-0000-0000000000aa",
                placement.getStockingAreaRef().toString());
        assertNull(placement.getLegacyStockingArea());
    }

    @Test
    void legacyEmbeddedAreaLandsInLegacyField() {
        final String json = "{\"id\":\"00000000-0000-0000-0000-000000000001\","
                + "\"file_name\":\"castle\",\"hash\":\"00000000-0000-0000-0000-000000000002\","
                + "\"origin\":{\"dimension\":\"minecraft:overworld\",\"position\":[0,64,0]},"
                + "\"rotation\":\"NONE\",\"mirror\":\"NONE\","
                + "\"stockingArea\":{\"dimension\":\"minecraft:overworld\","
                + "\"minX\":0,\"minY\":64,\"minZ\":0,\"maxX\":9,\"maxY\":70,\"maxZ\":9}}";
        final ServerPlacement placement = ServerPlacement.fromJson(
                new JsonParser().parse(json).getAsJsonObject(), null);
        assertNull(placement.getStockingAreaRef());
        final StockingAreaDefinition legacy = placement.getLegacyStockingArea();
        assertEquals(9, legacy.getMax().getX());
    }
}
