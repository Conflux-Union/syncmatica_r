package cn.net.rms.syncmatica_r.client;

import cn.net.rms.syncmatica_r.Syncmatica;
import cn.net.rms.syncmatica_r.client.hud.MaterialHudOverlay;
import cn.net.rms.syncmatica_r.client.update.UpdateChecker;
import cn.net.rms.syncmatica_r.client.update.UpdateConfig;
import fi.dy.masa.malilib.event.InitializationHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
//#if MC < 12001
import net.minecraft.text.TranslatableText;
//#endif

public class SyncmaticaClient implements ClientModInitializer {
    private static final WorldEntryTracker WORLD_ENTRY_TRACKER = new WorldEntryTracker();

    @Override
    public void onInitializeClient() {
        BreakingChangeNotice.initialize();
        HudPreferences.load();
        MaterialListPreferences.load();
        MaterialHudOverlay.getInstance().setHudScale(HudPreferences.getHudScale());
        MaterialHudOverlay.getInstance().register();
        ClientTickEvents.END_CLIENT_TICK.register(SyncmaticaClient::handleClientTick);

        // Register initialization handler with malilib
        // This ensures hotkeys are registered at the correct time
        InitializationHandler.getInstance().registerInitializationHandler(new SyncmaticaInitHandler());
    }

    private static void handleClientTick(final MinecraftClient client) {
        MaterialHudOverlay.getInstance().tick();
        final cn.net.rms.syncmatica_r.Context clientContext = Syncmatica.getContext(Syncmatica.CLIENT_CONTEXT);
        if (clientContext != null && clientContext.getCommunicationManager() != null) {
            clientContext.getCommunicationManager().tick();
        }
        final boolean inGame = client != null && client.world != null && client.player != null;
        if (WORLD_ENTRY_TRACKER.update(inGame)) {
            BreakingChangeNotice.showIfNeeded(client);
        }
        if (!inGame) {
            return;
        }
        if (!UpdateConfig.isCheckUpdateEnabled()) {
            return;
        }
        if (UpdateChecker.getInstance() == null) {
            UpdateChecker.init(Syncmatica.getVersion(), UpdateConfig.isCheckPreReleaseEnabled());
        }
        final UpdateChecker checker = UpdateChecker.getInstance();
        if (checker == null) {
            return;
        }
        checker.checkForUpdatesAsync();
        if (checker.hasIntegrityWarning() && !checker.isIntegrityNotified()) {
            final Text warningTitle;
            final Text warningDescription;
            //#if MC >= 12001
            //$$ warningTitle = Text.translatable("syncmatica_r.update.toast.title");
            //$$ warningDescription = Text.translatable("syncmatica_r.update.toast.integrity_warning");
            //#else
            warningTitle = new TranslatableText("syncmatica_r.update.toast.title");
            warningDescription = new TranslatableText("syncmatica_r.update.toast.integrity_warning");
            //#endif
            //#if MC >= 12005
            //#if MC >= 260100
            //$$ client.player.sendSystemMessage(warningDescription);
            //#else
            //$$ client.player.sendMessage(warningDescription, false);
            //#endif
            //#elseif MC >= 12001
            SystemToast.add(client.getToastManager(), SystemToast.Type.TUTORIAL_HINT, warningTitle, warningDescription);
            //#else
            //$$ client.getToastManager().add(SystemToast.create(client, SystemToast.Type.TUTORIAL_HINT, warningTitle, warningDescription));
            //#endif
            checker.markIntegrityNotified();
        }
        if (!checker.hasUpdate() || checker.isNotified()) {
            return;
        }
        final String localVersion = Syncmatica.getVersion();
        final String remoteVersion = checker.getRemoteVersion();
        final Text title;
        final Text description;
        //#if MC >= 12001
        //$$ title = Text.translatable("syncmatica_r.update.toast.title");
        //$$ description = Text.translatable("syncmatica_r.update.toast.description", localVersion, remoteVersion);
        //#else
        title = new TranslatableText("syncmatica_r.update.toast.title");
        description = new TranslatableText("syncmatica_r.update.toast.description", localVersion, remoteVersion);
        //#endif
        //#if MC >= 12005
        //#if MC >= 260100
        //$$ client.player.sendSystemMessage(description);
        //#else
        //$$ client.player.sendMessage(description, false);
        //#endif
        //#elseif MC >= 12001
        //$$ SystemToast.add(client.getToastManager(), SystemToast.Type.TUTORIAL_HINT, title, description);
        //#else
        client.getToastManager().add(SystemToast.create(client, SystemToast.Type.TUTORIAL_HINT, title, description));
        //#endif
        checker.markNotified();
    }
}
