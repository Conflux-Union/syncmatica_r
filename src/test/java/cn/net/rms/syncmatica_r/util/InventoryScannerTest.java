package cn.net.rms.syncmatica_r.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.net.rms.syncmatica_r.material.MaterialKey;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class InventoryScannerTest {
    @Test
    void materialTotalsSaturateInsteadOfOverflowing() {
        final MaterialKey key = new MaterialKey(IdentifierUtil.require("minecraft:stone"), "");
        final Map<MaterialKey, Integer> totals = new HashMap<>();
        totals.put(key, Integer.MAX_VALUE - 1);

        InventoryScanner.mergeCount(totals, key, 10);

        assertEquals(Integer.MAX_VALUE, totals.get(key));
    }
}
