package cn.net.rms.syncmatica_r.service;

public class PlacementAccessService extends AbstractService {

    public static final String CONFIG_KEY = "placement";
    public static final Boolean ALLOW_ANYONE_TO_MOVE_DEFAULT = false;

    Boolean allowAnyoneToMove = ALLOW_ANYONE_TO_MOVE_DEFAULT;

    public boolean isAllowAnyoneToMove() {
        return Boolean.TRUE.equals(allowAnyoneToMove);
    }

    @Override
    public String getConfigKey() {
        return CONFIG_KEY;
    }

    @Override
    public void getDefaultConfiguration(final IServiceConfiguration configuration) {
        final ConfigRegistry registry = new ConfigRegistry();
        registerConfigOptions(registry);
        registry.saveDefaults(getConfigKey(), configuration);
    }

    @Override
    public void configure(final IServiceConfiguration configuration) {
        configuration.loadBoolean("allow_anyone_to_move", this::setAllowAnyoneToMove);
    }

    public void registerConfigOptions(final ConfigRegistry registry) {
        registry.add(ConfigOption.bool(
                getConfigKey(), "allow_anyone_to_move", ALLOW_ANYONE_TO_MOVE_DEFAULT,
                () -> allowAnyoneToMove, this::setAllowAnyoneToMove));
    }

    private void setAllowAnyoneToMove(final boolean value) {
        allowAnyoneToMove = value;
    }

    @Override
    public void startup() {
    }

    @Override
    public void shutdown() {
    }
}
