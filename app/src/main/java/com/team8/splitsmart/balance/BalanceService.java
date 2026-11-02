package com.team8.splitsmart.balance;

import com.team8.splitsmart.data.ExpenseRepository;
import com.team8.splitsmart.data.SettlementRepository;
import com.team8.splitsmart.data.User;
import com.team8.splitsmart.data.UserRepository;

import java.util.LinkedHashMap;
import java.util.Map;

/** Loads a group's expenses and settlements and returns the logged-in user's balances. */
public class BalanceService {
    private final ExpenseRepository expenseRepo;
    private final SettlementRepository settlementRepo;
    private final UserRepository userRepo;
    private final BalanceCalculator calculator;

    public BalanceService(ExpenseRepository expenseRepo, SettlementRepository settlementRepo,
                          UserRepository userRepo, BalanceCalculator calculator) {
        this.expenseRepo = expenseRepo;
        this.settlementRepo = settlementRepo;
        this.userRepo = userRepo;
        this.calculator = calculator;
    }

    public BalanceSummary getBalances(long groupId, long userId) {
        Map<Long, Integer> pairwise = calculator.computePairwise(userId,
                expenseRepo.findByGroup(groupId), settlementRepo.findByGroup(groupId));
        Map<Long, String> names = new LinkedHashMap<>();
        for (User member : userRepo.findByGroup(groupId)) {
            if (member.userId != userId) {
                names.put(member.userId, member.name);
            }
        }
        return calculator.summarize(pairwise, names);
    }
}
