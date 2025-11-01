package ch.endte.syncmatica.material;

import java.util.function.Predicate;

/**
 * Legacy compatible wrapper for material progress exposed to clients.
 */
public class SyncmaticaMaterialEntry {
    private MaterialKey key;
    private int amountRequired;
    private int stockingSupplied;

    public static final Unfinished UNFINISHED = new Unfinished();

    public int getAmountRequired() {
        return amountRequired;
    }

    public MaterialKey getKey() {
        return key;
    }

    public void setKey(final MaterialKey key) {
        this.key = key;
    }

    public int getAmountPresent() {
        return stockingSupplied;
    }

    public int getAmountMissing() {
        return Math.max(0, amountRequired - getAmountPresent());
    }

    public boolean isFinished() {
        return getAmountPresent() >= amountRequired;
    }

    public int getStockingSupplied() {
        return stockingSupplied;
    }

    public void setStockingSupplied(final int stockingSupplied) {
        this.stockingSupplied = Math.max(0, stockingSupplied);
    }

    public void setAmountRequired(final int amountRequired) {
        this.amountRequired = Math.max(0, amountRequired);
    }

    public static class Unfinished implements Predicate<SyncmaticaMaterialEntry> {
        @Override
        public boolean test(final SyncmaticaMaterialEntry arg0) {
            return !arg0.isFinished();
        }
    }
}
