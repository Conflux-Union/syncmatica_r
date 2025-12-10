package cn.net.rms.syncmatica_r.client.hotkey;

import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;

import java.util.Collections;
import java.util.List;

/**
 * Central registry for all Syncmatica hotkeys using malilib's ConfigHotkey.
 */
public final class SyncmaticaHotkeys {

    /**
     * Hotkey to open the Material Collections GUI.
     * Default is empty (unassigned).
     */
    public static final ConfigHotkey OPEN_MATERIAL_COLLECTIONS = new ConfigHotkey(
            "openMaterialCollections",
            "",
            KeybindSettings.DEFAULT,
            "syncmatica_r.hotkey.open_material_collections.comment"
    );

    private SyncmaticaHotkeys() {
        // Utility class, no instantiation
    }

    /**
     * Returns a list of all registered hotkeys.
     *
     * @return immutable list of all ConfigHotkey instances
     */
    public static List<ConfigHotkey> getHotkeys() {
        return Collections.singletonList(OPEN_MATERIAL_COLLECTIONS);
    }
}
