package com.team8.splitsmart.split;

import com.team8.splitsmart.Money;
import com.team8.splitsmart.data.SplitMethod;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Each participant is assigned an exact amount; the amounts must add up to the total. */
public class ExactAmountSplitStrategy implements SplitStrategy {
    private static final Pattern AMOUNT = Pattern.compile("^\\d+(\\.\\d{0,2})?$|^\\.\\d{1,2}$");
    private static final int MAX_CENTS = 100_000_000; // $1,000,000.00

    @Override
    public SplitResult calculate(int totalCents, List<Long> participantIds, Map<Long, String> inputs) {
        Map<Long, Integer> shares = new LinkedHashMap<>();
        Map<Long, String> used = new LinkedHashMap<>();
        int sum = 0;
        for (long id : participantIds) {
            String text = inputs.get(id);
            used.put(id, text);
            int cents = toCents(text);
            if (cents < 0) {
                return new SplitResult(SplitMethod.EXACT_AMOUNT, shares, used, false,
                        "Enter a valid amount (up to two decimals) for every participant");
            }
            shares.put(id, cents);
            sum += cents;
        }
        if (sum != totalCents) {
            return new SplitResult(SplitMethod.EXACT_AMOUNT, shares, used, false,
                    "Amounts add up to " + Money.format(sum) + " but the total is " + Money.format(totalCents));
        }
        return new SplitResult(SplitMethod.EXACT_AMOUNT, shares, used, true, null);
    }

    /** "5.25" -> 525; a share may be zero. Returns -1 when the text is not a valid amount. */
    private int toCents(String text) {
        if (text == null) {
            return -1;
        }
        String trimmed = text.trim().replace("$", "").replace(",", "");
        if (!AMOUNT.matcher(trimmed).matches()) {
            return -1;
        }
        BigDecimal cents = new BigDecimal(trimmed).movePointRight(2);
        if (cents.compareTo(BigDecimal.valueOf(MAX_CENTS)) > 0) {
            return -1;
        }
        return cents.intValueExact();
    }
}
