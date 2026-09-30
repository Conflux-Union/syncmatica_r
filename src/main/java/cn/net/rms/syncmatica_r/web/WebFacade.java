package cn.net.rms.syncmatica_r.web;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.build_management.BuildRegion;
import cn.net.rms.syncmatica_r.communication.PlacementAccessPolicy;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifier;
import cn.net.rms.syncmatica_r.material.MaterialKey;
import cn.net.rms.syncmatica_r.material.MaterialProgressEntry;
import cn.net.rms.syncmatica_r.material.StockingAreaDefinition;
import cn.net.rms.syncmatica_r.material.StockingAreaRegistry;
import cn.net.rms.syncmatica_r.service.BuildService;
import cn.net.rms.syncmatica_r.service.MaterialService;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

/**
 * Server-thread facade for web reads and ordinary-player writes.
 *
 * <p>Every read builds a detached immutable snapshot. Permission decisions are
 * inputs to mutations rather than fields smuggled into a DTO.</p>
 */
public final class WebFacade {
    private final Context context;
    private final Predicate<String> loadedDimension;
    private final Function<MaterialKey, String> materialTranslationKey;

    public enum StockingAreaOutcome {
        UPDATED,
        UNCHANGED,
        UNKNOWN_PLACEMENT,
        UNKNOWN_AREA,
        FORBIDDEN,
        DISABLED
    }

    public enum StockingAreaRegistryOutcome {
        CREATED,
        UPDATED,
        DELETED,
        INVALID_NAME,
        DUPLICATE_NAME,
        RESERVED_NAME,
        TOO_LARGE,
        NOT_FOUND,
        IN_USE,
        FORBIDDEN,
        DIMENSION_NOT_LOADED,
        DISABLED
    }

    /**
     * Safe default for consumers that cannot prove which dimensions are loaded.
     * Such consumers may read snapshots but cannot register stocking areas.
     */
    public WebFacade(final Context context) {
        this(context, dimension -> false);
    }

    public WebFacade(final Context context, final Predicate<String> loadedDimension) {
        this(context, loadedDimension, WebFacade::translationKey);
    }

    WebFacade(
            final Context context,
            final Predicate<String> loadedDimension,
            final Function<MaterialKey, String> materialTranslationKey
    ) {
        this.context = Objects.requireNonNull(context, "context");
        this.loadedDimension = Objects.requireNonNull(loadedDimension, "loadedDimension");
        this.materialTranslationKey =
                Objects.requireNonNull(materialTranslationKey, "materialTranslationKey");
    }

    public List<WebDtos.ProjectSummary> listProjects() {
        final List<WebDtos.ProjectSummary> result = new ArrayList<>();
        for (final ServerPlacement placement : context.getSyncmaticManager().getAll()) {
            result.add(new WebDtos.ProjectSummary(
                    placement.getId().toString(),
                    placement.getName(),
                    nameOf(placement.getOwner()),
                    placement.getLastModifiedAtMillis()
            ));
        }
        result.sort(Comparator.comparing(WebDtos.ProjectSummary::name)
                .thenComparing(WebDtos.ProjectSummary::id));
        return List.copyOf(result);
    }

    public Optional<WebDtos.ProjectDetail> getProject(final UUID placementId) {
        final ServerPlacement placement = context.getSyncmaticManager().getPlacement(placementId);
        if (placement == null) {
            return Optional.empty();
        }
        final BlockPos position = placement.getPosition();
        return Optional.of(new WebDtos.ProjectDetail(
                placement.getId().toString(),
                placement.getName(),
                placement.getFileName(),
                placement.getHash().toString(),
                player(placement.getOwner()),
                player(placement.getLastModifiedBy()),
                placement.getCreatedAtMillis(),
                placement.getLastModifiedAtMillis(),
                new WebDtos.Position(
                        placement.getDimension(),
                        position.getX(),
                        position.getY(),
                        position.getZ()
                ),
                placement.getRotation().name(),
                placement.getMirror().name(),
                placement.getMaterialAvailability().name()
        ));
    }

