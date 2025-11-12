package cn.net.rms.syncmatica_r;

import cn.net.rms.syncmatica_r.communication.CommunicationManager;
import cn.net.rms.syncmatica_r.mixin_actor.ActorClientPlayNetworkHandler;
import net.minecraft.util.Identifier;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Syncmatica {

    public static final String VERSION = "0.3.13";
    public static final String MOD_ID = "syncmatica_r";
    public static final String LEGACY_MOD_ID = "syncmatica";
    public static final Identifier CLIENT_CONTEXT = new Identifier(MOD_ID + ":client_context");
    public static final Identifier SERVER_CONTEXT = new Identifier(MOD_ID + ":server_context");
    public static final UUID syncmaticaId = UUID.fromString("4c1b738f-56fa-4011-8273-498c972424ea");
    private static final String SERVER_PATH = "." + File.separator + "syncmatics";
    private static final String CLIENT_PATH = "." + File.separator + "schematics" + File.separator + "sync";
    private static final Map<Identifier, Context> contexts = new HashMap<>();

    protected Syncmatica() {

    }

    public static Context initServer(final CommunicationManager comms, final IFileStorage fileStorage, final SyncmaticManager schematics, final boolean isIntegratedServer, final File worldPath) {
        final Context serverContext = new Context(
                fileStorage,
                comms,
                schematics,
                true,
                new File(SERVER_PATH),
                isIntegratedServer,
                worldPath
        );
        init(serverContext, SERVER_CONTEXT);
        return serverContext;
    }

    public static Context initClient(final CommunicationManager comms, final IFileStorage fileStorage, final SyncmaticManager schematics) {
        final Context clientContext = new Context(
                fileStorage,
                comms,
                schematics,
                new File(CLIENT_PATH)
        );
        init(clientContext, CLIENT_CONTEXT);
        return clientContext;
    }

    public static void restartClient() {
        final Context oldClient = getContext(CLIENT_CONTEXT);
        if (oldClient != null) {
            if (oldClient.isStarted()) {
                oldClient.shutdown();
            }

            contexts.remove(CLIENT_CONTEXT);
        }

        ActorClientPlayNetworkHandler.getInstance().startClient();
    }

    public static Context getContext(final Identifier id) {
        return contexts.get(id);
    }

    private static void init(final Context con, final Identifier contextId) {
        if (!contexts.containsKey(contextId)) {
            contexts.put(contextId, con);
        }
    }

    public static void shutdown() {
        for (final Context con : contexts.values()) {
            if (con.isStarted()) {
                con.shutdown();
            }
        }
        deinit();
    }

    private static void deinit() {
        contexts.clear();
    }

}
