package com.team8.splitsmart.balance;

import com.team8.splitsmart.data.Expense;
import com.team8.splitsmart.data.ExpenseSplit;
import com.team8.splitsmart.data.Settlement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure calculation of pairwise balances. Debts are only ever combined between the same two users;
 * nothing is simplified across different pairs.
 */
public class BalanceCalculator {

    /**
     * Net balance between {@code userId} and every other user they share an expense or settlement with.
     * Positive values mean the other user owes {@code userId}; negative values mean {@code userId} owes them.
     */
    public Map<Long, Integer> computePairwise(long userId, List<Expense> expenses, List<Settlement> settlements) {
        Map<Long, Integer> net = new HashMap<>();
        for (Expense expense : expenses) {
            for (ExpenseSplit split : expense.splits) {
                if (split.userId == expense.payerId) {
                    continue; // nobody owes money to themselves
                }
                if (expense.payerId == userId) {
                    net.merge(split.userId, split.shareCents, Integer::sum);
                } else if (split.userId == userId) {
                    net.merge(expense.payerId, -split.shareCents, Integer::sum);
                }
            }
        }
        for (Settlement settlement : settlements) {
            if (settlement.payerId == userId) {
                net.merge(settlement.payeeId, settlement.amountCents, Integer::sum);
            } else if (settlement.payeeId == userId) {
                net.merge(settlement.payerId, -settlement.amountCents, Integer::sum);
            }
        }
        return net;
    }

    /**
     * Builds the balance page: one row per other member (in the given order) plus the two totals,
     * which are sums of the individual balances and never netted against each other.
     */
    public BalanceSummary summarize(Map<Long, Integer> pairwise, Map<Long, String> names) {
        int toPay = 0;
        int toReceive = 0;
        List<PairBalance> pairs = new ArrayList<>();
        for (Map.Entry<Long, String> member : names.entrySet()) {
            int netCents = pairwise.getOrDefault(member.getKey(), 0);
            if (netCents > 0) {
                toReceive += netCents;
            } else {
                toPay -= netCents;
            }
            pairs.add(new PairBalance(member.getKey(), member.getValue(), netCents));
        }
        return new BalanceSummary(toPay, toReceive, pairs);
    }
}
