package cn.net.rms.syncmatica_r.material;

import ch.endte.syncmatica.extended_core.PlayerIdentifier;

import java.util.*;

public final class MaterialProgressEntry {
    private final MaterialKey key;
    private final int requiredAmount;
    private final Set<PlayerIdentifier> claimants = new LinkedHashSet<>();
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

    public void setStockingSupplied(final int stockingSupplied) {
        this.stockingSupplied = Math.max(0, stockingSupplied);
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

    public Collection<PlayerIdentifier> getClaimants() {
        return Collections.unmodifiableCollection(claimants);
    }

    public void clearClaimants() {
        claimants.clear();
    }

    public void addClaimer(final PlayerIdentifier id) {
        if (id != null) {
            claimants.add(id);
        }
    }

    public void removeClaimer(final PlayerIdentifier id) {
        if (id != null) {
            claimants.remove(id);
        }
    }

    public boolean hasClaimer(final PlayerIdentifier id) {
        return id != null && claimants.contains(id);
    }
}
