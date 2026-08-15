package cn.net.rms.syncmatica_r.litematica.gui;

import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.build_management.BuildRegion;
import cn.net.rms.syncmatica_r.client.BuildVisibilityPreferences;
import cn.net.rms.syncmatica_r.client.BuildWarningPreferences;
import cn.net.rms.syncmatica_r.litematica.ClaimedRegionVisibility;
import cn.net.rms.syncmatica_r.litematica.ScreenHelper;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;

import java.util.function.BooleanSupplier;

/**
 * The sub-regions of one shared schematic, so players can divide the build
 * between themselves.
 */
public class GuiBuildRegions extends GuiListBase<BuildRegion, WidgetBuildRegionEntry, WidgetListBuildRegions> {

    /** Tall enough for the control bar above the list, as on the material screen. */
    private static final int TOP_BAR_HEIGHT = 34;

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

        addClientPreferenceBar();
        reportEmptyRegionList();
    }

    /**
     * Both switches are the player's own rather than the server's, and both act
     * on the claims listed below them, so they belong on this screen instead of
     * only in a config file.
     */
    private void addClientPreferenceBar() {
        int x = 10;
        x += addToggleButton(x, "syncmatica_r.gui.button.build.follow_claims",
                BuildVisibilityPreferences::isFollowClaimsEnabled,
                () -> {
                    BuildVisibilityPreferences.setFollowClaimsEnabled(
                            !BuildVisibilityPreferences.isFollowClaimsEnabled());
                    ClaimedRegionVisibility.getInstance().refresh();
                }) + 6;
        addToggleButton(x, "syncmatica_r.gui.button.build.warn_foreign",
                BuildWarningPreferences::isEnabled,
                () -> BuildWarningPreferences.setEnabled(!BuildWarningPreferences.isEnabled()));
    }

    /**
     * @return the width the button took, so the next one can start after it
     */
    private int addToggleButton(final int x, final String labelKey,
                                final BooleanSupplier state, final Runnable onToggle) {
        // Sized for both labels so the button does not resize as it is clicked.
        final int buttonWidth = Math.max(
                getStringWidth(toggleLabel(labelKey, true)),
                getStringWidth(toggleLabel(labelKey, false))) + 20;
        final ButtonGeneric button =
                new ButtonGeneric(x, 10, buttonWidth, 20, toggleLabel(labelKey, state.getAsBoolean()));
        addButton(button, (clicked, mouseButton) -> {
            onToggle.run();
            clicked.setDisplayString(toggleLabel(labelKey, state.getAsBoolean()));
        });
        return buttonWidth;
    }

    private static String toggleLabel(final String labelKey, final boolean enabled) {
        final String state = StringUtils.translate(enabled
                ? "syncmatica_r.gui.label.toggle_on"
                : "syncmatica_r.gui.label.toggle_off");
        return StringUtils.translate(labelKey, state);
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
