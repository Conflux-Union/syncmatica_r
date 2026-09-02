package cn.net.rms.syncmatica_r.client.hotkey;

import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Central registry for all Syncmatica hotkeys using malilib's ConfigHotkey.
 */
public final class SyncmaticaHotkeys {

    /**
     * Custom keybind settings with orderSensitive=false to allow any key order in combinations.
     * This fixes the issue where CTRL+F wouldn't work because key press order was unpredictable.
     */
    private static final KeybindSettings HOTKEY_SETTINGS = KeybindSettings.create(
            KeybindSettings.Context.INGAME,
            KeyAction.PRESS,
            false,  // allowExtraKeys
            false,  // orderSensitive - allow any key order in combinations
            false,  // exclusive
            true    // cancel
    );

    /**
     * Hotkey to open the Material Collections GUI.
     * Default is empty (unassigned).
     */
    public static final ConfigHotkey OPEN_MATERIAL_COLLECTIONS = new ConfigHotkey(
            "openMaterialCollections",
            "",
            HOTKEY_SETTINGS,
            "syncmatica_r.hotkey.open_material_collections.comment",
            "syncmatica_r.gui.label.hotkey.material_collections"
    ) {
        @Override
        public String getConfigGuiDisplayName() {
            return getPrettyName();
        }
    };

    /**
     * Hotkey to open the Build Management GUI.
     * Default is empty (unassigned).
     */
    public static final ConfigHotkey OPEN_BUILD_MANAGEMENT = new ConfigHotkey(
            "openBuildManagement",
            "",
            HOTKEY_SETTINGS,
            "syncmatica_r.hotkey.open_build_management.comment",
            "syncmatica_r.gui.label.hotkey.build_management"
    ) {
        @Override
        public String getConfigGuiDisplayName() {
            return getPrettyName();
        }
    };

    private static final List<ConfigHotkey> HOTKEYS = Collections.unmodifiableList(
            Arrays.asList(OPEN_MATERIAL_COLLECTIONS, OPEN_BUILD_MANAGEMENT)
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
        return HOTKEYS;
    }
}
