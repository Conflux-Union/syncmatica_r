package cn.net.rms.syncmatica_r.service;

import cn.net.rms.syncmatica_r.Context;
import ch.endte.syncmatica.service.IServiceConfiguration;

public interface IService {

    Context getContext();

    void setContext(Context context);

    void getDefaultConfiguration(IServiceConfiguration configuration);

    String getConfigKey();

    void configure(IServiceConfiguration configuration);

    void startup();

    void shutdown();
}
