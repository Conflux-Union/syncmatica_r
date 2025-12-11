package cn.net.rms.syncmatica_r.litematica.gui;

import cn.net.rms.syncmatica_r.client.hotkey.SyncmaticaHotkeyConfig;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.hotkeys.KeybindMulti;
import fi.dy.masa.malilib.util.StringUtils;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Button listener for hotkey configuration.
 * Handles left-click to enter listening mode, right-click to clear keybind.
 */
public class ButtonListenerHotkeyConfig implements IButtonActionListener {

    private static final int LEFT_CLICK = 0;
    private static final int RIGHT_CLICK = 1;

    private final ConfigHotkey hotkey;
    private final ButtonGeneric button;
    private final String labelPrefix;
    private final IHotkeyListeningHost host;
    private boolean listening;
    private final Set<Integer> recordedKeyCodes = new LinkedHashSet<>();

    /**
     * Creates a new hotkey config button listener.
     *
     * @param hotkey the ConfigHotkey to configure
     * @param button the button to update display string
     * @param host   the GUI host that handles key listening
     */
    public ButtonListenerHotkeyConfig(final ConfigHotkey hotkey, final ButtonGeneric button,
                                      final IHotkeyListeningHost host, final String labelPrefix) {
        this.hotkey = hotkey;
        this.button = button;
        this.host = host;
        this.labelPrefix = labelPrefix;
        this.listening = false;
    }

    @Override
    public void actionPerformedWithButton(final ButtonBase buttonBase, final int mouseButton) {
        if (mouseButton == LEFT_CLICK) {
            enterListeningMode();
        } else if (mouseButton == RIGHT_CLICK) {
            clearKeybind();
        }
    }

    /**
     * Enters listening mode to capture the next key press.
     */
    private void enterListeningMode() {
        listening = true;
        recordedKeyCodes.clear();
        button.setDisplayString(formatDisplay(recordedKeyCodes, true));
        host.setHotkeyListener(this);
    }

    /**
     * Clears the current keybind assignment.
     */
    private void clearKeybind() {
        hotkey.getKeybind().setValueFromString("");
        updateButtonDisplay();
        SyncmaticaHotkeyConfig.save();
        // Notify malilib to update the key-to-hotkey mapping
        InputEventHandler.getKeybindManager().updateUsedKeys();
    }

    private void finalizeCurrentKeys() {
        if (!recordedKeyCodes.isEmpty()) {
            final String serialized = serializeKeys(recordedKeyCodes);
            if (!serialized.isEmpty()) {
                hotkey.getKeybind().setValueFromString(serialized);
                SyncmaticaHotkeyConfig.save();
                // Notify malilib to update the key-to-hotkey mapping
                InputEventHandler.getKeybindManager().updateUsedKeys();
            }
        }
        exitListeningMode();
    }

