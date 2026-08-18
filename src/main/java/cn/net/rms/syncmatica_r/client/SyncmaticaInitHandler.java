package cn.net.rms.syncmatica_r.client;

import cn.net.rms.syncmatica_r.client.hotkey.HotkeyCallbackOpenGui;
import cn.net.rms.syncmatica_r.client.hotkey.SyncmaticaHotkeyConfig;
import cn.net.rms.syncmatica_r.client.hotkey.SyncmaticaHotkeyProvider;
import cn.net.rms.syncmatica_r.client.hotkey.SyncmaticaHotkeys;
import cn.net.rms.syncmatica_r.litematica.gui.GuiBuildManagement;
import cn.net.rms.syncmatica_r.litematica.gui.GuiStockingAreaMaterialOverview;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;

/**
 * Initialization handler for Syncmatica client-side features.
 * Registers hotkeys with malilib at the correct initialization time.
 */
public final class SyncmaticaInitHandler implements IInitializationHandler {

    @Override
    public void registerModHandlers() {
        // Load hotkey config first
        SyncmaticaHotkeyConfig.load();

        // Register keybind provider with malilib
        SyncmaticaHotkeyProvider.init();

        // Set up hotkey callbacks
        SyncmaticaHotkeys.OPEN_MATERIAL_COLLECTIONS.getKeybind()
                .setCallback(new HotkeyCallbackOpenGui(() -> new GuiStockingAreaMaterialOverview(null)));
        SyncmaticaHotkeys.OPEN_BUILD_MANAGEMENT.getKeybind()
                .setCallback(new HotkeyCallbackOpenGui(GuiBuildManagement::new));
    }
}
