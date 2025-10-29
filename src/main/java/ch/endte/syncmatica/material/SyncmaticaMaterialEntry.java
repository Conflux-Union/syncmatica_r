package ch.endte.syncmatica.material;

import java.util.function.Predicate;

/**
 * Legacy compatible wrapper for material progress exposed to clients.
 */
public class SyncmaticaMaterialEntry {
    private MaterialKey key;
    private int amountRequired;
    private int playerSupplied;
    private int stockingSupplied;
    private String claimedBy;

    public static final Unclaimed UNCLAIMED = new Unclaimed();
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
        return playerSupplied + stockingSupplied;
    }

    public int getAmountMissing() {
        return Math.max(0, amountRequired - getAmountPresent());
    }

    public boolean isClaimed() {
        return claimedBy != null;
    }

    public String getClaimedBy() {
        return claimedBy;
    }

    public boolean isFinished() {
        return getAmountPresent() >= amountRequired;
    }

    public int getPlayerSupplied() {
        return playerSupplied;
    }

    public void setPlayerSupplied(final int playerSupplied) {
        this.playerSupplied = Math.max(0, playerSupplied);
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

    public void setClaimedBy(final String claimedBy) {
        this.claimedBy = claimedBy;
    }

    public static class Unclaimed implements Predicate<SyncmaticaMaterialEntry> {
        @Override
        public boolean test(final SyncmaticaMaterialEntry arg0) {
            return !arg0.isClaimed();
        }
    }

    public static class Unfinished implements Predicate<SyncmaticaMaterialEntry> {
        @Override
        public boolean test(final SyncmaticaMaterialEntry arg0) {
            return !arg0.isFinished();
        }
    }
}
