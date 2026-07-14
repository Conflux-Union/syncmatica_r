package cn.net.rms.syncmatica_r.service;

import java.util.HashMap;
import java.util.Map;

final class QuotaLedger {
    private final Map<String, Long> consumedBytes = new HashMap<>();

    boolean tryConsume(final String identity, final long bytes, final long limit) {
        if (identity == null || bytes <= 0L || limit < 0L) {
            return false;
        }
        final long consumed = consumedBytes.getOrDefault(identity, 0L);
        if (consumed > limit - bytes) {
            return false;
        }
        consumedBytes.put(identity, consumed + bytes);
        return true;
    }

    long getConsumedBytes(final String identity) {
        return consumedBytes.getOrDefault(identity, 0L);
    }

    void clear() {
        consumedBytes.clear();
    }
}
