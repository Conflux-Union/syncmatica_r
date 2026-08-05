package cn.net.rms.syncmatica_r.client.hotkey;

import cn.net.rms.syncmatica_r.litematica.gui.GuiStockingAreaMaterialOverview;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/**
 * Callback handler for the "Open Material Collections" hotkey.
 * Opens the GuiStockingAreaMaterialOverview when conditions are met.
 */
 public final class HotkeyCallbackOpenMaterialCollections implements IHotkeyCallback {

    public static final HotkeyCallbackOpenMaterialCollections INSTANCE = new HotkeyCallbackOpenMaterialCollections();

    private HotkeyCallbackOpenMaterialCollections() {
        // Singleton
    }

    @Override
    public boolean onKeyAction(final KeyAction action, final IKeybind key) {
        // Only trigger on key press
        if (action != KeyAction.PRESS) {
            return false;
        }

        // Check if no other screen is currently open
        final MinecraftClient client = MinecraftClient.getInstance();
        final Screen currentScreen;
        //#if MC >= 260200
        //$$ currentScreen = client.gui.screen();
        //#else
        currentScreen = client.currentScreen;
        //#endif
        if (currentScreen != null) {
            return false;
        }

        // Open the Material Collections GUI (null for overview of all placements)
        final GuiStockingAreaMaterialOverview overview = new GuiStockingAreaMaterialOverview(null);
        //#if MC >= 260200
        //$$ client.gui.setScreen(overview);
        //#else
        client.setScreen(overview);
        //#endif
        return true;
    }
}
