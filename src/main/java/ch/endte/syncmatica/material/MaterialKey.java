package ch.endte.syncmatica.material;

import net.minecraft.util.Identifier;

import java.util.Objects;

public final class MaterialKey {
    private final Identifier itemId;
    private final String variant;

    public MaterialKey(final Identifier itemId, final String variant) {
        this.itemId = Objects.requireNonNull(itemId, "itemId");
        this.variant = variant == null ? "" : variant;
    }

    public Identifier getItemId() {
        return itemId;
    }

    public String getVariant() {
        return variant;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final MaterialKey other = (MaterialKey) obj;
        return itemId.equals(other.itemId) && variant.equals(other.variant);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, variant);
    }

    @Override
    public String toString() {
        return variant.isEmpty() ? itemId.toString() : itemId + "#" + variant;
    }
}
