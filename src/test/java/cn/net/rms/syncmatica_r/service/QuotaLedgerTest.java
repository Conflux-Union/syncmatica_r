package cn.net.rms.syncmatica_r.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class QuotaLedgerTest {
    @Test
    void countsEveryAcceptedChunkAgainstTheSameIdentity() {
        final QuotaLedger ledger = new QuotaLedger();

        assertTrue(ledger.tryConsume("player", 40L, 100L));
        assertTrue(ledger.tryConsume("player", 60L, 100L));
        assertFalse(ledger.tryConsume("player", 1L, 100L));
        assertEquals(100L, ledger.getConsumedBytes("player"));
    }

    @Test
    void rejectsInvalidConsumptionWithoutChangingState() {
        final QuotaLedger ledger = new QuotaLedger();

        assertFalse(ledger.tryConsume("player", 0L, 100L));
        assertFalse(ledger.tryConsume(null, 1L, 100L));
        assertEquals(0L, ledger.getConsumedBytes("player"));
    }
}
