package cn.net.rms.syncmatica_r.util;

import net.minecraft.util.Identifier;

import java.util.Optional;

/**
 * Central utility for Identifier parsing to avoid scattered preprocessor blocks.
 */
public final class IdentifierUtil {

    private IdentifierUtil() {
    }

    public static Identifier require(final String value) {
        if (value == null) {
            throw new IllegalArgumentException("Identifier value is null");
        }
        return toIdentifier(value);
    }

    public static Optional<Identifier> tryParse(final String value) {
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(toIdentifier(value));
        } catch (final IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static Identifier toIdentifier(final String value) {
//#if MC >= 12005
//$$         return Identifier.of(value);
//#else
        return new Identifier(value);
//#endif
    }
}
