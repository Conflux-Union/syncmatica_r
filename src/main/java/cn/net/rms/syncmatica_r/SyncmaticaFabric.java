package cn.net.rms.syncmatica_r;

import cn.net.rms.syncmatica_r.command.SyncmaticaCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;

public final class SyncmaticaFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        registerCommands();
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> SyncmaticaCommand.register(dispatcher));
    }
}
