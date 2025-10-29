package ch.endte.syncmatica.material;

import ch.endte.syncmatica.extended_core.PlayerIdentifier;

import java.util.Objects;

/**
 * Aggregated state for a single material requirement.
 */
public final class MaterialProgressEntry {
    private final MaterialKey key;
    private final int requiredAmount;
    private int playerSupplied;
    private int stockingSupplied;
    private PlayerIdentifier claimedBy;

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

    public int getPlayerSupplied() {
        return playerSupplied;
    }

    public int getStockingSupplied() {
        return stockingSupplied;
    }

    public int getTotalSupplied() {
        return playerSupplied + stockingSupplied;
    }

    public int getMissingAmount() {
        return Math.max(0, requiredAmount - getTotalSupplied());
    }

    public PlayerIdentifier getClaimedBy() {
        return claimedBy;
    }

    public boolean isFinished() {
        return getTotalSupplied() >= requiredAmount;
    }

    public void setPlayerSupplied(final int playerSupplied) {
        this.playerSupplied = Math.max(0, playerSupplied);
    }

    public void setStockingSupplied(final int stockingSupplied) {
        this.stockingSupplied = Math.max(0, stockingSupplied);
    }

    public void setClaimedBy(final PlayerIdentifier claimedBy) {
        this.claimedBy = claimedBy;
    }
}