    public List<WebDtos.Material> getMaterials(final UUID placementId) {
        final ServerPlacement placement = context.getSyncmaticManager().getPlacement(placementId);
        if (placement == null) {
            return List.of();
        }
        final List<WebDtos.Material> result = new ArrayList<>();
        for (final MaterialProgressEntry entry : placement.getMaterialProgress().getEntries()) {
            final long required = entry.getRequiredAmount();
            final long supplied = entry.getTotalSupplied();
            result.add(new WebDtos.Material(
                    entry.getKey().itemId().toString(),
                    materialTranslationKey.apply(entry.getKey()),
                    fallbackName(entry.getKey()),
                    entry.getKey().variant(),
                    required,
                    supplied,
                    Math.max(0L, required - supplied),
                    progressPercent(supplied, required),
                    players(entry.getClaimants())
            ));
        }
        result.sort(Comparator.comparing(WebDtos.Material::itemId)
                .thenComparing(WebDtos.Material::variant));
        return List.copyOf(result);
    }

    public List<WebDtos.MaterialSummary> getMaterialSummary() {
        final Map<MaterialKey, MaterialTotals> totals = new LinkedHashMap<>();
        for (final ServerPlacement placement : context.getSyncmaticManager().getAll()) {
            for (final MaterialProgressEntry entry : placement.getMaterialProgress().getEntries()) {
                final MaterialTotals total = totals.computeIfAbsent(entry.getKey(), unused -> new MaterialTotals());
                total.required = saturatedAdd(total.required, entry.getRequiredAmount());
                total.supplied = saturatedAdd(total.supplied, entry.getTotalSupplied());
            }
        }
        final List<WebDtos.MaterialSummary> result = new ArrayList<>();
        for (final Map.Entry<MaterialKey, MaterialTotals> entry : totals.entrySet()) {
            final MaterialTotals total = entry.getValue();
            result.add(new WebDtos.MaterialSummary(
                    entry.getKey().itemId().toString(),
                    materialTranslationKey.apply(entry.getKey()),
                    fallbackName(entry.getKey()),
                    entry.getKey().variant(),
                    total.required,
                    total.supplied,
                    Math.max(0L, total.required - total.supplied),
                    progressPercent(total.supplied, total.required)
            ));
        }
        result.sort(Comparator.comparing(WebDtos.MaterialSummary::itemId)
                .thenComparing(WebDtos.MaterialSummary::variant));
        return List.copyOf(result);
    }

    public Optional<WebDtos.StockingArea> getStockingArea(final UUID placementId) {
        final ServerPlacement placement = context.getSyncmaticManager().getPlacement(placementId);
        if (placement == null) {
            return Optional.empty();
        }
        final MaterialService service = context.getMaterialService();
        final StockingAreaDefinition area = service.resolveStockingArea(placement);
        if (area == null) {
            return Optional.empty();
        }
        final StockingAreaRegistry.Entry bound = service.getStockingAreaRegistry()
                .getById(placement.getStockingAreaRef());
        return Optional.of(new WebDtos.StockingArea(
                area.getDimensionId(),
                area.getMin().getX(),
                area.getMin().getY(),
                area.getMin().getZ(),
                area.getMax().getX(),
                area.getMax().getY(),
                area.getMax().getZ(),
                area.getVolume(),
                bound == null ? null : bound.getId().toString(),
                bound == null ? null : bound.getName()
        ));
    }

    public List<WebDtos.BuildRegion> getBuildRegions(final UUID placementId) {
        final ServerPlacement placement = context.getSyncmaticManager().getPlacement(placementId);
        if (placement == null) {
            return List.of();
        }
        final List<WebDtos.BuildRegion> result = new ArrayList<>();
        for (final BuildRegion region : placement.getBuildRegions().getRegions()) {
            result.add(new WebDtos.BuildRegion(
                    region.getRegionName(),
                    region.getRequiredBlocks(),
                    region.getPlacedBlocks(),
                    region.isScanned(),
                    region.getLastScanMillis(),
                    region.isScanned()
                            ? progressPercent(region.getPlacedBlocks(), region.getRequiredBlocks())
                            : -1,
                    players(region.getClaimants())
            ));
        }
        result.sort(Comparator.comparing(WebDtos.BuildRegion::name));
        return List.copyOf(result);
    }

