package cn.net.rms.syncmatica_r;

import cn.net.rms.syncmatica_r.communication.CommunicationManager;
import cn.net.rms.syncmatica_r.communication.FeatureSet;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifierProvider;
import cn.net.rms.syncmatica_r.service.*;
import cn.net.rms.syncmatica_r.service.IServiceConfiguration;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;

public class Context {

    private final IFileStorage files;
    private final CommunicationManager comMan;
    private final SyncmaticManager synMan;
    private final boolean server;
    private final boolean integratedServer;
    private final File litematicFolder;
    private final File worldFolder;
    private final QuotaService quota;
    private final DebugService debugService;
    private final PlayerIdentifierProvider playerIdentifierProvider;
    private final MaterialService materialService;
    private FeatureSet fs = null;
    private boolean isStarted = false;

    public Context(
            final IFileStorage fs,
            final CommunicationManager comMan,
            final SyncmaticManager synMan,
            final File litematicFolder
    ) {
        this(fs, comMan, synMan, false, litematicFolder, false, null);
    }

    public Context(
            final IFileStorage fs,
            final CommunicationManager comMan,
            final SyncmaticManager synMan,
            final boolean isServer,
            final File litematicFolder,
            final boolean integrated,
            final File worldFolder
    ) {
        files = fs;
        fs.setContext(this);
        this.comMan = comMan;
        comMan.setContext(this);
        this.synMan = synMan;
        synMan.setContext(this);
        server = isServer;
        if (isServer) {
            quota = new QuotaService();
            quota.setContext(this);
            materialService = new MaterialService();
            materialService.setContext(this);
        } else {
            quota = null;
            materialService = null;
        }
        playerIdentifierProvider = new PlayerIdentifierProvider(this);
        debugService = new DebugService();
        this.litematicFolder = litematicFolder;
        if (!litematicFolder.exists() && !litematicFolder.mkdirs()) {
            throw new IllegalStateException("Failed to create litematica folder " + litematicFolder.getAbsolutePath());
        }
        integratedServer = integrated;
        this.worldFolder = worldFolder;
        loadConfiguration();
    }

    public PlayerIdentifierProvider getPlayerIdentifierProvider() {
        return playerIdentifierProvider;
    }

    public IFileStorage getFileStorage() {
        return files;
    }

    public CommunicationManager getCommunicationManager() {
        return comMan;
    }

    public SyncmaticManager getSyncmaticManager() {
        return synMan;
    }

    public QuotaService getQuotaService() {
        return quota;
    }

    public MaterialService getMaterialService() {
        return materialService;
    }

    public DebugService getDebugService() {
        return debugService;
    }

    public FeatureSet getFeatureSet() {
        if (fs == null) {
            generateFeatureSet();
        }
        return fs;
    }

    public boolean isServer() {
        return server;
    }

    public boolean isIntegratedServer() {
        return integratedServer;
    }

    public boolean isStarted() {
        return isStarted;
    }

    public File getLitematicFolder() {
        return litematicFolder;
    }

    private void generateFeatureSet() {
        final ArrayList<Feature> features = new ArrayList<>(Arrays.asList(Feature.values()));
        if (isServer() && (materialService == null || !materialService.isEnabled())) {

            features.remove(Feature.MATERIAL_PROGRESS);
            features.remove(Feature.MATERIAL_CLAIMS);
        }
        fs = new FeatureSet(features);
    }

    public void startup() {
        startupServices();
        isStarted = true;
        synMan.startup();
    }

    public void shutdown() {
        shutdownServices();
        isStarted = false;
        synMan.shutdown();
    }

    public boolean checkPartnerVersion(final String version) {
        return !version.equals("0.0.1");
    }

    public File getConfigFolder() {
        if (isServer() && isIntegratedServer()) {
            return resolveConfigFolder(worldFolder);
        }
        return resolveConfigFolder(new File(".", "config"));
    }

    public File getConfigFile() {
        return new File(getConfigFolder(), "config.json");
    }

