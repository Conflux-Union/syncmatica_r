package cn.net.rms.syncmatica_r;

import cn.net.rms.syncmatica_r.communication.CommunicationManager;
import cn.net.rms.syncmatica_r.communication.FeatureSet;
import cn.net.rms.syncmatica_r.communication.ProtocolLimits;
import cn.net.rms.syncmatica_r.communication.ServerCommunicationManager;
import cn.net.rms.syncmatica_r.extended_core.PlayerIdentifierProvider;
import cn.net.rms.syncmatica_r.service.*;
import cn.net.rms.syncmatica_r.service.IServiceConfiguration;
import cn.net.rms.syncmatica_r.util.VersionComparator;
import cn.net.rms.syncmatica_r.web.WebService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import net.minecraft.server.MinecraftServer;

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
    private final BuildService buildService;
    private final WebService webService;
    private MinecraftServer minecraftServer;
    private ConfigRegistry configRegistry;
    private ConfigStore configStore;
    private JsonObject loadedConfiguration;
    private FeatureSet fs = null;
    private boolean isStarted = false;
    private boolean quotaStarted;
    private boolean materialStarted;
    private boolean buildStarted;
    private boolean debugStarted;
    private boolean managerStarted;
    private boolean webStarted;

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
            buildService = new BuildService();
            buildService.setContext(this);
            webService = new WebService();
            webService.setContext(this);
        } else {
            quota = null;
            materialService = null;
            buildService = null;
            webService = null;
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

    public BuildService getBuildService() {
        return buildService;
    }

    public WebService getWebService() {
        return webService;
    }

    public MinecraftServer getMinecraftServer() {
        return minecraftServer;
    }

    public void attachMinecraftServer(final MinecraftServer minecraftServer) {
        if (!server) {
            throw new IllegalStateException("A client context cannot own a Minecraft server");
        }
        if (this.minecraftServer != null && this.minecraftServer != minecraftServer) {
            throw new IllegalStateException("Minecraft server is already attached");
        }
        this.minecraftServer = minecraftServer;
    }

    public long getMaxTransferBytes() {
        return materialService == null
                ? ProtocolLimits.DEFAULT_MAX_SCHEMATIC_BYTES
                : materialService.getMaxSchematicBytes();
    }

    public DebugService getDebugService() {
        return debugService;
    }

    public ConfigRegistry getConfigRegistry() {
        return configRegistry;
    }

    public ConfigStore getConfigStore() {
        return configStore;
    }

    public JsonObject getLoadedConfiguration() {
        return loadedConfiguration == null
                ? null
                : new Gson().fromJson(loadedConfiguration.toString(), JsonObject.class);
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
            features.remove(Feature.STOCKING_AREA_SETUP);
        }
        // Build management reads the schematic itself, so it stands or falls on
        // its own switch rather than on whether materials are tracked.
        if (isServer() && (buildService == null || !buildService.isEnabled())) {
            features.remove(Feature.BUILD_MANAGEMENT);
        }
        fs = new FeatureSet(features);
    }

    public void serverFeaturesChanged() {
        if (!isServer()) {
            return;
        }
        fs = null;
        if (isStarted && comMan instanceof ServerCommunicationManager) {
            ((ServerCommunicationManager) comMan).reloadFeatureState();
        }
    }

    public void startup() {
        if (isStarted) {
            return;
        }
        try {
            if (quota != null) {
                quota.startup();
                quotaStarted = true;
            }
            if (materialService != null) {
                materialService.startup();
                materialStarted = true;
            }
            if (buildService != null) {
                buildService.startup();
                buildStarted = true;
            }
            debugService.startup();
            debugStarted = true;
            synMan.startup();
            managerStarted = true;
            if (webService != null) {
                webService.startup();
                webStarted = true;
            }
            isStarted = true;
        } catch (final RuntimeException | Error failure) {
            rollbackStartup(failure);
            throw failure;
        }
    }

    public void shutdown() {
        stopStartedServices(null);
        playerIdentifierProvider.clear();
        isStarted = false;
    }

    public boolean checkPartnerVersion(final String version) {
        if (version == null) {
            return true;
        }
        final String normalized = VersionComparator.normalize(version);
        // Preserve legacy behaviour: only hard-block the known bad sentinel version.
        return !"0.0.1".equals(normalized);
    }

    private File getConfigRoot() {
        if (isServer() && isIntegratedServer()) {
            return worldFolder == null ? new File(".") : worldFolder;
        }
        return new File(".", "config");
    }

    public File getConfigFolder() {
        final File root = getConfigRoot();
        return new File(root, Syncmatica.MOD_ID);
    }

    /**
     * @return the save directory of the world this server runs, or null off a
     *         server. Data that describes world blocks rather than schematics
     *         belongs here, so restoring a backup restores it too — on a
     *         dedicated server that is a different place from the config folder.
     */
    public File getWorldFolder() {
        return worldFolder;
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

    public void loadConfiguration() {
        final File configFolder = getConfigFolder();
        final File root = getConfigRoot();
        final File legacyFolder = new File(root, Syncmatica.LEGACY_MOD_ID);
        migrateLegacyFolder(legacyFolder, configFolder);

        boolean attemptToLoad = false;
        JsonObject configuration;
        try {
            configuration = new Gson().fromJson(new BufferedReader(new FileReader(getConfigFile())), JsonObject.class);
            attemptToLoad = true;
        } catch (final Exception ignored) {
            configuration = new JsonObject();
        }
        boolean needsRewrite = false;
        needsRewrite |= applyRootDefaults(configuration);
        if (isServer()) {
            needsRewrite |= loadConfigurationForService(quota, configuration, attemptToLoad);
            if (materialService != null) {
                needsRewrite |= loadConfigurationForService(materialService, configuration, attemptToLoad);
            }
            if (buildService != null) {
                needsRewrite |= loadConfigurationForService(buildService, configuration, attemptToLoad);
            }
            if (webService != null) {
                needsRewrite |= loadConfigurationForService(webService, configuration, attemptToLoad);
            }
        }
        needsRewrite |= loadConfigurationForService(debugService, configuration, attemptToLoad);
        loadedConfiguration = configuration;
        if (isServer()) {
            configRegistry = new ConfigRegistry();
            quota.registerConfigOptions(configRegistry);
            materialService.registerConfigOptions(configRegistry);
            buildService.registerConfigOptions(configRegistry);
            debugService.registerConfigOptions(configRegistry);
            configStore = new ConfigStore(getConfigFile().toPath(), configuration, configRegistry);
        } else {
            configRegistry = null;
            configStore = null;
        }
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

    private void migrateLegacyFolder(final File legacyFolder, final File preferredFolder) {
        if (preferredFolder.exists()) {
            return;
        }
        if (legacyFolder == null || !legacyFolder.isDirectory()) {
            return;
        }
        copyDirectory(legacyFolder, preferredFolder);
    }

    private void copyDirectory(final File source, final File target) {
        if (!source.isDirectory()) {
            return;
        }
        if (!target.exists() && !target.mkdirs()) {
            return;
        }
        final File[] children = source.listFiles();
        if (children == null) {
            return;
        }
        for (final File child : children) {
            final File dest = new File(target, child.getName());
            if (child.isDirectory()) {
                copyDirectory(child, dest);
            } else if (!dest.exists()) {
                copyFile(child, dest);
            }
        }
    }

    private void copyFile(final File source, final File target) {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(target)) {
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    private boolean applyRootDefaults(final JsonObject configuration) {
        boolean changed = false;
        if (!configuration.has("checkupdate")) {
            configuration.addProperty("checkupdate", true);
            changed = true;
        }
        if (!configuration.has("check_pre_release")) {
            configuration.addProperty("check_pre_release", false);
            changed = true;
        }
        return changed;
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

    private void rollbackStartup(final Throwable failure) {
        stopStartedServices(failure);
        playerIdentifierProvider.clear();
        isStarted = false;
    }

    private void stopStartedServices(final Throwable startupFailure) {
        if (webStarted) {
            stop(webService::shutdown, startupFailure);
            webStarted = false;
        }
        if (managerStarted) {
            stop(synMan::shutdown, startupFailure);
            managerStarted = false;
        }
        if (debugStarted) {
            stop(debugService::shutdown, startupFailure);
            debugStarted = false;
        }
        if (buildStarted) {
            stop(buildService::shutdown, startupFailure);
            buildStarted = false;
        }
        if (materialStarted) {
            stop(materialService::shutdown, startupFailure);
            materialStarted = false;
        }
        if (quotaStarted) {
            stop(quota::shutdown, startupFailure);
            quotaStarted = false;
        }
    }

    private static void stop(final Runnable operation, final Throwable startupFailure) {
        try {
            operation.run();
        } catch (final RuntimeException | Error shutdownFailure) {
            if (startupFailure == null) {
                throw shutdownFailure;
            }
            startupFailure.addSuppressed(shutdownFailure);
        }
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
