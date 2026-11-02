package com.team8.splitsmart.split;

import com.team8.splitsmart.data.SplitMethod;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Picks the split strategy, produces the share preview, and verifies shares add up to the total. */
public class SplitCalculator {
    private final Map<SplitMethod, SplitStrategy> strategies = new EnumMap<>(SplitMethod.class);

    public SplitCalculator() {
        strategies.put(SplitMethod.EQUAL, new EqualSplitStrategy());
        strategies.put(SplitMethod.EXACT_AMOUNT, new ExactAmountSplitStrategy());
        strategies.put(SplitMethod.PERCENTAGE, new PercentageSplitStrategy());
    }

    public SplitResult preview(SplitMethod method, int totalCents, List<Long> participantIds,
                               Map<Long, String> inputs) {
        if (totalCents <= 0) {
            return new SplitResult(method, new LinkedHashMap<>(), new LinkedHashMap<>(), false,
                    "Enter a total amount greater than $0.00");
        }
        if (participantIds.size() < 2) {
            return new SplitResult(method, new LinkedHashMap<>(), new LinkedHashMap<>(), false,
                    "Select at least one participant besides the payer");
        }
        return strategies.get(method).calculate(totalCents, participantIds, inputs);
    }

    public boolean verify(int totalCents, SplitResult split) {
        if (split == null || !split.valid) {
            return false;
        }
        int sum = 0;
        for (int share : split.sharesCents.values()) {
            sum += share;
        }
        return sum == totalCents;
    }
}
