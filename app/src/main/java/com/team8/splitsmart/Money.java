package com.team8.splitsmart;

import java.util.Locale;

/** Formats integer cents as US dollars. */
public final class Money {
    private Money() {
    }

    public static String format(long cents) {
        String sign = cents < 0 ? "-" : "";
        long abs = Math.abs(cents);
        return String.format(Locale.US, "%s$%,d.%02d", sign, abs / 100, abs % 100);
    }

    /** Plain decimal without the dollar sign, e.g. 1234 -> "12.34", for pre-filling input fields. */
    public static String toInput(long cents) {
        return String.format(Locale.US, "%d.%02d", cents / 100, cents % 100);
    }
}
