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