    /**
     * Aggregates every material and build region the player has claimed
     * across all placements, so the web client polls one endpoint instead of
     * one request per project.
     */
    public WebDtos.MyClaims getMyClaims(final UUID playerId) {
        final List<WebDtos.ClaimedMaterial> materials = new ArrayList<>();
        final List<WebDtos.ClaimedRegion> regions = new ArrayList<>();
        for (final ServerPlacement placement : context.getSyncmaticManager().getAll()) {
            for (final MaterialProgressEntry entry : placement.getMaterialProgress().getEntries()) {
                if (isClaimedBy(entry.getClaimants(), playerId)) {
                    final long required = entry.getRequiredAmount();
                    final long supplied = entry.getTotalSupplied();
                    materials.add(new WebDtos.ClaimedMaterial(
                            placement.getId().toString(),
                            placement.getName(),
                            entry.getKey().itemId().toString(),
                            materialTranslationKey.apply(entry.getKey()),
                            fallbackName(entry.getKey()),
                            entry.getKey().variant(),
                            required,
                            supplied,
                            Math.max(0L, required - supplied),
                            progressPercent(supplied, required)
                    ));
                }
            }
            for (final BuildRegion region : placement.getBuildRegions().getRegions()) {
                if (isClaimedBy(region.getClaimants(), playerId)) {
                    regions.add(new WebDtos.ClaimedRegion(
                            placement.getId().toString(),
                            placement.getName(),
                            region.getRegionName(),
                            region.getRequiredBlocks(),
                            region.getPlacedBlocks(),
                            region.isScanned(),
                            region.getLastScanMillis(),
                            region.isScanned()
                                    ? progressPercent(region.getPlacedBlocks(), region.getRequiredBlocks())
                                    : -1
                    ));
                }
            }
        }
        materials.sort(Comparator.comparing(WebDtos.ClaimedMaterial::projectName)
                .thenComparing(WebDtos.ClaimedMaterial::itemId)
                .thenComparing(WebDtos.ClaimedMaterial::variant));
        regions.sort(Comparator.comparing(WebDtos.ClaimedRegion::projectName)
                .thenComparing(WebDtos.ClaimedRegion::name));
        return new WebDtos.MyClaims(List.copyOf(materials), List.copyOf(regions));
    }

    private static boolean isClaimedBy(final java.util.Collection<PlayerIdentifier> claimants,
                                       final UUID playerId) {
        for (final PlayerIdentifier claimant : claimants) {
            if (claimant != null && playerId.equals(claimant.uuid)) {
                return true;
            }
        }
        return false;
    }

    public MaterialService.ClaimOutcome setMaterialClaim(final UUID placementId, final MaterialKey key,
                                                         final PlayerIdentifier player, final boolean claimed) {
        return context.getMaterialService().setClaim(
                context.getSyncmaticManager().getPlacement(placementId), key, player, claimed);
    }

    public MaterialService.ReleaseClaimsOutcome releaseMaterialClaims(
            final UUID placementId,
            final PlayerIdentifier player
    ) {
        return context.getMaterialService().releaseClaims(
                context.getSyncmaticManager().getPlacement(placementId), player);
    }

    public BuildService.ClaimOutcome setBuildClaim(final UUID placementId, final String regionName,
                                                   final PlayerIdentifier player, final boolean claimed) {
        return context.getBuildService().setClaim(
                context.getSyncmaticManager().getPlacement(placementId), regionName, player, claimed);
    }

    public List<WebDtos.StockingAreaRecord> listStockingAreas() {
        final MaterialService service = context.getMaterialService();
        if (service == null) {
            return List.of();
        }
        final List<WebDtos.StockingAreaRecord> result = new ArrayList<>();
        for (final StockingAreaRegistry.Entry entry : service.getStockingAreaRegistry().getAll()) {
            result.add(stockingAreaRecord(service, entry));
        }
        result.sort(Comparator.comparing(WebDtos.StockingAreaRecord::name));
        return List.copyOf(result);
    }

    /**
     * Any authenticated actor may create an area; it is owned by the creating
     * player (owner = actor), matching the command layer's player path.
     * Elevation grants no extra create rights and only keeps the mutation
     * surface uniform with the update and delete signatures.
     */
    public StockingAreaRegistryOutcome createStockingArea(final String name,
                                                          final PlayerIdentifier actor,
                                                          final boolean elevated,
                                                          final String dimension,
                                                          final int minX,
                                                          final int minY,
                                                          final int minZ,
                                                          final int maxX,
                                                          final int maxY,
                                                          final int maxZ) {
        final MaterialService service = context.getMaterialService();
        if (service == null || !service.isEnabled()) {
            return StockingAreaRegistryOutcome.DISABLED;
        }
        // The reserved default entry is bootstrapped by migration and the
        // server only; the web never writes it, regardless of elevation.
        if (StockingAreaRegistry.RESERVED_DEFAULT_NAME.equals(name)) {
            return StockingAreaRegistryOutcome.RESERVED_NAME;
        }
        if (dimension == null || !loadedDimension.test(dimension)) {
            return StockingAreaRegistryOutcome.DIMENSION_NOT_LOADED;
        }
        return switch (service.createStockingArea(
                name, definition(dimension, minX, minY, minZ, maxX, maxY, maxZ),
                actor == null ? null : actor.uuid)) {
            case CREATED -> StockingAreaRegistryOutcome.CREATED;
            case INVALID_NAME -> StockingAreaRegistryOutcome.INVALID_NAME;
            case DUPLICATE_NAME -> StockingAreaRegistryOutcome.DUPLICATE_NAME;
            case RESERVED_NAME -> StockingAreaRegistryOutcome.RESERVED_NAME;
            case TOO_LARGE -> StockingAreaRegistryOutcome.TOO_LARGE;
        };
    }

