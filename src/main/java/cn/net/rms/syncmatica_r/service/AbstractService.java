package cn.net.rms.syncmatica_r.service;

import cn.net.rms.syncmatica_r.Context;

abstract class AbstractService implements IService {

    Context context;

    @Override
    public Context getContext() {
        return context;
    }

    @Override
    public void setContext(final Context context) {
        this.context = context;
    }
}
