package cn.net.rms.syncmatica_r.litematica.gui;

import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.build_management.BuildRegion;
import cn.net.rms.syncmatica_r.client.BuildVisibilityPreferences;
import cn.net.rms.syncmatica_r.litematica.ClaimedRegionVisibility;
import cn.net.rms.syncmatica_r.litematica.ScreenHelper;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;

/**
 * The sub-regions of one shared schematic, so players can divide the build
 * between themselves.
 */
public class GuiBuildRegions extends GuiListBase<BuildRegion, WidgetBuildRegionEntry, WidgetListBuildRegions> {

    private static final int TOP_BAR_HEIGHT = 24;

    private final ServerPlacement placement;
    private boolean emptyReported;

    public GuiBuildRegions(final ServerPlacement placement) {
        super(12, TOP_BAR_HEIGHT);
        this.placement = placement;
        title = StringUtils.translate("syncmatica_r.gui.title.build_management") + ": " + placement.getName();
        ScreenHelper.ifPresent(helper -> helper.setCurrentGui(this));
    }

    @Override
    public void initGui() {
        super.initGui();
        final String closeLabel = StringUtils.translate("syncmatica_r.gui.button.back");
        final int closeWidth = getStringWidth(closeLabel) + 20;
        final ButtonGeneric closeButton =
                new ButtonGeneric(width - closeWidth - 10, height - 26, closeWidth, 20, closeLabel);
        addButton(closeButton, (button, mouseButton) -> closeGui(true));

        addFollowClaimsButton();
        reportEmptyRegionList();
    }

    /**
     * The switch sits next to the rows it acts on, because this is the screen
     * where the claims it follows are made.
     */
    private void addFollowClaimsButton() {
        // Sized for both labels so the button does not resize as it is clicked.
        final int labelWidth = Math.max(
                getStringWidth(buildFollowClaimsLabel(true)),
                getStringWidth(buildFollowClaimsLabel(false)));
        final ButtonGeneric followButton = new ButtonGeneric(
                10, height - 26, labelWidth + 20, 20, buildFollowClaimsLabel());
        addButton(followButton, (button, mouseButton) -> {
            BuildVisibilityPreferences.setFollowClaimsEnabled(!BuildVisibilityPreferences.isFollowClaimsEnabled());
            followButton.setDisplayString(buildFollowClaimsLabel());
            ClaimedRegionVisibility.getInstance().refresh();
        });
    }

    private static String buildFollowClaimsLabel() {
        return buildFollowClaimsLabel(BuildVisibilityPreferences.isFollowClaimsEnabled());
    }

    private static String buildFollowClaimsLabel(final boolean enabled) {
        final String state = StringUtils.translate(enabled
                ? "syncmatica_r.gui.label.toggle_on"
                : "syncmatica_r.gui.label.toggle_off");
        return StringUtils.translate("syncmatica_r.gui.button.build.follow_claims", state);
    }

    /**
     * An empty list looks the same whether the schematic has no regions or the
     * server never sent any, so say once that nothing is claimable yet.
     */
    private void reportEmptyRegionList() {
        if (emptyReported || !placement.getBuildRegions().isEmpty()) {
            return;
        }
        emptyReported = true;
        addMessage(Message.MessageType.INFO, "syncmatica_r.gui.label.build.empty");
    }

    @Override
    protected WidgetListBuildRegions createListWidget(final int listX, final int listY) {
        return new WidgetListBuildRegions(listX, listY, getBrowserWidth(), getBrowserHeight(), placement);
    }

    @Override
    protected int getBrowserHeight() {
        return height - TOP_BAR_HEIGHT - 30;
    }

    @Override
    protected int getBrowserWidth() {
        return width - 20;
    }
}
