package cn.net.rms.syncmatica_r.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

final class NaturalOrderComparatorTest {

    @Test
    void numberedNamesSortByValueRatherThanByCharacter() {
        final List<String> names = new ArrayList<>(Arrays.asList("11", "2", "1", "10", "3"));

        names.sort(NaturalOrderComparator.INSTANCE);

        assertEquals(Arrays.asList("1", "2", "3", "10", "11"), names);
    }

    @Test
    void numbersEmbeddedInTextAreCompared() {
        final List<String> names = new ArrayList<>(Arrays.asList(
                "Floor 10 west", "Floor 2 east", "Floor 2 west", "Floor 1"));

        names.sort(NaturalOrderComparator.INSTANCE);

        assertEquals(Arrays.asList("Floor 1", "Floor 2 east", "Floor 2 west", "Floor 10 west"), names);
    }

    @Test
    void leadingZeroesDoNotChangeTheValue() {
        assertTrue(NaturalOrderComparator.INSTANCE.compare("region007", "region8") < 0);
        assertTrue(NaturalOrderComparator.INSTANCE.compare("0", "00") != 0);
        assertTrue(NaturalOrderComparator.INSTANCE.compare("region0", "region1") < 0);
    }

    @Test
    void digitRunsLongerThanALongStillOrderByValue() {
        final String smaller = "part" + "9".repeat(30);
        final String larger = "part1" + "0".repeat(30);

        assertTrue(NaturalOrderComparator.INSTANCE.compare(smaller, larger) < 0);
    }

    @Test
    void textIsComparedWithoutRegardToCase() {
        assertTrue(NaturalOrderComparator.INSTANCE.compare("apse", "Basement") < 0);
        assertTrue(NaturalOrderComparator.INSTANCE.compare("Roof", "wall") < 0);
    }

    @Test
    void namesThatDifferOnlyInCaseStillGetAStableOrder() {
        // Equal under the comparison rules, so the exact text decides; what
        // matters is that the answer is not zero, or a sorted list could shuffle.
        assertTrue(NaturalOrderComparator.INSTANCE.compare("Wall", "wall") != 0);
        assertEquals(0, NaturalOrderComparator.INSTANCE.compare("wall", "wall"));
    }

    @Test
    void aPrefixSortsBeforeTheLongerName() {
        assertTrue(NaturalOrderComparator.INSTANCE.compare("roof", "roof east") < 0);
        assertTrue(NaturalOrderComparator.INSTANCE.compare("roof east", "roof") > 0);
    }
}