    public StockingAreaRegistryOutcome updateStockingArea(final UUID areaId,
                                                          final PlayerIdentifier actor,
                                                          final boolean elevated,
                                                          final String dimension,
                                                          final int minX,
                                                          final int minY,
                                                          final int minZ,
                                                          final int maxX,
                                                          final int maxY,
                                                          final int maxZ) {
        final MaterialService service = context.getMaterialService();
        if (service == null || !service.isEnabled()) {
            return StockingAreaRegistryOutcome.DISABLED;
        }
        final StockingAreaRegistry.Entry entry = service.getStockingAreaRegistry().getById(areaId);
        if (entry == null) {
            return StockingAreaRegistryOutcome.NOT_FOUND;
        }
        if (!canManageStockingAreaEntry(actor, entry, elevated)) {
            return StockingAreaRegistryOutcome.FORBIDDEN;
        }
        if (dimension == null || !loadedDimension.test(dimension)) {
            return StockingAreaRegistryOutcome.DIMENSION_NOT_LOADED;
        }
        return switch (service.updateStockingArea(
                areaId, definition(dimension, minX, minY, minZ, maxX, maxY, maxZ))) {
            case UPDATED -> StockingAreaRegistryOutcome.UPDATED;
            case TOO_LARGE -> StockingAreaRegistryOutcome.TOO_LARGE;
            case NOT_FOUND -> StockingAreaRegistryOutcome.NOT_FOUND;
        };
    }

    public StockingAreaRegistryOutcome deleteStockingArea(final UUID areaId,
                                                          final boolean force,
                                                          final PlayerIdentifier actor,
                                                          final boolean elevated) {
        final MaterialService service = context.getMaterialService();
        if (service == null || !service.isEnabled()) {
            return StockingAreaRegistryOutcome.DISABLED;
        }
        final StockingAreaRegistry.Entry entry = service.getStockingAreaRegistry().getById(areaId);
        if (entry == null) {
            return StockingAreaRegistryOutcome.NOT_FOUND;
        }
        if (!canManageStockingAreaEntry(actor, entry, elevated)) {
            return StockingAreaRegistryOutcome.FORBIDDEN;
        }
        return switch (service.deleteStockingArea(areaId, force)) {
            case DELETED -> StockingAreaRegistryOutcome.DELETED;
            case IN_USE -> StockingAreaRegistryOutcome.IN_USE;
            case NOT_FOUND -> StockingAreaRegistryOutcome.NOT_FOUND;
            case RESERVED_NAME -> StockingAreaRegistryOutcome.RESERVED_NAME;
        };
    }

    /** Names of the projects bound to the area, for the in-use error detail. */
    public List<String> stockingAreaReferenceNames(final UUID areaId) {
        final MaterialService service = context.getMaterialService();
        if (service == null) {
            return List.of();
        }
        final List<String> names = new ArrayList<>();
        for (final ServerPlacement placement : service.getPlacementsReferencing(areaId)) {
            names.add(placement.getName());
        }
        return List.copyOf(names);
    }

    /** Binds a placement to a registry entry; a null area id clears the binding. */
    public StockingAreaOutcome setStockingAreaRef(final UUID placementId,
                                                  final PlayerIdentifier player,
                                                  final boolean elevated,
                                                  final UUID areaId) {
        final MaterialService service = context.getMaterialService();
        if (service == null || !service.isEnabled()) {
            return StockingAreaOutcome.DISABLED;
        }
        final ServerPlacement placement = context.getSyncmaticManager().getPlacement(placementId);
        if (placement == null) {
            return StockingAreaOutcome.UNKNOWN_PLACEMENT;
        }
        if (!canManageStockingArea(placement, player, elevated, service)) {
            return StockingAreaOutcome.FORBIDDEN;
        }
        if (areaId != null && service.getStockingAreaRegistry().getById(areaId) == null) {
            return StockingAreaOutcome.UNKNOWN_AREA;
        }
        if (Objects.equals(placement.getStockingAreaRef(), areaId)) {
            return StockingAreaOutcome.UNCHANGED;
        }
        service.bindStockingArea(placement, areaId);
        return StockingAreaOutcome.UPDATED;
    }

