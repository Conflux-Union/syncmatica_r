package cn.net.rms.syncmatica_r.util;

public final class VersionComparator {

    private VersionComparator() {
    }

    public static int compare(final String localVersion, final String remoteVersion) {
        final VersionInfo local = parse(localVersion);
        final VersionInfo remote = parse(remoteVersion);
        if (!local.preRelease && remote.preRelease) {
            return 1;
        }
        final int majorCompare = Integer.compare(local.major, remote.major);
        if (majorCompare != 0) {
            return majorCompare;
        }
        final int minorCompare = Integer.compare(local.minor, remote.minor);
        if (minorCompare != 0) {
            return minorCompare;
        }
        final int patchCompare = Integer.compare(local.patch, remote.patch);
        if (patchCompare != 0) {
            return patchCompare;
        }
        if (local.preRelease && !remote.preRelease) {
            return -1;
        }
        return Integer.compare(local.preNumber, remote.preNumber);
    }

    public static int compareAllowPreRelease(final String localVersion, final String remoteVersion) {
        final VersionInfo local = parse(localVersion);
        final VersionInfo remote = parse(remoteVersion);
        final int majorCompare = Integer.compare(local.major, remote.major);
        if (majorCompare != 0) {
            return majorCompare;
        }
        final int minorCompare = Integer.compare(local.minor, remote.minor);
        if (minorCompare != 0) {
            return minorCompare;
        }
        final int patchCompare = Integer.compare(local.patch, remote.patch);
        if (patchCompare != 0) {
            return patchCompare;
        }
        if (!local.preRelease && !remote.preRelease) {
            return 0;
        }
        if (!local.preRelease && remote.preRelease) {
            return 1;
        }
        if (local.preRelease && !remote.preRelease) {
            return -1;
        }
        return Integer.compare(local.preNumber, remote.preNumber);
    }

    public static String normalize(final String rawVersion) {
        final VersionInfo info = parse(rawVersion);
        final StringBuilder builder = new StringBuilder();
        builder.append(info.major).append('.').append(info.minor).append('.').append(info.patch);
        if (info.preRelease) {
            builder.append("-pre-").append(info.preNumber);
        }
        return builder.toString();
    }

    private static VersionInfo parse(final String rawVersion) {
        if (rawVersion == null) {
            return new VersionInfo(0, 0, 0, false, 0);
        }
        String trimmed = rawVersion.trim();
        if (trimmed.isEmpty()) {
            return new VersionInfo(0, 0, 0, false, 0);
        }
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
            trimmed = trimmed.substring(1);
        }
        String core = trimmed;
        String prePart = null;
        final int dashIndex = trimmed.indexOf('-');
        if (dashIndex >= 0) {
            core = trimmed.substring(0, dashIndex);
            prePart = trimmed.substring(dashIndex + 1);
        }
        final String[] parts = core.split("\\.");
        final int major = parseIntSafe(parts, 0);
        final int minor = parseIntSafe(parts, 1);
        final int patch = parseIntSafe(parts, 2);
        boolean preRelease = false;
        int preNumber = 0;
        if (prePart != null && !prePart.isEmpty()) {
            final String[] preParts = prePart.split("-");
            if (preParts.length == 2 && "pre".equals(preParts[0])) {
                preRelease = true;
                preNumber = parsePositiveInt(preParts[1]);
            }
        }
        return new VersionInfo(major, minor, patch, preRelease, preNumber);
    }

    private static int parseIntSafe(final String[] parts, final int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index]);
        } catch (final NumberFormatException ignored) {
            return 0;
        }
    }

    private static int parsePositiveInt(final String value) {
        try {
            final int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                return 0;
            }
            return parsed;
        } catch (final NumberFormatException ignored) {
            return 0;
        }
    }

    private static final class VersionInfo {

        private final int major;
        private final int minor;
        private final int patch;
        private final boolean preRelease;
        private final int preNumber;

        private VersionInfo(final int major, final int minor, final int patch, final boolean preRelease, final int preNumber) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.preRelease = preRelease;
            this.preNumber = preNumber;
        }
    }
}
