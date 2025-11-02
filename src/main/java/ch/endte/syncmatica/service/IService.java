package ch.endte.syncmatica.service;

import ch.endte.syncmatica.Context;

public interface IService {

    Context getContext();

    void setContext(Context context);

    void getDefaultConfiguration(IServiceConfiguration configuration);

    String getConfigKey();

    void configure(IServiceConfiguration configuration);

    void startup();

    void shutdown();
}
