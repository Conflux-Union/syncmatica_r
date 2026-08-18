package cn.net.rms.syncmatica_r.client.hotkey;

import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.util.function.Supplier;

/**
 * Opens a Syncmatica screen on a key press.
 *
 * <p>Which screen it is comes from the supplier, so every hotkey that just opens
 * a GUI shares one copy of the version-specific screen calls rather than
 * repeating them per hotkey.
 */
public final class HotkeyCallbackOpenGui implements IHotkeyCallback {

    private final Supplier<Screen> screenFactory;

    public HotkeyCallbackOpenGui(final Supplier<Screen> screenFactory) {
        this.screenFactory = screenFactory;
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

        final Screen screen = screenFactory.get();
        if (screen == null) {
            return false;
        }
        //#if MC >= 260200
        //$$ client.gui.setScreen(screen);
        //#else
        client.setScreen(screen);
        //#endif
        return true;
    }
}
