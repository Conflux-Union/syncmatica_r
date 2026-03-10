package cn.net.rms.syncmatica_r.litematica.gui;

import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.ServerPosition;
import cn.net.rms.syncmatica_r.litematica.LitematicManager;
import cn.net.rms.syncmatica_r.litematica.ScreenHelper;
import com.google.common.collect.ImmutableList;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetSearchBar;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.util.math.MatrixStack;
//#if MC >= 12111
//$$ import fi.dy.masa.malilib.render.GuiContext;
//#endif
//#if MC >= 12001
//$$ import net.minecraft.client.gui.DrawContext;
//#endif
import net.minecraft.util.math.BlockPos;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class WidgetListSyncmaticaServerPlacement extends WidgetListBase<ServerPlacement, WidgetSyncmaticaServerPlacementEntry> {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final int infoWidth;
    private final int infoHeight;
    private final GuiSyncmaticaServerPlacementList parent;

    public WidgetListSyncmaticaServerPlacement(final int x, final int y, final int width, final int height, final GuiSyncmaticaServerPlacementList parent,
                                               final ISelectionListener<ServerPlacement> selectionListener) {
        super(x, y, width, height, selectionListener);
        browserEntryHeight = 22;
        infoWidth = 170;
        infoHeight = 290;
        widgetSearchBar = new WidgetSearchBar(x + 2, y + 4, width - 14, 14, 0, Icons.FILE_ICON_SEARCH, LeftRight.LEFT);
        browserEntriesOffsetY = widgetSearchBar.getHeight() + 3;
        this.parent = parent;
        setSize(width, height);
        ScreenHelper.ifPresent(s -> s.setCurrentGui(parent));
    }

    @Override
    public void setSize(final int width, final int height) {
        super.setSize(width, height);

        browserWidth = getBrowserWidthForTotalWidth(width);
        browserEntryWidth = browserWidth - 14;
    }

    protected int getBrowserWidthForTotalWidth(final int width) {
        return width - 6 - infoWidth;
    }

//#if MC >= 12111
//$$     @Override
//$$     public void drawContents(final GuiContext guiContext, final int mouseX, final int mouseY, final float partialTicks) {
//$$         RenderUtils.drawOutlinedBox(guiContext, posX, posY, browserWidth, browserHeight, 0xB0000000, GuiBase.COLOR_HORIZONTAL_BAR);
//$$         super.drawContents(guiContext, mouseX, mouseY, partialTicks);
//$$         drawPlacementInfo(getLastSelectedEntry(), guiContext);
//$$     }
//$$
//$$     private void drawPlacementInfo(final ServerPlacement placement, final GuiContext guiContext) {
//#elseif MC >= 12108
//$$     @Override
//$$     public void drawContents(final DrawContext drawContext, final int mouseX, final int mouseY, final float partialTicks) {
//$$         RenderUtils.drawOutlinedBox(drawContext, posX, posY, browserWidth, browserHeight, 0xB0000000, GuiBase.COLOR_HORIZONTAL_BAR);
//$$         super.drawContents(drawContext, mouseX, mouseY, partialTicks);
//$$         drawPlacementInfo(getLastSelectedEntry(), drawContext);
//$$     }
//$$
//$$     private void drawPlacementInfo(final ServerPlacement placement, final DrawContext drawContext) {
//#elseif MC >= 12001
//$$     @Override
//$$     public void drawContents(final DrawContext drawContext, final int mouseX, final int mouseY, final float partialTicks) {
//$$         RenderUtils.drawOutlinedBox(posX, posY, browserWidth, browserHeight, 0xB0000000, GuiBase.COLOR_HORIZONTAL_BAR);
//$$         super.drawContents(drawContext, mouseX, mouseY, partialTicks);
//$$         drawPlacementInfo(getLastSelectedEntry(), drawContext);
//$$     }

//$$     private void drawPlacementInfo(final ServerPlacement placement, final DrawContext drawContext) {
//#else
    @Override
    public void drawContents(final MatrixStack matrixStack, final int mouseX, final int mouseY, final float partialTicks) {
        RenderUtils.drawOutlinedBox(posX, posY, browserWidth, browserHeight, 0xB0000000, GuiBase.COLOR_HORIZONTAL_BAR);
        super.drawContents(matrixStack, mouseX, mouseY, partialTicks);
        drawPlacementInfo(getLastSelectedEntry(), matrixStack);
    }

    private void drawPlacementInfo(final ServerPlacement placement, final MatrixStack matrixStack) {
//#endif
        int x = posX + totalWidth - infoWidth;
        int y = posY;
        final int height = Math.min(infoHeight, parent.getMaxInfoHeight());

        //#if MC >= 12111
        //$$ RenderUtils.drawOutlinedBox(guiContext, x, y, infoWidth, height, 0xA0000000, GuiBase.COLOR_HORIZONTAL_BAR);
        //#elseif MC >= 12108
        //$$ RenderUtils.drawOutlinedBox(drawContext, x, y, infoWidth, height, 0xA0000000, GuiBase.COLOR_HORIZONTAL_BAR);
        //#else
        RenderUtils.drawOutlinedBox(x, y, infoWidth, height, 0xA0000000, GuiBase.COLOR_HORIZONTAL_BAR);
        //#endif

        if (placement == null) {
            return;
        }

        //#if MC < 12108
        RenderUtils.color(1f, 1f, 1f, 1f);
        //#endif

        x += 3;
        y += 3;
        final int textColor = 0xC0C0C0C0;
        final int valueColor = 0xFFFFFFFF;

        String str = StringUtils.translate("syncmatica_r.gui.label.placement_info.file_name");
//#if MC >= 12111
//$$         drawString(guiContext, str, x, y, textColor);
//#elseif MC >= 12001
//$$         drawString(drawContext, str, x, y, textColor);
//#else
        drawString(matrixStack, str, x, y, textColor);
//#endif
        y += 12;
//#if MC >= 12111
//$$         drawString(guiContext, placement.getName(), x + 4, y, valueColor);
//#elseif MC >= 12001
//$$         drawString(drawContext, placement.getName(), x + 4, y, valueColor);
//#else
        drawString(matrixStack, placement.getName(), x + 4, y, valueColor);
//#endif
        y += 12;

        str = StringUtils.translate("syncmatica_r.gui.label.placement_info.dimension_id");
//#if MC >= 12111
//$$         drawString(guiContext, str, x, y, textColor);
//#elseif MC >= 12001
//$$         drawString(drawContext, str, x, y, textColor);
//#else
        drawString(matrixStack, str, x, y, textColor);
//#endif
        y += 12;
//#if MC >= 12111
//$$         drawString(guiContext, placement.getDimension(), x + 4, y, valueColor);
//#elseif MC >= 12001
//$$         drawString(drawContext, placement.getDimension(), x + 4, y, valueColor);
//#else
        drawString(matrixStack, placement.getDimension(), x + 4, y, valueColor);
//#endif
        y += 12;

        str = StringUtils.translate("syncmatica_r.gui.label.placement_info.position");
//#if MC >= 12111
//$$         drawString(guiContext, str, x, y, textColor);
//#elseif MC >= 12001
//$$         drawString(drawContext, str, x, y, textColor);
//#else
        drawString(matrixStack, str, x, y, textColor);
//#endif
        y += 12;
        final BlockPos origin = placement.getPosition();
        final String tmp = String.format("%d %d %d", origin.getX(), origin.getY(), origin.getZ());
//#if MC >= 12111
//$$         drawString(guiContext, tmp, x + 4, y, valueColor);
//#elseif MC >= 12001
//$$         drawString(drawContext, tmp, x + 4, y, valueColor);
//#else
        drawString(matrixStack, tmp, x + 4, y, valueColor);
//#endif
        y += 12;

        str = StringUtils.translate("syncmatica_r.gui.label.placement_info.owner");
//#if MC >= 12111
//$$         drawString(guiContext, str, x, y, textColor);
//#elseif MC >= 12001
//$$         drawString(drawContext, str, x, y, textColor);
//#else
        drawString(matrixStack, str, x, y, textColor);
//#endif
        y += 12;
//#if MC >= 12111
//$$         drawString(guiContext, placement.getOwner().getName(), x + 4, y, valueColor);
//#elseif MC >= 12001
//$$         drawString(drawContext, placement.getOwner().getName(), x + 4, y, valueColor);
//#else
        drawString(matrixStack, placement.getOwner().getName(), x + 4, y, valueColor);
//#endif
        y += 12;

        str = StringUtils.translate("syncmatica_r.gui.label.placement_info.last_modified");
//#if MC >= 12111
//$$         drawString(guiContext, str, x, y, textColor);
//#elseif MC >= 12001
//$$         drawString(drawContext, str, x, y, textColor);
//#else
        drawString(matrixStack, str, x, y, textColor);
//#endif
        y += 12;
//#if MC >= 12111
//$$         drawString(guiContext, placement.getLastModifiedBy().getName(), x + 4, y, valueColor);
//#elseif MC >= 12001
//$$         drawString(drawContext, placement.getLastModifiedBy().getName(), x + 4, y, valueColor);
//#else
        drawString(matrixStack, placement.getLastModifiedBy().getName(), x + 4, y, valueColor);
//#endif

        y += 12;
        str = StringUtils.translate("syncmatica_r.gui.label.placement_info.uploaded_at");
//#if MC >= 12111
//$$         drawString(guiContext, str, x, y, textColor);
//#elseif MC >= 12001
//$$         drawString(drawContext, str, x, y, textColor);
//#else
        drawString(matrixStack, str, x, y, textColor);
//#endif
        y += 12;
        final String createdAt = formatTimestamp(placement.getCreatedAtMillis());
//#if MC >= 12111
//$$         drawString(guiContext, createdAt, x + 4, y, valueColor);
//#elseif MC >= 12001
//$$         drawString(drawContext, createdAt, x + 4, y, valueColor);
//#else
        drawString(matrixStack, createdAt, x + 4, y, valueColor);
//#endif

        y += 12;
        str = StringUtils.translate("syncmatica_r.gui.label.placement_info.last_edited_at");
//#if MC >= 12111
//$$         drawString(guiContext, str, x, y, textColor);
//#elseif MC >= 12001
//$$         drawString(drawContext, str, x, y, textColor);
//#else
        drawString(matrixStack, str, x, y, textColor);
//#endif
        y += 12;
        final String lastEdited = formatTimestamp(placement.getLastModifiedAtMillis());
//#if MC >= 12111
//$$         drawString(guiContext, lastEdited, x + 4, y, valueColor);
//#elseif MC >= 12001
//$$         drawString(drawContext, lastEdited, x + 4, y, valueColor);
//#else
        drawString(matrixStack, lastEdited, x + 4, y, valueColor);
//#endif
    }

    @Override
    protected List<String> getEntryStringsForFilter(final ServerPlacement entry) {
        final String metaName = entry.getName().toLowerCase();
        return ImmutableList.of(metaName);
    }

    @Override
    protected WidgetSyncmaticaServerPlacementEntry createListEntryWidget(final int x, final int y, final int listIndex, final boolean isOdd, final ServerPlacement entry) {
        return new WidgetSyncmaticaServerPlacementEntry(x, y, browserEntryWidth, getBrowserEntryHeightFor(entry), entry, listIndex);
    }

    @Override
    protected Collection<ServerPlacement> getAllEntries() {
        final cn.net.rms.syncmatica_r.Context activeContext = LitematicManager.getInstance().getActiveContext();
        if (activeContext == null) {
            return java.util.Collections.emptyList();
        }
        final ServerPosition playerPosition = LitematicManager.getInstance().getPlayerPosition();
        final Collection<ServerPlacement> serverPlacements = activeContext.getSyncmaticManager().getAll();
        return serverPlacements.stream().sorted(new PlayerDistanceComparator(playerPosition)).collect(Collectors.toList());
    }

    private String formatTimestamp(final long timestamp) {
        if (timestamp <= 0L) {
            return StringUtils.translate("syncmatica_r.gui.label.placement_info.timestamp_unknown");
        }
        return TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(timestamp));
    }

    public static class PlayerDistanceComparator implements Comparator<ServerPlacement> {

        private final String playerDimension;
        private final BlockPos playerPosition;
        private final BlockPos playerPositionOverworld;
        private final BlockPos playerPositionNether;

        PlayerDistanceComparator(final ServerPosition playerPosition) {
            this.playerPosition = playerPosition.getBlockPosition();
            playerDimension = playerPosition.getDimensionId();

            if (playerPosition.getDimensionId().equals(ServerPosition.OVERWORLD_DIMENSION_ID)) {
                playerPositionNether = new BlockPos(
                        this.playerPosition.getX() << 3,
                        this.playerPosition.getY() << 3,
                        this.playerPosition.getZ() << 3
                );
            } else {
                playerPositionNether = this.playerPosition;
            }
            if (playerPosition.getDimensionId().equals(ServerPosition.NETHER_DIMENSION_ID)) {
                playerPositionOverworld = new BlockPos(
                        this.playerPosition.getX() >> 3,
                        this.playerPosition.getY() >> 3,
                        this.playerPosition.getZ() >> 3
                );
            } else {
                playerPositionOverworld = this.playerPosition;
            }
        }

        @Override
        public int compare(final ServerPlacement serverPlacement1, final ServerPlacement serverPlacement2) {
            final String dimension1 = serverPlacement1.getDimension();
            final String dimension2 = serverPlacement2.getDimension();

            final boolean equalDimension1 = compareDimensions(dimension1, playerDimension);
            final boolean equalDimension2 = compareDimensions(dimension2, playerDimension);

            if (equalDimension1 ^ equalDimension2) {
                return equalDimension1 ? -1 : 1;
            }

            final boolean linkedDimensions1 = areInLinkedDimensions(dimension1, playerDimension);
            final boolean linkedDimensions2 = areInLinkedDimensions(dimension2, playerDimension);

            if (linkedDimensions1 ^ linkedDimensions2) {
                return linkedDimensions1 ? -1 : 1;
            }

            return Double.compare(
                    getDimensionDistanceSquared(serverPlacement1.getOrigin()),
                    getDimensionDistanceSquared(serverPlacement2.getOrigin())
            );
        }

        private double getDimensionDistanceSquared(final ServerPosition position) {
            if (position.getDimensionId().equals(ServerPosition.OVERWORLD_DIMENSION_ID)) {
                return distanceSquared(
                        position.getBlockPosition(),
                        playerPositionOverworld.getX(),
                        playerPositionOverworld.getY(),
                        playerPositionOverworld.getZ()
                );
            }
            if (position.getDimensionId().equals(ServerPosition.NETHER_DIMENSION_ID)) {
                return distanceSquared(
                        position.getBlockPosition(),
                        playerPositionNether.getX(),
                        playerPositionNether.getY(),
                        playerPositionNether.getZ()
                );
            }
            return distanceSquared(
                    position.getBlockPosition(),
                    playerPosition.getX(),
                    playerPosition.getY(),
                    playerPosition.getZ()
            );
        }

        private boolean compareDimensions(final String dimensionId1, final String dimensionId2) {
            return dimensionId1.equals(dimensionId2);
        }

        private boolean areInLinkedDimensions(final String dimension1, final String dimension2) {
            return (isOverworld(dimension1) && isNether(dimension2))
                    || (isNether(dimension1) && isOverworld(dimension2));
        }

        private boolean isNether(final String dimensionId) {
            return dimensionId.equals(ServerPosition.NETHER_DIMENSION_ID);
        }

        private boolean isOverworld(final String dimensionId) {
            return dimensionId.equals(ServerPosition.OVERWORLD_DIMENSION_ID);
        }

        private double distanceSquared(final BlockPos pos, final double x, final double y, final double z) {
//#if MC >= 12001
//$$             return pos.getSquaredDistance(x, y, z);
//#else
            return pos.getSquaredDistance(x, y, z, false);
//#endif
        }
    }
}
