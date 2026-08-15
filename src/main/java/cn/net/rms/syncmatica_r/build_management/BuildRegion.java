package cn.net.rms.syncmatica_r.build_management;

import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * One sub-region of a shared schematic, named after the region key in the
 * litematic file, together with who signed up to build it.
 *
 * <p>Claimants are stored as a set even though the server allows a single one at
 * a time. Keeping the shape open is what lets the rule live in one place —
 * {@link BuildService} — rather than being baked into the storage and the wire
 * format as well.
 */
public final class BuildRegion {
    private final String regionName;
    private final long requiredBlocks;
    private final Set<PlayerIdentifier> claimants = new LinkedHashSet<>();
    private RegionScanCache scanCache;
    private long placedBlocks;
    private long lastScanMillis;

    public BuildRegion(final String regionName, final long requiredBlocks) {
        this.regionName = Objects.requireNonNull(regionName, "regionName");
        this.requiredBlocks = Math.max(0L, requiredBlocks);
    }

    public String getRegionName() {
        return regionName;
    }

    /**
     * @return how many block positions in this region need a material placed.
     *         Air, the upper half of doors and beds, and blocks without an item
     *         form are excluded, matching how the material list is counted.
     */
    public long getRequiredBlocks() {
        return requiredBlocks;
    }

    /** @return how many of those positions already hold the right block */
    public long getPlacedBlocks() {
        return placedBlocks;
    }

    public long getLastScanMillis() {
        return lastScanMillis;
    }

    public void recordScan(final long placedBlocks, final long scanMillis) {
        this.placedBlocks = Math.max(0L, Math.min(placedBlocks, requiredBlocks));
        this.lastScanMillis = scanMillis;
    }

    /**
     * The per-chunk counts {@link #getPlacedBlocks()} is summed from. Held by the
     * server that measures the region; a client is sent the total instead, so
     * this stays null there.
     */
    public RegionScanCache getScanCache() {
        return scanCache;
    }

    public void setScanCache(final RegionScanCache scanCache) {
        this.scanCache = scanCache;
    }

    /**
     * Forgets everything measured about this region, counts and total alike, so
     * the next pass rebuilds both from the world.
     */
    public void forgetScan() {
        scanCache = null;
        placedBlocks = 0L;
        lastScanMillis = 0L;
    }

    /** A region nobody has scanned yet reports no progress rather than zero. */
    public boolean isScanned() {
        return lastScanMillis > 0L;
    }

    public boolean isComplete() {
        return isScanned() && placedBlocks >= requiredBlocks;
    }

    /** @return 0-100, or -1 when this region has never been scanned */
    public int getCompletionPercent() {
        if (!isScanned()) {
            return -1;
        }
        if (requiredBlocks <= 0L) {
            return 100;
        }
        return (int) Math.min(100L, placedBlocks * 100L / requiredBlocks);
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

    public boolean isClaimed() {
        return !claimants.isEmpty();
    }
}
