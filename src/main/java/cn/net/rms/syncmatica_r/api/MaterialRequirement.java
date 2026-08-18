package cn.net.rms.syncmatica_r.api;

import java.util.Objects;

/**
 * A material amount still missing from Syncmatica stocking areas.
 *
 * <p>The item id and variant are strings so consumers do not need to share
 * Syncmatica's Minecraft mappings or internal material model.</p>
 */
public record MaterialRequirement(String itemId, String variant, int missingAmount) {

    public MaterialRequirement {
        itemId = Objects.requireNonNull(itemId, "itemId");
        variant = variant == null ? "" : variant;
        if (itemId.isEmpty()) {
            throw new IllegalArgumentException("itemId must not be empty");
        }
        if (missingAmount <= 0) {
            throw new IllegalArgumentException("missingAmount must be positive");
        }
    }
}
