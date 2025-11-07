package ch.endte.syncmatica.client;

import ch.endte.syncmatica.client.hud.MaterialHudOverlay;
import net.fabricmc.api.ClientModInitializer;

public class SyncmaticaClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HudPreferences.load();
        MaterialHudOverlay.getInstance().setHudScale(HudPreferences.getHudScale());
        MaterialHudOverlay.getInstance().register();
    }
}
