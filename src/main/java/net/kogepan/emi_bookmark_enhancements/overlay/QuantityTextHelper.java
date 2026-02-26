package net.kogepan.emi_bookmark_enhancements.overlay;

import java.util.Locale;

final class QuantityTextHelper {
    private QuantityTextHelper() {
    }

    static String formatFull(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    static String formatCompact(long value) {
        long abs = Math.abs(value);
        if (abs >= 1_000_000_000L) {
            return trim(value / 1_000_000_000D) + "B";
        }
        if (abs >= 1_000_000L) {
            return trim(value / 1_000_000D) + "M";
        }
        if (abs >= 1_000L) {
            return trim(value / 1_000D) + "K";
        }
        return Long.toString(value);
    }

    static String formatStackBreakdown(long value) {
        long stacks = value / 64L;
        long remainder = value % 64L;
        return value + " = " + stacks + " x 64 + " + remainder;
    }

    private static String trim(double value) {
        String text = String.format(Locale.ROOT, "%.1f", value);
        if (text.endsWith(".0")) {
            return text.substring(0, text.length() - 2);
        }
        return text;
    }
}
