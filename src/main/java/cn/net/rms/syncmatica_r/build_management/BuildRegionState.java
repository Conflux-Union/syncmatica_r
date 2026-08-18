package cn.net.rms.syncmatica_r.build_management;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The sub-regions of one shared schematic, keyed by region name.
 *
 * <p>Keyed by name rather than by index on purpose: a name survives a
 * re-extraction, a re-share and a server restart, so a claim stays attached to
 * the part of the build it was made for. A region that disappears from the
 * schematic loses its claim with it.
 */
public class BuildRegionState {
    private final Map<String, BuildRegion> regions = new LinkedHashMap<>();

    public Collection<BuildRegion> getRegions() {
        return Collections.unmodifiableCollection(regions.values());
    }

    public BuildRegion getOrCreate(final String regionName, final long requiredBlocks) {
        return regions.computeIfAbsent(regionName, missing -> new BuildRegion(missing, requiredBlocks));
    }

    public BuildRegion get(final String regionName) {
        return regions.get(regionName);
    }

    public void clear() {
        regions.clear();
    }

    public boolean isEmpty() {
        return regions.isEmpty();
    }
}
