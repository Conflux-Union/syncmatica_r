package cn.net.rms.syncmatica_r.client;

import cn.net.rms.syncmatica_r.client.hud.MaterialHudOverlay;
import net.fabricmc.api.ClientModInitializer;

public class SyncmaticaClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HudPreferences.load();
        MaterialHudOverlay.getInstance().setHudScale(HudPreferences.getHudScale());
        MaterialHudOverlay.getInstance().register();
    }
}
