package com.team8.splitsmart.dashboard;

import com.team8.splitsmart.data.Category;
import com.team8.splitsmart.data.Expense;
import com.team8.splitsmart.data.ExpenseRepository;

import java.time.YearMonth;
import java.util.EnumMap;
import java.util.Map;

/** Totals a month's group spending and breaks it down by category. Settlements are never included. */
public class MonthlySummaryService {
    private final ExpenseRepository expenseRepo;

    public MonthlySummaryService(ExpenseRepository expenseRepo) {
        this.expenseRepo = expenseRepo;
    }

    /** Counts only expenses whose creation date falls in {@code month}. */
    public MonthlySummary getSummary(long groupId, YearMonth month) {
        Map<Category, Integer> byCategory = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            byCategory.put(category, 0);
        }
        int count = 0;
        int total = 0;
        for (Expense expense : expenseRepo.findByGroupAndMonth(groupId, month)) {
            if (!YearMonth.from(expense.createdDate).equals(month)) {
                continue;
            }
            count++;
            total += expense.amountCents;
            byCategory.merge(expense.category, expense.amountCents, Integer::sum);
        }
        return new MonthlySummary(month, count, total, byCategory);
    }
}
