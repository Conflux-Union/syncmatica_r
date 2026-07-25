package cn.net.rms.syncmatica_r.service;

import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.service.IServiceConfiguration;

public class QuotaService extends AbstractService {

    public static final Boolean IS_ENABLED_DEFAULT = false;
    public static final Integer QUOTA_LIMIT_DEFAULT = 40000000;

    private final QuotaLedger ledger = new QuotaLedger();
    Boolean isEnabled = IS_ENABLED_DEFAULT;
    Integer limit = QUOTA_LIMIT_DEFAULT;

    public long getLimitBytes() {
        return Math.max(0L, limit.longValue());
    }

    public boolean tryConsume(final ExchangeTarget sender, final long newData) {
        if (!Boolean.TRUE.equals(isEnabled)) {
            return true;
        }
        return sender != null && ledger.tryConsume(sender.getPersistentName(), newData, Math.max(0L, limit.longValue()));
    }

    @Override
    public void getDefaultConfiguration(final IServiceConfiguration configuration) {
        configuration.saveBoolean("enabled", IS_ENABLED_DEFAULT);
        configuration.saveInteger("limit", QUOTA_LIMIT_DEFAULT);
    }

    @Override
    public String getConfigKey() {
        return "quota";
    }

    @Override
    public void configure(final IServiceConfiguration configuration) {
        configuration.loadBoolean("enabled", b -> isEnabled = b);
        configuration.loadInteger("limit", i -> limit = Math.max(0, i));
    }

    @Override
    public void startup() {
    }

    @Override
    public void shutdown() {
        ledger.clear();
    }
}
