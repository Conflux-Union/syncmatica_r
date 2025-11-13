package cn.net.rms.syncmatica_r.client;

import cn.net.rms.syncmatica_r.Syncmatica;
import cn.net.rms.syncmatica_r.client.hud.MaterialHudOverlay;
import cn.net.rms.syncmatica_r.client.update.UpdateChecker;
import cn.net.rms.syncmatica_r.client.update.UpdateConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;

public class SyncmaticaClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HudPreferences.load();
        MaterialHudOverlay.getInstance().setHudScale(HudPreferences.getHudScale());
        MaterialHudOverlay.getInstance().register();
        ClientTickEvents.END_CLIENT_TICK.register(SyncmaticaClient::handleClientTick);
    }

    private static void handleClientTick(final MinecraftClient client) {
        if (client == null || client.world == null || client.player == null) {
            return;
        }
        if (!UpdateConfig.isCheckUpdateEnabled()) {
            return;
        }
        if (UpdateChecker.getInstance() == null) {
            UpdateChecker.init(Syncmatica.VERSION, UpdateConfig.isCheckPreReleaseEnabled());
        }
        final UpdateChecker checker = UpdateChecker.getInstance();
        if (checker == null) {
            return;
        }
        checker.checkForUpdatesAsync();
        if (!checker.hasUpdate() || checker.isNotified()) {
            return;
        }
        final String localVersion = Syncmatica.VERSION;
        final String remoteVersion = checker.getRemoteVersion();
        final Text title = Text.translatable("syncmatica_r.update.toast.title");
        final Text description = Text.translatable("syncmatica_r.update.toast.description", localVersion, remoteVersion);
        SystemToast.add(client.getToastManager(), SystemToast.Type.TUTORIAL_HINT, title, description);
        checker.markNotified();
    }
}