    /**
     * Called when a key is pressed while in listening mode.
     *
     * @param keyCode   the key code
     * @param scanCode  the scan code
     * @param modifiers the modifier keys
     * @return true if the key was handled
     */
    public boolean onKeyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!listening) {
            return false;
        }

        // Escape cancels listening mode
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancelListening();
            return true;
        }

        // Enter finalizes the combination
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            finalizeCurrentKeys();
            return true;
        }

        // Collect keys (modifiers + main key) to allow multi-key combinations
        for (final int code : buildKeyCodes(keyCode, modifiers)) {
            recordedKeyCodes.add(code);
        }
        button.setDisplayString(formatDisplay(recordedKeyCodes));

        // If the pressed key isn't just a modifier, commit immediately
        if (!isModifierKey(keyCode)) {
            finalizeCurrentKeys();
        }
        return true;
    }

    /**
     * Cancels listening mode without making changes.
     */
    private void cancelListening() {
        exitListeningMode();
    }

    /**
     * Exits listening mode and updates the button display.
     */
    private void exitListeningMode() {
        listening = false;
        updateButtonDisplay();
        host.setHotkeyListener(null);
    }

    /**
     * Updates the button display string to show current keybind.
     */
    private void updateButtonDisplay() {
        if (listening) {
            button.setDisplayString(formatDisplay(recordedKeyCodes, true));
            return;
        }
        final String keyValue = hotkey.getKeybind().getKeysDisplayString();
        button.setDisplayString(formatDisplayFromString(keyValue));
    }

    /**
     * Builds key codes (GLFW) for the pressed key and active modifiers.
     */
    private Set<Integer> buildKeyCodes(final int keyCode, final int modifiers) {
        final Set<Integer> keys = new LinkedHashSet<>(4);

        addModifier(keys, modifiers, GLFW.GLFW_MOD_CONTROL,
                GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL);
        addModifier(keys, modifiers, GLFW.GLFW_MOD_SHIFT,
                GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT);
        addModifier(keys, modifiers, GLFW.GLFW_MOD_ALT,
                GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT);
        addModifier(keys, modifiers, GLFW.GLFW_MOD_SUPER,
                GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER);

        keys.add(keyCode);

        return keys;
    }

    private static boolean isModifierKey(final int keyCode) {
        return keyCode == GLFW.GLFW_KEY_LEFT_CONTROL
                || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL
                || keyCode == GLFW.GLFW_KEY_LEFT_SHIFT
                || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT
                || keyCode == GLFW.GLFW_KEY_LEFT_ALT
                || keyCode == GLFW.GLFW_KEY_RIGHT_ALT
                || keyCode == GLFW.GLFW_KEY_LEFT_SUPER
                || keyCode == GLFW.GLFW_KEY_RIGHT_SUPER;
    }

    private static void addModifier(final Set<Integer> keys, final int modifiers,
                                    final int mask, final int leftKey, final int rightKey) {
        if ((modifiers & mask) == 0) {
            return;
        }
        final long window = net.minecraft.client.MinecraftClient.getInstance().getWindow().getHandle();
        boolean added = false;
        if (GLFW.glfwGetKey(window, leftKey) == GLFW.GLFW_PRESS) {
            keys.add(leftKey);
            added = true;
        }
        if (GLFW.glfwGetKey(window, rightKey) == GLFW.GLFW_PRESS) {
            keys.add(rightKey);
            added = true;
        }
        if (!added) {
            // Fallback: record left side to keep the bind usable even if state query fails
            keys.add(leftKey);
        }
    }

    private String formatDisplay(final Set<Integer> keys) {
        return formatDisplay(keys, false);
    }

    private String formatDisplay(final Set<Integer> keys, final boolean listeningMode) {
        if (keys.isEmpty()) {
            return labelPrefix + StringUtils.translate("syncmatica_r.gui.label.hotkey.press_key");
        }
        final StringBuilder sb = new StringBuilder();
        for (final int code : keys) {
            final String name = KeybindMulti.getStorageStringForKeyCode(code);
            if (name == null || name.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("+");
            }
            sb.append(name);
        }
        return labelPrefix + (sb.length() == 0
                ? StringUtils.translate("syncmatica_r.gui.label.hotkey.press_key")
                : sb.toString());
    }

    private static String serializeKeys(final Set<Integer> keys) {
        final StringBuilder sb = new StringBuilder();
        for (final int code : keys) {
            final String name = KeybindMulti.getStorageStringForKeyCode(code);
            if (name == null || name.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(name);
        }
        return sb.toString();
    }

    private String formatDisplayFromString(final String keyValue) {
        if (keyValue == null || keyValue.isEmpty()) {
            return labelPrefix + StringUtils.translate("syncmatica_r.gui.label.hotkey.none");
        }
        return labelPrefix + keyValue.replace(",", "+");
    }

    /**
     * Returns whether this listener is currently in listening mode.
     *
     * @return true if listening for key input
     */
    public boolean isListening() {
        return listening;
    }

    /**
     * Interface for GUI hosts that support hotkey listening.
     */
    public interface IHotkeyListeningHost {
        /**
         * Sets the active hotkey listener.
         *
         * @param listener the listener, or null to clear
         */
        void setHotkeyListener(ButtonListenerHotkeyConfig listener);
    }
}
