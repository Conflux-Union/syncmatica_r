package cn.net.rms.syncmatica_r.schematic;

import java.util.Map;
import java.util.TreeMap;

/**
 * Lookup table from Minecraft data versions to human-readable version strings.
 * Not every snapshot is listed; lookups return the closest known version at or
 * below the requested data version. Data cloned from MaLiLib via sakura-ryoko/syncmatica.
 */
public final class Schema {

    private static final TreeMap<Integer, String> VERSIONS = new TreeMap<>();

    static {
        VERSIONS.put(9999, "FUTURE");
        VERSIONS.put(5000, "26w14a");
        VERSIONS.put(4897, "26.2-pre-4");
        VERSIONS.put(4891, "26.2-snapshot-7");
        VERSIONS.put(4887, "26.2-snapshot-4");
        VERSIONS.put(4883, "26.2-snapshot-1");
        VERSIONS.put(4790, "26.1.2");
        VERSIONS.put(4788, "26.1.1");
        VERSIONS.put(4786, "26.1");
        VERSIONS.put(4774, "26.1-snapshot-6");
        VERSIONS.put(4764, "26.1-snapshot-1");
        VERSIONS.put(4671, "1.21.11");
        VERSIONS.put(4662, "25w46a");
        VERSIONS.put(4657, "25w41a");
        VERSIONS.put(4556, "1.21.10");
        VERSIONS.put(4554, "1.21.9");
        VERSIONS.put(4546, "25w36b");
        VERSIONS.put(4534, "25w31a");
        VERSIONS.put(4440, "1.21.8");
        VERSIONS.put(4438, "1.21.7");
        VERSIONS.put(4435, "1.21.6");
        VERSIONS.put(4429, "25w21a");
        VERSIONS.put(4423, "25w16a");
        VERSIONS.put(4325, "1.21.5");
        VERSIONS.put(4319, "25w10a");
        VERSIONS.put(4304, "25w03a");
        VERSIONS.put(4298, "25w02a");
        VERSIONS.put(4189, "1.21.4");
        VERSIONS.put(4178, "24w46a");
        VERSIONS.put(4174, "24w44a");
        VERSIONS.put(4082, "1.21.3");
        VERSIONS.put(4080, "1.21.2");
        VERSIONS.put(4072, "24w40a");
        VERSIONS.put(4065, "24w37a");
        VERSIONS.put(4062, "24w35a");
        VERSIONS.put(4058, "24w33a");
        VERSIONS.put(3955, "1.21.1");
        VERSIONS.put(3953, "1.21");
        VERSIONS.put(3946, "24w21a");
        VERSIONS.put(3940, "24w18a");
        VERSIONS.put(3837, "1.20.5");
        VERSIONS.put(3827, "24w14a");
        VERSIONS.put(3826, "24w13a");
        VERSIONS.put(3824, "24w12a");
        VERSIONS.put(3821, "24w10a");
        VERSIONS.put(3819, "24w09a");
        VERSIONS.put(3817, "24w07a");
        VERSIONS.put(3804, "24w03a");
        VERSIONS.put(3801, "23w51a");
        VERSIONS.put(3700, "1.20.4");
        VERSIONS.put(3691, "23w46a");
        VERSIONS.put(3687, "23w43b");
        VERSIONS.put(3679, "23w40a");
        VERSIONS.put(3578, "1.20.2");
        VERSIONS.put(3571, "23w35a");
        VERSIONS.put(3567, "23w31a");
        VERSIONS.put(3465, "1.20.1");
        VERSIONS.put(3463, "1.20");
        VERSIONS.put(3453, "23w18a");
        VERSIONS.put(3449, "23w16a");
        VERSIONS.put(3442, "23w12a");
        VERSIONS.put(3337, "1.19.4");
        VERSIONS.put(3218, "1.19.3");
        VERSIONS.put(3120, "1.19.2");
        VERSIONS.put(3117, "1.19.1");
        VERSIONS.put(3105, "1.19");
        VERSIONS.put(3096, "22w19a");
        VERSIONS.put(3091, "22w16a");
        VERSIONS.put(3080, "22w11a");
        VERSIONS.put(2975, "1.18.2");
        VERSIONS.put(2865, "1.18.1");
        VERSIONS.put(2860, "1.18");
        VERSIONS.put(2845, "21w44a");
        VERSIONS.put(2839, "21w41a");
        VERSIONS.put(2834, "21w37a");
        VERSIONS.put(2730, "1.17.1");
        VERSIONS.put(2724, "1.17");
        VERSIONS.put(2715, "21w20a");
        VERSIONS.put(2709, "21w15a");
        VERSIONS.put(2699, "21w10a");
        VERSIONS.put(2690, "21w05a");
        VERSIONS.put(2685, "20w49a");
        VERSIONS.put(2681, "20w45a");
        VERSIONS.put(2586, "1.16.5");
        VERSIONS.put(2584, "1.16.4");
        VERSIONS.put(2580, "1.16.3");
        VERSIONS.put(2578, "1.16.2");
        VERSIONS.put(2567, "1.16.1");
        VERSIONS.put(2566, "1.16");
        VERSIONS.put(2555, "20w22a");
        VERSIONS.put(2525, "20w15a");
        VERSIONS.put(2504, "20w06a");
        VERSIONS.put(2230, "1.15.2");
        VERSIONS.put(2227, "1.15.1");
        VERSIONS.put(2225, "1.15");
        VERSIONS.put(2217, "19w46b");
        VERSIONS.put(2208, "19w40a");
        VERSIONS.put(2200, "19w34a");
        VERSIONS.put(1976, "1.14.4");
        VERSIONS.put(1968, "1.14.3");
        VERSIONS.put(1963, "1.14.2");
        VERSIONS.put(1957, "1.14.1");
        VERSIONS.put(1952, "1.14");
        VERSIONS.put(1945, "19w14b");
        VERSIONS.put(1934, "19w08b");
        VERSIONS.put(1919, "18w50a");
        VERSIONS.put(1901, "18w43a");
        VERSIONS.put(1631, "1.13.2");
        VERSIONS.put(1628, "1.13.1");
        VERSIONS.put(1519, "1.13");
        VERSIONS.put(1499, "18w22c");
        VERSIONS.put(1481, "18w14b");
        VERSIONS.put(1469, "18w07c");
        VERSIONS.put(1457, "17w50a");
        VERSIONS.put(1451, "17w47a");
        VERSIONS.put(1449, "17w46a");
        VERSIONS.put(1444, "17w43a");
        VERSIONS.put(1343, "1.12.2");
        VERSIONS.put(1241, "1.12.1");
        VERSIONS.put(1139, "1.12");
        VERSIONS.put(922, "1.11.2");
        VERSIONS.put(819, "1.11");
        VERSIONS.put(512, "1.10.2");
        VERSIONS.put(510, "1.10");
        VERSIONS.put(184, "1.9.4");
        VERSIONS.put(169, "1.9");
        VERSIONS.put(100, "15w32a");
    }

    private Schema() {
    }

    /**
     * Returns the closest known Minecraft version string at or below the given
     * data version, or null if the data version predates all known entries.
     */
    public static String getVersionString(final int dataVersion) {
        final Map.Entry<Integer, String> entry = VERSIONS.floorEntry(dataVersion);
        return entry == null ? null : entry.getValue();
    }
}