    private static StockingAreaDefinition definition(final String dimension,
                                                     final int minX,
                                                     final int minY,
                                                     final int minZ,
                                                     final int maxX,
                                                     final int maxY,
                                                     final int maxZ) {
        return new StockingAreaDefinition(
                dimension,
                new BlockPos(minX, minY, minZ),
                new BlockPos(maxX, maxY, maxZ)
        );
    }

    private static WebDtos.StockingAreaRecord stockingAreaRecord(
            final MaterialService service, final StockingAreaRegistry.Entry entry) {
        final StockingAreaDefinition definition = entry.getDefinition();
        return new WebDtos.StockingAreaRecord(
                entry.getId().toString(),
                entry.getName(),
                definition.getDimensionId(),
                definition.getMin().getX(),
                definition.getMin().getY(),
                definition.getMin().getZ(),
                definition.getMax().getX(),
                definition.getMax().getY(),
                definition.getMax().getZ(),
                entry.getOwnerPlayerId() == null ? null : entry.getOwnerPlayerId().toString(),
                service.getPlacementsReferencing(entry.getId()).size()
        );
    }

    private static boolean canManageStockingAreaEntry(final PlayerIdentifier actor,
                                                      final StockingAreaRegistry.Entry entry,
                                                      final boolean elevated) {
        return PlacementAccessPolicy.canManageStockingAreaEntry(
                actor == null ? null : actor.uuid,
                entry.getOwnerPlayerId(),
                elevated
        );
    }

    private static boolean canManageStockingArea(final ServerPlacement placement,
                                                  final PlayerIdentifier player,
                                                  final boolean elevated,
                                                  final MaterialService service) {
        return PlacementAccessPolicy.canManageStockingArea(
                player == null ? null : player.uuid,
                placement.getOwner() == null ? null : placement.getOwner().uuid,
                elevated,
                service.isOwnerStockingAreaManagementEnabled()
        );
    }

    private static List<WebDtos.Player> players(
            final java.util.Collection<PlayerIdentifier> identifiers) {
        final List<WebDtos.Player> result = new ArrayList<>();
        for (final PlayerIdentifier identifier : identifiers) {
            result.add(player(identifier));
        }
        return List.copyOf(result);
    }

    private static WebDtos.Player player(final PlayerIdentifier identifier) {
        return identifier == null
                ? null
                : new WebDtos.Player(identifier.uuid.toString(), identifier.getName());
    }

    private static String nameOf(final PlayerIdentifier identifier) {
        return identifier == null ? "" : identifier.getName();
    }

    private static String translationKey(final MaterialKey key) {
        if (key == null) {
            return "";
        }
        //#if MC >= 260100
        //$$ final Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(key.itemId());
        //#else
        final Item item = net.minecraft.util.registry.Registry.ITEM.get(key.itemId());
        //#endif
        if (item == Items.AIR) {
            return "";
        }
        //#if MC >= 260100
        //$$ return item.getDescriptionId();
        //#else
        return item.getTranslationKey();
        //#endif
    }

    private static String fallbackName(final MaterialKey key) {
        if (key == null) {
            return "";
        }
        final String path = key.itemId().getPath();
        final StringBuilder result = new StringBuilder(path.length());
        boolean capitalize = true;
        for (int i = 0; i < path.length(); i++) {
            final char current = path.charAt(i);
            if (current == '_' || current == '-') {
                result.append(' ');
                capitalize = true;
            } else {
                result.append(capitalize ? Character.toUpperCase(current) : current);
                capitalize = false;
            }
        }
        return result.toString();
    }

    private static int progressPercent(final long supplied, final long required) {
        if (required <= 0L) {
            return 100;
        }
        if (supplied <= 0L) {
            return 0;
        }
        if (supplied >= required) {
            return 100;
        }
        return BigInteger.valueOf(supplied)
                .multiply(BigInteger.valueOf(100L))
                .divide(BigInteger.valueOf(required))
                .intValue();
    }

    private static long saturatedAdd(final long left, final long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + Math.max(0L, right);
    }

    private static final class MaterialTotals {
        private long required;
        private long supplied;
    }
}
