package cn.net.rms.syncmatica_r.litematica.gui;

import cn.net.rms.syncmatica_r.client.HudPreferences;
import cn.net.rms.syncmatica_r.client.hud.MaterialHudOverlay;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.ISliderCallback;
import fi.dy.masa.malilib.gui.widgets.WidgetSlider;
import fi.dy.masa.malilib.util.StringUtils;

public class GuiSyncmaticaSharedSettings extends GuiBase {

    public GuiSyncmaticaSharedSettings() {
        title = StringUtils.translate("syncmatica_r.gui.title.shared_settings");
    }

    @Override
    public void initGui() {
        super.initGui();

        final int sliderWidth = 220;
        final int sliderHeight = 20;
        final int sliderX = width / 2 - sliderWidth / 2;
        final int sliderY = height / 2 - sliderHeight / 2;
        final int toggleY = sliderY - sliderHeight - 10;

        final ButtonGeneric hudToggle = new ButtonGeneric(sliderX, toggleY, sliderWidth, sliderHeight,
                buildHudToggleLabel());
        addButton(hudToggle, (button, mouseButton) -> {
            HudPreferences.setHudEnabled(!HudPreferences.isHudEnabled());
            hudToggle.setDisplayString(buildHudToggleLabel());
            MaterialHudOverlay.getInstance().scheduleRefresh();
        });

        addWidget(new WidgetSlider(sliderX, sliderY, sliderWidth, sliderHeight, new HudScaleSliderCallback()));

        final String backLabel = StringUtils.translate("syncmatica_r.gui.button.back");
        final int backWidth = getStringWidth(backLabel) + 20;
        final int backX = width / 2 - backWidth / 2;
        final int backY = height - 30;
        final ButtonGeneric backButton = new ButtonGeneric(backX, backY, backWidth, 20, backLabel);
        addButton(backButton, (IButtonActionListener) (button, mouseButton) -> closeGui(true));
    }

    private String buildHudToggleLabel() {
        final String stateKey = HudPreferences.isHudEnabled()
                ? "syncmatica_r.gui.label.toggle_on"
                : "syncmatica_r.gui.label.toggle_off";
        final String stateValue = StringUtils.translate(stateKey);
        return StringUtils.translate("syncmatica_r.gui.button.hud_toggle", stateValue);
    }

    private static final class HudScaleSliderCallback implements ISliderCallback {

        @Override
        public int getMaxSteps() {
            return 100;
        }

        @Override
        public double getValueRelative() {
            return HudPreferences.getRelativeScale();
        }

        @Override
        public void setValueRelative(final double value) {
            HudPreferences.setRelativeScale(value);
            final double scale = HudPreferences.getHudScale();
            final MaterialHudOverlay overlay = MaterialHudOverlay.getInstance();
            overlay.setHudScale(scale);
            overlay.scheduleRefresh();
        }

        @Override
        public String getFormattedDisplayValue() {
            return StringUtils.translate("syncmatica_r.gui.label.hud_scale_value",
                    (int) Math.round(HudPreferences.getHudScale() * 100d));
        }
    }
}
