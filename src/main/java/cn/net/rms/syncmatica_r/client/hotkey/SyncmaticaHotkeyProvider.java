package cn.net.rms.syncmatica_r.client.hotkey;

import cn.net.rms.syncmatica_r.Syncmatica;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;

/**
 * Keybind provider that registers Syncmatica hotkeys with malilib's input system.
 */
public final class SyncmaticaHotkeyProvider implements IKeybindProvider {

    private static SyncmaticaHotkeyProvider instance;

    private SyncmaticaHotkeyProvider() {
        // Private constructor for singleton
    }

    /**
     * Initializes the hotkey provider and registers it with malilib's InputEventHandler.
     */
    public static void init() {
        if (instance == null) {
            instance = new SyncmaticaHotkeyProvider();
            InputEventHandler.getKeybindManager().registerKeybindProvider(instance);
        }
    }

    /**
     * Returns the singleton instance.
     *
     * @return the SyncmaticaHotkeyProvider instance, or null if not initialized
     */
    public static SyncmaticaHotkeyProvider getInstance() {
        return instance;
    }

    @Override
    public void addKeysToMap(final IKeybindManager manager) {
        for (final ConfigHotkey hotkey : SyncmaticaHotkeys.getHotkeys()) {
            manager.addKeybindToMap(hotkey.getKeybind());
        }
    }

    @Override
    public void addHotkeys(final IKeybindManager manager) {
        manager.addHotkeysForCategory(
                Syncmatica.MOD_ID,
                "syncmatica_r.hotkeys.category",
                SyncmaticaHotkeys.getHotkeys().stream()
                        .map(h -> (IHotkey) h)
                        .collect(java.util.stream.Collectors.toList())
        );
    }
}
