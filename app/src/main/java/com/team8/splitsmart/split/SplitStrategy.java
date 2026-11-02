package com.team8.splitsmart.split;

import java.util.List;
import java.util.Map;

/** Common contract for the three split methods. */
public interface SplitStrategy {
    SplitResult calculate(int totalCents, List<Long> participantIds, Map<Long, String> inputs);
}