    public File getAndCreateConfigFile() throws IOException {
        final File configFolder = getConfigFolder();
        if (!configFolder.exists() && !configFolder.mkdirs()) {
            throw new IOException("Failed to create config folder " + configFolder.getAbsolutePath());
        }
        final File configFile = getConfigFile();
        if (!configFile.exists() && !configFile.createNewFile()) {
            throw new IOException("Failed to create config file " + configFile.getAbsolutePath());
        }
        return configFile;
    }

    private File resolveConfigFolder(final File base) {
        final File root = base == null ? new File(".") : base;
        final File preferred = new File(root, Syncmatica.MOD_ID);
        final File legacy = new File(root, Syncmatica.LEGACY_MOD_ID);
        if (!preferred.exists() && legacy.exists()) {
            return legacy;
        }
        return preferred;
    }

    public void loadConfiguration() {
        boolean attemptToLoad = false;
        JsonObject configuration;
        try {
            configuration = new Gson().fromJson(new BufferedReader(new FileReader(getConfigFile())), JsonObject.class);
            attemptToLoad = true;
        } catch (final Exception ignored) {
            configuration = new JsonObject();
        }
        boolean needsRewrite = false;
        if (isServer()) {
            needsRewrite = loadConfigurationForService(quota, configuration, attemptToLoad);
            if (materialService != null) {
                needsRewrite |= loadConfigurationForService(materialService, configuration, attemptToLoad);
            }
        }
        needsRewrite |= loadConfigurationForService(debugService, configuration, attemptToLoad);
        if (needsRewrite) {
            try (
                    final Writer writer = new BufferedWriter(new FileWriter(getAndCreateConfigFile()))
            ) {
                final Gson gson = new GsonBuilder().setPrettyPrinting().create();
                final String jsonString = gson.toJson(configuration);
                writer.write(jsonString);
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
    }

    private Boolean loadConfigurationForService(final IService service, final JsonObject configuration, final boolean attemptToLoad) {
        final String configKey = service.getConfigKey();
        JsonObject serviceJson = null;
        JsonConfiguration serviceConfiguration = null;
        boolean configured = false;
        boolean needsRewrite = false;

        if (attemptToLoad && configuration.has(configKey)) {
            try {
                serviceJson = configuration.getAsJsonObject(configKey);
                if (serviceJson != null) {
                    serviceConfiguration = new JsonConfiguration(serviceJson);
                    service.configure(serviceConfiguration);
                    configured = true;
                    if (serviceConfiguration.hadError()) {
                        needsRewrite = true;
                    }
                }
            } catch (final Exception e) {
                e.printStackTrace();
                needsRewrite = true;
            }
        }
        if (serviceJson == null) {
            serviceJson = new JsonObject();
            configuration.add(configKey, serviceJson);
            needsRewrite = true;
        }
        if (serviceConfiguration == null) {
            serviceConfiguration = new JsonConfiguration(serviceJson);
        }
        service.getDefaultConfiguration(serviceConfiguration);
        if (serviceConfiguration.didWriteDefaults()) {
            needsRewrite = true;
        }
        if (!configured) {
            service.configure(serviceConfiguration);
        }
        return needsRewrite;
    }

    private void startupServices() {
        if (quota != null) {
            quota.startup();
        }
        if (materialService != null) {
            materialService.startup();
        }
        debugService.startup();
    }

    private void shutdownServices() {
        if (quota != null) {
            quota.shutdown();
        }
        if (materialService != null) {
            materialService.shutdown();
        }
        debugService.shutdown();
    }

    public static class DuplicateContextAssignmentException extends RuntimeException {
        private static final long serialVersionUID = -5147544661160756303L;

        public DuplicateContextAssignmentException(final String reason) {
            super(reason);
        }
    }

    public static class ContextMismatchException extends RuntimeException {
        private static final long serialVersionUID = 2769376183212635479L;

        public ContextMismatchException(final String reason) {
            super(reason);
        }
    }
}
