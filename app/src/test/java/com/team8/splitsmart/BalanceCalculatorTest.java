package com.team8.splitsmart;

import static org.junit.Assert.assertEquals;

import com.team8.splitsmart.balance.BalanceCalculator;
import com.team8.splitsmart.balance.BalanceSummary;
import com.team8.splitsmart.data.Expense;
import com.team8.splitsmart.data.ExpenseSplit;
import com.team8.splitsmart.data.Settlement;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BalanceCalculatorTest {
    private static final long ALICE = 1, BOB = 2, CAROL = 3, DAVID = 4;
    private final BalanceCalculator calculator = new BalanceCalculator();

    /** An expense paid by {@code payer} where {@code debtor} owes {@code cents} and the payer owes the rest. */
    private static Expense owes(long debtor, long payer, int cents) {
        Expense expense = new Expense();
        expense.payerId = payer;
        expense.amountCents = cents * 2;
        expense.splits = new ArrayList<>(Arrays.asList(
                new ExpenseSplit(payer, cents, null),
                new ExpenseSplit(debtor, cents, null)));
        return expense;
    }

    private static Settlement settlement(long payer, long payee, int cents) {
        Settlement settlement = new Settlement();
        settlement.payerId = payer;
        settlement.payeeId = payee;
        settlement.amountCents = cents;
        return settlement;
    }

    private static Map<Long, String> names(long... ids) {
        Map<Long, String> names = new LinkedHashMap<>();
        for (long id : ids) {
            names.put(id, "user" + id);
        }
        return names;
    }

    @Test
    public void payerDoesNotOweThemselves() {
        Expense expense = new Expense();
        expense.payerId = ALICE;
        expense.splits = Arrays.asList(
                new ExpenseSplit(ALICE, 1000, null),
                new ExpenseSplit(BOB, 1000, null),
                new ExpenseSplit(CAROL, 1000, null));
        Map<Long, Integer> alice = calculator.computePairwise(ALICE, Collections.singletonList(expense),
                Collections.emptyList());
        assertEquals(1000, (int) alice.get(BOB));
        assertEquals(1000, (int) alice.get(CAROL));
        assertEquals(null, alice.get(ALICE));
    }

    @Test
    public void combinesAllExpensesBetweenSamePair() {
        List<Expense> expenses = Arrays.asList(owes(ALICE, BOB, 1000), owes(ALICE, BOB, 500), owes(BOB, ALICE, 400));
        Map<Long, Integer> alice = calculator.computePairwise(ALICE, expenses, Collections.emptyList());
        assertEquals(-1100, (int) alice.get(BOB)); // Alice owes Bob $11.00
    }

    @Test
    public void totalsAreNotNetted() {
        List<Expense> expenses = Arrays.asList(owes(BOB, ALICE, 1000), owes(CAROL, ALICE, 500), owes(ALICE, DAVID, 400));
        BalanceSummary summary = calculator.summarize(
                calculator.computePairwise(ALICE, expenses, Collections.emptyList()), names(BOB, CAROL, DAVID));
        assertEquals(1500, summary.totalToReceiveCents);
        assertEquals(400, summary.totalToPayCents);
        assertEquals(3, summary.pairs.size());
    }

    @Test
    public void debtsAreNotSimplifiedAcrossPairs() {
        List<Expense> expenses = Arrays.asList(owes(ALICE, BOB, 1000), owes(BOB, CAROL, 1000));
        Map<Long, Integer> alice = calculator.computePairwise(ALICE, expenses, Collections.emptyList());
        assertEquals(-1000, (int) alice.get(BOB));
        assertEquals(null, alice.get(CAROL));
        BalanceSummary bob = calculator.summarize(
                calculator.computePairwise(BOB, expenses, Collections.emptyList()), names(ALICE, CAROL));
        assertEquals(1000, bob.totalToReceiveCents);
        assertEquals(1000, bob.totalToPayCents);
    }

    @Test
    public void settlementsReduceDebt() {
        List<Expense> expenses = Collections.singletonList(owes(BOB, ALICE, 1000));
        List<Settlement> settlements = Collections.singletonList(settlement(BOB, ALICE, 600));
        assertEquals(400, (int) calculator.computePairwise(ALICE, expenses, settlements).get(BOB));
        assertEquals(-400, (int) calculator.computePairwise(BOB, expenses, settlements).get(ALICE));
    }

    @Test
    public void membersWithNoActivityShowZero() {
        BalanceSummary summary = calculator.summarize(new LinkedHashMap<>(), names(BOB));
        assertEquals(0, summary.pairs.get(0).netCents);
        assertEquals(0, summary.totalToPayCents);
        assertEquals(0, summary.totalToReceiveCents);
    }
}
