package cn.net.rms.syncmatica_r.web;

import java.util.List;

/**
 * Immutable values crossing the game-state boundary into the web layer.
 *
 * <p>Identifiers are strings on purpose: none of these records retains a
 * mutable placement, material entry, region, player identifier, or block
 * position.</p>
 */
public final class WebDtos {
    private WebDtos() {
    }

    public record Player(String id, String name) {
    }

    public record Position(String dimension, int x, int y, int z) {
    }

    public record ProjectSummary(
            String id,
            String name,
            String ownerName,
            long lastModifiedAt
    ) {
    }

    public record ProjectDetail(
            String id,
            String name,
            String fileName,
            String hash,
            Player owner,
            Player lastModifiedBy,
            long createdAt,
            long lastModifiedAt,
            Position position,
            String rotation,
            String mirror,
            String materialAvailability
    ) {
    }

    public record Material(
            String itemId,
            String variant,
            long required,
            long supplied,
            long missing,
            int progressPercent,
            List<Player> claimants
    ) {
        public Material {
            claimants = List.copyOf(claimants);
        }
    }

    public record MaterialSummary(
            String itemId,
            String variant,
            long required,
            long supplied,
            long missing,
            int progressPercent
    ) {
    }

    public record StockingArea(
            String dimension,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            long volume
    ) {
    }

    public record BuildRegion(
            String name,
            long requiredBlocks,
            long placedBlocks,
            boolean scanned,
            long lastScanAt,
            int progressPercent,
            List<Player> claimants
    ) {
        public BuildRegion {
            claimants = List.copyOf(claimants);
        }
    }
}
