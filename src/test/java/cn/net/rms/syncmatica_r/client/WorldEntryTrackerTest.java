package cn.net.rms.syncmatica_r.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class WorldEntryTrackerTest {
    @Test
    void emitsOnceForEachWorldEntry() {
        final WorldEntryTracker tracker = new WorldEntryTracker();

        assertFalse(tracker.update(false));
        assertTrue(tracker.update(true));
        assertFalse(tracker.update(true));
        assertFalse(tracker.update(false));
        assertTrue(tracker.update(true));
    }
}
