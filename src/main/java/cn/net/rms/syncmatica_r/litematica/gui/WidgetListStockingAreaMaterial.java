package cn.net.rms.syncmatica_r.litematica.gui;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.litematica.LitematicManager;
import cn.net.rms.syncmatica_r.material.StockingAreaDefinition;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WidgetListStockingAreaMaterial extends WidgetListBase<
        WidgetStockingAreaMaterialEntry.RowData,
        WidgetStockingAreaMaterialEntry> {

    // Cache rows so repeated draw passes don't rebuild the same data.
    private final List<WidgetStockingAreaMaterialEntry.RowData> cachedRows = new ArrayList<>();
    private final UUID focusPlacementId;

    public WidgetListStockingAreaMaterial(final int x, final int y, final int width, final int height,
                                          final ServerPlacement focusPlacement) {
        super(x, y, width, height, null);
        browserEntryHeight = 24;
        browserEntryWidth = width - 8;
        focusPlacementId = focusPlacement == null ? null : focusPlacement.getId();
    }

    @Override
    public void setSize(final int width, final int height) {
        super.setSize(width, height);
        browserEntryWidth = width - 8;
    }

    @Override
    protected Collection<WidgetStockingAreaMaterialEntry.RowData> getAllEntries() {
        cachedRows.clear();
        final Context context = LitematicManager.getInstance().getActiveContext();
        if (context == null || context.getSyncmaticManager() == null) {
            // Without a context we cannot surface placements, so show a descriptive row.
            cachedRows.add(WidgetStockingAreaMaterialEntry.RowData.empty(
                    StringUtils.translate("syncmatica_r.gui.label.stocking_area.unavailable")));
            return cachedRows;
        }
        final Collection<ServerPlacement> placements = context.getSyncmaticManager().getAll();
        if (placements.isEmpty()) {
            cachedRows.add(WidgetStockingAreaMaterialEntry.RowData.empty(
                    StringUtils.translate("syncmatica_r.gui.label.stocking_area.empty")));
            return cachedRows;
        }
        final List<StockingAreaGroup> groups = groupPlacements(placements);
        for (final StockingAreaGroup group : groups) {
            // Headers separate each stocking area bucket.
            cachedRows.add(WidgetStockingAreaMaterialEntry.RowData.header(group.toSummary()));
            group.placements.sort(Comparator.comparing(ServerPlacement::getName, String.CASE_INSENSITIVE_ORDER));
            for (final ServerPlacement placement : group.placements) {
                final boolean highlight = focusPlacementId != null && focusPlacementId.equals(placement.getId());
                cachedRows.add(WidgetStockingAreaMaterialEntry.RowData.placement(placement, highlight));
            }
        }
        return cachedRows;
    }

    @Override
    protected WidgetStockingAreaMaterialEntry createListEntryWidget(final int x, final int y, final int listIndex,
                                                                    final boolean isOdd,
                                                                    final WidgetStockingAreaMaterialEntry.RowData entry) {
        return new WidgetStockingAreaMaterialEntry(x, y, browserEntryWidth, browserEntryHeight, entry, listIndex);
    }

    private List<StockingAreaGroup> groupPlacements(final Collection<ServerPlacement> placements) {
        // Linked hash map preserves deterministic ordering once the sort keys line up.
        final Map<StockingAreaKey, StockingAreaGroup> grouped = new LinkedHashMap<>();
        for (final ServerPlacement placement : placements) {
            final StockingAreaKey key = StockingAreaKey.fromDefinition(placement.getStockingArea());
            grouped.computeIfAbsent(key, StockingAreaGroup::new).placements.add(placement);
        }
        final List<StockingAreaGroup> groups = new ArrayList<>(grouped.values());
        groups.sort((left, right) -> {
            final int orderCompare = Integer.compare(left.order(), right.order());
            if (orderCompare != 0) {
                return orderCompare;
            }
            return left.sortToken.compareTo(right.sortToken);
        });
        return groups;
    }

    private static final class StockingAreaGroup {
        // Each group fronts a header string and the placements sharing the same stocking area.
        private final StockingAreaKey key;
        private final List<ServerPlacement> placements = new ArrayList<>();
        private final String displayName;
        private final String sortToken;

        private StockingAreaGroup(final StockingAreaKey key) {
            this.key = key;
            this.displayName = key.describe();
            this.sortToken = key.sortToken();
        }

        private int order() {
            return key.isDefault ? 0 : 1;
        }

        private WidgetStockingAreaMaterialEntry.StockingAreaSummary toSummary() {
            return new WidgetStockingAreaMaterialEntry.StockingAreaSummary(displayName, placements);
        }
    }

    private static final class StockingAreaKey {
        // Keys normalize stocking area coordinates so distinct placements can be bucketed deterministically.
        private final boolean isDefault;
        private final String dimensionId;
        private final BlockPos min;
        private final BlockPos max;

        private StockingAreaKey(final StockingAreaDefinition area) {
            isDefault = area == null;
            if (area == null) {
                dimensionId = "";
                min = BlockPos.ORIGIN;
                max = BlockPos.ORIGIN;
            } else {
                dimensionId = area.getDimensionId();
                min = area.getMin();
                max = area.getMax();
            }
        }

        private static StockingAreaKey fromDefinition(final StockingAreaDefinition area) {
            return new StockingAreaKey(area);
        }

        private String describe() {
            if (isDefault) {
                return StringUtils.translate("syncmatica_r.gui.label.stocking_area.default");
            }
            return StringUtils.translate(
                    "syncmatica_r.gui.label.stocking_area.bounds",
                    dimensionId,
                    min.getX(), min.getY(), min.getZ(),
                    max.getX(), max.getY(), max.getZ()
            );
        }

        private String sortToken() {
            if (isDefault) {
                return "0";
            }
            return dimensionId + ":" + min.getX() + ":" + min.getY() + ":" + min.getZ()
                    + ":" + max.getX() + ":" + max.getY() + ":" + max.getZ();
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final StockingAreaKey other = (StockingAreaKey) obj;
            if (isDefault != other.isDefault) {
                return false;
            }
            if (isDefault) {
                return true;
            }
            return dimensionId.equals(other.dimensionId)
                    && min.equals(other.min)
                    && max.equals(other.max);
        }

        @Override
        public int hashCode() {
            if (isDefault) {
                return 31;
            }
            int result = dimensionId.hashCode();
            result = 31 * result + min.hashCode();
            result = 31 * result + max.hashCode();
            return result;
        }
    }
}
