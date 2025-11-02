package ch.endte.syncmatica.material;

import net.minecraft.util.Identifier;

import java.util.Objects;

public record MaterialKey(Identifier itemId, String variant) {
    public MaterialKey(final Identifier itemId, final String variant) {
        this.itemId = Objects.requireNonNull(itemId, "itemId");
        this.variant = variant == null ? "" : variant;
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
    public String toString() {
        return variant.isEmpty() ? itemId.toString() : itemId + "#" + variant;
    }
}
