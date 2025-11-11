package cn.net.rms.syncmatica_r.litematica.gui;

import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.IGuiIcon;

import java.util.List;

public interface IButtonType {
    IGuiIcon getIcon();

    String getTranslatedKey();

    List<String> getHoverStrings();

    IButtonActionListener getButtonListener();

    boolean isActive();

}
