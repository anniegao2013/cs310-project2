package com.team8.splitsmart.split;

import com.team8.splitsmart.data.SplitMethod;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Divides the total as evenly as possible; the first participants absorb the leftover cents. */
public class EqualSplitStrategy implements SplitStrategy {
    @Override
    public SplitResult calculate(int totalCents, List<Long> participantIds, Map<Long, String> inputs) {
        int count = participantIds.size();
        int base = totalCents / count;
        int remainder = totalCents % count;
        Map<Long, Integer> shares = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            shares.put(participantIds.get(i), base + (i < remainder ? 1 : 0));
        }
        return new SplitResult(SplitMethod.EQUAL, shares, new LinkedHashMap<>(), true, null);
    }
}
