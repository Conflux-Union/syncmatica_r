package cn.net.rms.syncmatica_r.litematica.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ClientSettingsGuiContractTest {

    private final Path projectRoot = Path.of(System.getProperty("syncmatica.projectRoot"));

    @Test
    void clientSettingsUseMalilibConfigScreen() throws IOException {
        final String screen = read(
                "src/main/java/cn/net/rms/syncmatica_r/litematica/gui/GuiSyncmaticaSharedSettings.java");

        assertTrue(screen.contains("extends GuiConfigsBase"));
        assertTrue(screen.contains("ClientConfigs.General.OPTIONS"));
        assertTrue(screen.contains("SyncmaticaHotkeys.getHotkeys()"));
        assertFalse(screen.contains("WidgetSlider"));
        assertFalse(screen.contains("ButtonListenerHotkeyConfig"));
    }

    @Test
    void clientConfigIsRegisteredWithMalilib() throws IOException {
        final String initHandler = read(
                "src/main/java/cn/net/rms/syncmatica_r/client/SyncmaticaInitHandler.java");
        final String configs = read(
                "src/main/java/cn/net/rms/syncmatica_r/client/ClientConfigs.java");

        assertTrue(initHandler.contains("ConfigManager.getInstance().registerConfigHandler"));
        assertTrue(configs.contains("implements IConfigHandler"));
        assertTrue(configs.contains("ClientConfigMigrator.readLegacy"));
        assertTrue(configs.contains("ConfigUtils.readConfigBase"));
        assertTrue(configs.contains("ConfigUtils.writeConfigBase"));
        assertTrue(configs.contains("ClaimedRegionVisibility.getInstance().refresh()"));
        assertTrue(configs.contains("MaterialHudOverlay.getInstance().scheduleRefresh()"));
    }

    @Test
    void clientConfigLabelsAreLocalized() throws IOException {
        final String configs = read(
                "src/main/java/cn/net/rms/syncmatica_r/client/ClientConfigs.java");
        final String hotkeys = read(
                "src/main/java/cn/net/rms/syncmatica_r/client/hotkey/SyncmaticaHotkeys.java");
        final String english = read("src/main/resources/assets/syncmatica_r/lang/en_us.json");
        final String chinese = read("src/main/resources/assets/syncmatica_r/lang/zh_cn.json");

        assertTrue(configs.contains("getConfigGuiDisplayName()"));
        assertTrue(hotkeys.contains("getConfigGuiDisplayName()"));
        assertTrue(english.contains("\"syncmatica_r.gui.button.config.general\": \"General\""));
        assertTrue(english.contains("\"syncmatica_r.config.name.hud_scale\": \"HUD Scale\""));
        assertTrue(chinese.contains("\"syncmatica_r.gui.button.config.general\": \"通用\""));
        assertTrue(chinese.contains("\"syncmatica_r.config.name.hud_scale\": \"HUD 缩放\""));
    }

    @Test
    void configScreenUsesTheSyncmaticaRevolutionTitle() throws IOException {
        final String menuButtons = read(
                "src/main/java/cn/net/rms/syncmatica_r/litematica/gui/MainMenuButtonType.java");
        final String settingsScreen = read(
                "src/main/java/cn/net/rms/syncmatica_r/litematica/gui/GuiSyncmaticaSharedSettings.java");
        final String english = read("src/main/resources/assets/syncmatica_r/lang/en_us.json");
        final String chinese = read("src/main/resources/assets/syncmatica_r/lang/zh_cn.json");

        assertTrue(menuButtons.contains("syncmatica_r.gui.button.client_settings"));
        assertTrue(settingsScreen.contains("syncmatica_r.gui.title.configs"));
        assertTrue(settingsScreen.contains("useTitleHierarchy = false"));
        assertTrue(english.contains("\"syncmatica_r.gui.button.client_settings\": \"Client Settings\""));
        assertTrue(english.contains(
                "\"syncmatica_r.gui.title.configs\": \"Syncmatica Revolution Configs\""));
        assertTrue(chinese.contains("\"syncmatica_r.gui.button.client_settings\": \"客户端设置\""));
        assertTrue(chinese.contains(
                "\"syncmatica_r.gui.title.configs\": \"Syncmatica Revolution 配置\""));
    }

    @Test
    void mainMenuLabelsSyncmaticaButtonsWithTheRuntimeVersion() throws IOException {
        final String mainMenu = read(
                "src/main/java/cn/net/rms/syncmatica_r/litematica_mixin/MixinGuiMainMenu.java");
        final String english = read("src/main/resources/assets/syncmatica_r/lang/en_us.json");
        final String chinese = read("src/main/resources/assets/syncmatica_r/lang/zh_cn.json");

        assertTrue(mainMenu.contains("new WidgetLabel(x, 10, width, 10"));
        assertFalse(mainMenu.contains("label.setCentered(true)"));
        assertTrue(mainMenu.contains("Syncmatica.getVersion()"));
        assertTrue(mainMenu.contains("syncmatica_r.gui.label.version"));
        assertTrue(english.contains(
                "\"syncmatica_r.gui.label.version\": \"Syncmatica Revolution v%s\""));
        assertTrue(chinese.contains(
                "\"syncmatica_r.gui.label.version\": \"Syncmatica Revolution v%s\""));
    }

    private String read(final String relativePath) throws IOException {
        return Files.readString(projectRoot.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
