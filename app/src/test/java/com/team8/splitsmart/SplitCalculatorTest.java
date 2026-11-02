package com.team8.splitsmart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.team8.splitsmart.data.SplitMethod;
import com.team8.splitsmart.split.SplitCalculator;
import com.team8.splitsmart.split.SplitResult;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SplitCalculatorTest {
    private final SplitCalculator calculator = new SplitCalculator();
    private final List<Long> abc = Arrays.asList(1L, 2L, 3L);

    private static int sum(SplitResult result) {
        int total = 0;
        for (int share : result.sharesCents.values()) {
            total += share;
        }
        return total;
    }

    @Test
    public void equalSplitOfTenDollarsThreeWaysGivesOneExtraCent() {
        SplitResult result = calculator.preview(SplitMethod.EQUAL, 1000, abc, new HashMap<>());
        assertTrue(result.valid);
        assertEquals(334, (int) result.sharesCents.get(1L));
        assertEquals(333, (int) result.sharesCents.get(2L));
        assertEquals(333, (int) result.sharesCents.get(3L));
        assertTrue(calculator.verify(1000, result));
    }

    @Test
    public void equalSplitThirtyDollarsThreeWays() {
        SplitResult result = calculator.preview(SplitMethod.EQUAL, 3000, abc, new HashMap<>());
        assertEquals(1000, (int) result.sharesCents.get(2L));
        assertEquals(3000, sum(result));
    }

    @Test
    public void needsAtLeastTwoParticipants() {
        SplitResult result = calculator.preview(SplitMethod.EQUAL, 1000, Arrays.asList(1L), new HashMap<>());
        assertFalse(result.valid);
    }

    @Test
    public void exactSplitMustAddUpToTotal() {
        Map<Long, String> inputs = new HashMap<>();
        inputs.put(1L, "5.00");
        inputs.put(2L, "3.00");
        inputs.put(3L, "1.99");
        SplitResult wrong = calculator.preview(SplitMethod.EXACT_AMOUNT, 1000, abc, inputs);
        assertFalse(wrong.valid);
        assertFalse(calculator.verify(1000, wrong));

        inputs.put(3L, "2");
        SplitResult right = calculator.preview(SplitMethod.EXACT_AMOUNT, 1000, abc, inputs);
        assertTrue(right.valid);
        assertEquals(200, (int) right.sharesCents.get(3L));
    }

    @Test
    public void exactSplitRejectsMissingOrBadAmounts() {
        Map<Long, String> inputs = new HashMap<>();
        inputs.put(1L, "5.00");
        inputs.put(2L, "5.001");
        assertFalse(calculator.preview(SplitMethod.EXACT_AMOUNT, 1000, abc, inputs).valid);
    }

    @Test
    public void percentageSplitMustTotalOneHundred() {
        Map<Long, String> inputs = new HashMap<>();
        inputs.put(1L, "50");
        inputs.put(2L, "30");
        inputs.put(3L, "10");
        SplitResult result = calculator.preview(SplitMethod.PERCENTAGE, 1000, abc, inputs);
        assertFalse(result.valid);
    }

    @Test
    public void percentageSplitRoundsToCentsAndSumsExactly() {
        Map<Long, String> inputs = new HashMap<>();
        inputs.put(1L, "33.33");
        inputs.put(2L, "33.33");
        inputs.put(3L, "33.34");
        SplitResult result = calculator.preview(SplitMethod.PERCENTAGE, 1000, abc, inputs);
        assertTrue(result.valid);
        assertEquals(1000, sum(result));
        assertEquals(333, (int) result.sharesCents.get(1L));
        assertEquals(333, (int) result.sharesCents.get(2L));
        assertEquals(334, (int) result.sharesCents.get(3L));
    }

    @Test
    public void percentageSplitWithRemaindersStillSumsToTotal() {
        Map<Long, String> inputs = new HashMap<>();
        inputs.put(1L, "50");
        inputs.put(2L, "25");
        inputs.put(3L, "25");
        SplitResult result = calculator.preview(SplitMethod.PERCENTAGE, 101, abc, inputs);
        assertTrue(result.valid);
        assertEquals(101, sum(result));
    }
}
