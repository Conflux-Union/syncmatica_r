package cn.net.rms.syncmatica_r.util;

import java.util.Comparator;

/**
 * Orders names the way a person reading a numbered list expects: a run of
 * digits is compared by its value rather than character by character, so
 * {@code 2} comes before {@code 10} instead of after it.
 *
 * <p>Region and schematic names are written by players, and numbering them is
 * the obvious way to split a build up. Plain lexicographic order turns that
 * into {@code 1, 10, 11, 2}, which is exactly the ordering the numbering was
 * meant to avoid.
 *
 * <p>Text outside a digit run is compared case-insensitively, matching what
 * these lists did before. Two names that compare equal under those rules fall
 * back to their exact text so the ordering stays total — otherwise
 * {@code "Wall"} and {@code "wall"}, or {@code "01"} and {@code "1"}, would sort
 * unpredictably against each other.
 */
public final class NaturalOrderComparator implements Comparator<String> {

    public static final NaturalOrderComparator INSTANCE = new NaturalOrderComparator();

    private NaturalOrderComparator() {
    }

    @Override
    public int compare(final String left, final String right) {
        if (left == null || right == null) {
            return left == right ? 0 : (left == null ? -1 : 1);
        }
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            final char leftChar = left.charAt(leftIndex);
            final char rightChar = right.charAt(rightIndex);
            if (isDigit(leftChar) && isDigit(rightChar)) {
                final int leftEnd = digitRunEnd(left, leftIndex);
                final int rightEnd = digitRunEnd(right, rightIndex);
                final int digits = compareDigitRuns(left, leftIndex, leftEnd, right, rightIndex, rightEnd);
                if (digits != 0) {
                    return digits;
                }
                leftIndex = leftEnd;
                rightIndex = rightEnd;
                continue;
            }
            final int letters = Character.compare(fold(leftChar), fold(rightChar));
            if (letters != 0) {
                return letters;
            }
            leftIndex++;
            rightIndex++;
        }
        if (leftIndex < left.length()) {
            return 1;
        }
        if (rightIndex < right.length()) {
            return -1;
        }
        return left.compareTo(right);
    }

    /**
     * Compares two digit runs by value without parsing them, so a name carrying
     * more digits than a {@code long} holds still orders sensibly.
     */
    private static int compareDigitRuns(final String left, final int leftStart, final int leftEnd,
                                        final String right, final int rightStart, final int rightEnd) {
        int leftIndex = skipLeadingZeros(left, leftStart, leftEnd);
        int rightIndex = skipLeadingZeros(right, rightStart, rightEnd);
        final int leftDigits = leftEnd - leftIndex;
        final int rightDigits = rightEnd - rightIndex;
        if (leftDigits != rightDigits) {
            return leftDigits < rightDigits ? -1 : 1;
        }
        while (leftIndex < leftEnd) {
            final int difference = left.charAt(leftIndex++) - right.charAt(rightIndex++);
            if (difference != 0) {
                return difference < 0 ? -1 : 1;
            }
        }
        return 0;
    }

    /** Keeps the last zero of an all-zero run so {@code "0"} still has a digit to compare. */
    private static int skipLeadingZeros(final String text, final int start, final int end) {
        int index = start;
        while (index < end - 1 && text.charAt(index) == '0') {
            index++;
        }
        return index;
    }

    private static int digitRunEnd(final String text, final int start) {
        int index = start;
        while (index < text.length() && isDigit(text.charAt(index))) {
            index++;
        }
        return index;
    }

    /**
     * Deliberately ASCII only: a digit run is compared through its characters,
     * which is exact for {@code '0'}–{@code '9'} and wrong for every other
     * numeral {@link Character#isDigit} accepts.
     */
    private static boolean isDigit(final char character) {
        return character >= '0' && character <= '9';
    }

    /** The same folding {@link String#compareToIgnoreCase} uses. */
    private static char fold(final char character) {
        return Character.toLowerCase(Character.toUpperCase(character));
    }
}
