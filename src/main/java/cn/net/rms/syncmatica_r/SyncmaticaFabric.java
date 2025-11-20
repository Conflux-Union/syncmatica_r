package cn.net.rms.syncmatica_r;

import cn.net.rms.syncmatica_r.command.SyncmaticaCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
//#if MC >= 12005
//$$ import cn.net.rms.syncmatica_r.communication.SyncmaticaPayload;
//$$ import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
//#endif

public final class SyncmaticaFabric implements ModInitializer {

    @Override
    public void onInitialize() {
//#if MC >= 12005
//$$         registerPayloads();
//#endif
        registerCommands();
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> SyncmaticaCommand.register(dispatcher));
    }

//#if MC >= 12005
//$$     private static void registerPayloads() {
//$$         PayloadTypeRegistry.playS2C().register(SyncmaticaPayload.PACKET_ID, SyncmaticaPayload.CODEC);
//$$         PayloadTypeRegistry.playC2S().register(SyncmaticaPayload.PACKET_ID, SyncmaticaPayload.CODEC);
//$$     }
//#endif
}
