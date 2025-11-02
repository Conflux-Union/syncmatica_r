package ch.endte.syncmatica.material;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class SyncmaticaMaterialEntry {
    public static final Unfinished UNFINISHED = new Unfinished();
    private final List<String> claimers = new ArrayList<>();
    private MaterialKey key;
    private int amountRequired;
    private int stockingSupplied;

    public int getAmountRequired() {
        return amountRequired;
    }

    public void setAmountRequired(final int amountRequired) {
        this.amountRequired = Math.max(0, amountRequired);
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

    public List<String> getClaimers() {
        return Collections.unmodifiableList(claimers);
    }

    public void setClaimers(final Collection<String> names) {
        claimers.clear();
        if (names != null) {
            claimers.addAll(names);
        }
    }

    public static class Unfinished implements Predicate<SyncmaticaMaterialEntry> {
        @Override
        public boolean test(final SyncmaticaMaterialEntry arg0) {
            return !arg0.isFinished();
        }
    }
}
