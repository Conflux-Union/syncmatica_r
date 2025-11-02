package ch.endte.syncmatica.material;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.math.BlockPos;

public final class StockingAreaDefinition {
    private static final String FIELD_DIMENSION = "dimension";
    private static final String FIELD_MIN_X = "minX";
    private static final String FIELD_MIN_Y = "minY";
    private static final String FIELD_MIN_Z = "minZ";
    private static final String FIELD_MAX_X = "maxX";
    private static final String FIELD_MAX_Y = "maxY";
    private static final String FIELD_MAX_Z = "maxZ";

    private final String dimensionId;
    private final BlockPos min;
    private final BlockPos max;

    public StockingAreaDefinition(final String dimensionId, final BlockPos cornerA, final BlockPos cornerB) {
        this.dimensionId = dimensionId;
        final int minX = Math.min(cornerA.getX(), cornerB.getX());
        final int minY = Math.min(cornerA.getY(), cornerB.getY());
        final int minZ = Math.min(cornerA.getZ(), cornerB.getZ());
        final int maxX = Math.max(cornerA.getX(), cornerB.getX());
        final int maxY = Math.max(cornerA.getY(), cornerB.getY());
        final int maxZ = Math.max(cornerA.getZ(), cornerB.getZ());
        min = new BlockPos(minX, minY, minZ);
        max = new BlockPos(maxX, maxY, maxZ);
    }

    public static StockingAreaDefinition fromJson(final JsonObject json) {
        if (json == null || !json.has(FIELD_DIMENSION)) {
            return null;
        }
        final String dimension = json.get(FIELD_DIMENSION).getAsString();
        final BlockPos min = new BlockPos(
                json.get(FIELD_MIN_X).getAsInt(),
                json.get(FIELD_MIN_Y).getAsInt(),
                json.get(FIELD_MIN_Z).getAsInt()
        );
        final BlockPos max = new BlockPos(
                json.get(FIELD_MAX_X).getAsInt(),
                json.get(FIELD_MAX_Y).getAsInt(),
                json.get(FIELD_MAX_Z).getAsInt()
        );
        return new StockingAreaDefinition(dimension, min, max);
    }

    public String getDimensionId() {
        return dimensionId;
    }

    public BlockPos getMin() {
        return min;
    }

    public BlockPos getMax() {
        return max;
    }

    public JsonObject toJson() {
        final JsonObject json = new JsonObject();
        json.add(FIELD_DIMENSION, new JsonPrimitive(dimensionId));
        json.add(FIELD_MIN_X, new JsonPrimitive(min.getX()));
        json.add(FIELD_MIN_Y, new JsonPrimitive(min.getY()));
        json.add(FIELD_MIN_Z, new JsonPrimitive(min.getZ()));
        json.add(FIELD_MAX_X, new JsonPrimitive(max.getX()));
        json.add(FIELD_MAX_Y, new JsonPrimitive(max.getY()));
        json.add(FIELD_MAX_Z, new JsonPrimitive(max.getZ()));
        return json;
    }
}
