package cn.net.rms.syncmatica_r.litematica.gui;

import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.material.MaterialExportService;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;

public class GuiMaterialExportOptions extends GuiBase {

    private final ServerPlacement placement;

    public GuiMaterialExportOptions(final ServerPlacement placement) {
        this.placement = placement;
        final String baseTitle = StringUtils.translate("syncmatica_r.gui.title.material_export");
        final String name = placement == null ? "" : placement.getName();
        title = name.isEmpty() ? baseTitle : baseTitle + ": " + name;
    }

    @Override
    public void initGui() {
        super.initGui();
        final int centerX = width / 2;
        final int centerY = height / 2;

        final String excelLabel = StringUtils.translate("syncmatica_r.gui.button.material_export_excel");
        final int excelWidth = getStringWidth(excelLabel) + 30;
        final int excelX = centerX - excelWidth / 2;
        final int excelY = centerY - 22;
        final ButtonGeneric excelButton = new ButtonGeneric(excelX, excelY, excelWidth, 20, excelLabel);
        addButton(excelButton, (button, mouseButton) -> {
            if (placement != null) {
                MaterialExportService.exportPlacementToXlsx(placement);
            }
            closeGui(true);
        });

        final String imageLabel = StringUtils.translate("syncmatica_r.gui.button.material_export_image");
        final int imageWidth = getStringWidth(imageLabel) + 30;
        final int imageX = centerX - imageWidth / 2;
        final int imageY = centerY + 4;
        final ButtonGeneric imageButton = new ButtonGeneric(imageX, imageY, imageWidth, 20, imageLabel);
        imageButton.setEnabled(false);
        addButton(imageButton, (button, mouseButton) -> {
        });

        final String backLabel = StringUtils.translate("syncmatica_r.gui.button.back");
        final int backWidth = getStringWidth(backLabel) + 20;
        final int backX = centerX - backWidth / 2;
        final int backY = height - 30;
        final ButtonGeneric backButton = new ButtonGeneric(backX, backY, backWidth, 20, backLabel);
        addButton(backButton, (button, mouseButton) -> closeGui(true));
    }
}

