package com.team8.splitsmart.split;

import com.team8.splitsmart.data.SplitMethod;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Each participant is assigned a percentage; percentages must add up to exactly 100.
 * Shares are rounded down to the cent and the leftover cents go to the largest remainders,
 * so the shares always add up to the total.
 */
public class PercentageSplitStrategy implements SplitStrategy {
    private static final Pattern PERCENT = Pattern.compile("^\\d+(\\.\\d{0,2})?$|^\\.\\d{1,2}$");
    private static final int FULL = 10_000; // 100.00% in basis points

    @Override
    public SplitResult calculate(int totalCents, List<Long> participantIds, Map<Long, String> inputs) {
        Map<Long, Integer> basisPoints = new LinkedHashMap<>();
        Map<Long, String> used = new LinkedHashMap<>();
        int sumPoints = 0;
        for (long id : participantIds) {
            String text = inputs.get(id);
            used.put(id, text);
            int points = toBasisPoints(text);
            if (points < 0) {
                return new SplitResult(SplitMethod.PERCENTAGE, new LinkedHashMap<>(), used, false,
                        "Enter a percentage (up to two decimals) for every participant");
            }
            basisPoints.put(id, points);
            sumPoints += points;
        }

        Map<Long, Integer> shares = new LinkedHashMap<>();
        List<long[]> remainders = new ArrayList<>(); // {participantIndex, remainder}
        int allocated = 0;
        for (int i = 0; i < participantIds.size(); i++) {
            long id = participantIds.get(i);
            long scaled = (long) totalCents * basisPoints.get(id);
            int share = (int) (scaled / FULL);
            shares.put(id, share);
            allocated += share;
            remainders.add(new long[]{i, scaled % FULL});
        }

        if (sumPoints != FULL) {
            return new SplitResult(SplitMethod.PERCENTAGE, shares, used, false,
                    "Percentages add up to " + BigDecimal.valueOf(sumPoints, 2).stripTrailingZeros().toPlainString()
                            + "% but must add up to 100%");
        }

        remainders.sort((a, b) -> b[1] != a[1] ? Long.compare(b[1], a[1]) : Long.compare(a[0], b[0]));
        int leftover = totalCents - allocated;
        for (int i = 0; i < leftover; i++) {
            long id = participantIds.get((int) remainders.get(i)[0]);
            shares.put(id, shares.get(id) + 1);
        }
        return new SplitResult(SplitMethod.PERCENTAGE, shares, used, true, null);
    }

    /** "33.33" -> 3333; returns -1 when the text is not a valid percentage between 0 and 100. */
    int toBasisPoints(String text) {
        if (text == null) {
            return -1;
        }
        String trimmed = text.trim().replace("%", "");
        if (!PERCENT.matcher(trimmed).matches()) {
            return -1;
        }
        BigDecimal points = new BigDecimal(trimmed).movePointRight(2);
        if (points.compareTo(BigDecimal.valueOf(FULL)) > 0) {
            return -1;
        }
        return points.intValueExact();
    }
}
