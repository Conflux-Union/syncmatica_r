package ch.endte.syncmatica.material;

import java.util.Objects;

/**
 * Aggregated state for a single material requirement.
 */
public final class MaterialProgressEntry {
    private final MaterialKey key;
    private final int requiredAmount;
    private int stockingSupplied;

    public MaterialProgressEntry(final MaterialKey key, final int requiredAmount) {
        this.key = Objects.requireNonNull(key, "key");
        this.requiredAmount = Math.max(0, requiredAmount);
    }

    public MaterialKey getKey() {
        return key;
    }

    public int getRequiredAmount() {
        return requiredAmount;
    }

    public int getStockingSupplied() {
        return stockingSupplied;
    }

    public int getTotalSupplied() {
        return stockingSupplied;
    }

    public int getMissingAmount() {
        return Math.max(0, requiredAmount - getTotalSupplied());
    }

    public boolean isFinished() {
        return getTotalSupplied() >= requiredAmount;
    }

    public void setStockingSupplied(final int stockingSupplied) {
        this.stockingSupplied = Math.max(0, stockingSupplied);
    }
}
