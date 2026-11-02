package com.team8.splitsmart.dashboard;

import com.team8.splitsmart.data.Category;

import java.time.YearMonth;
import java.util.Map;

public class MonthlySummary {
    public final YearMonth month;
    public final int expenseCount;
    public final int totalCents;
    /** Every category is present (0 if unused), so the category totals always add up to the total. */
    public final Map<Category, Integer> byCategoryCents;

    public MonthlySummary(YearMonth month, int expenseCount, int totalCents, Map<Category, Integer> byCategoryCents) {
        this.month = month;
        this.expenseCount = expenseCount;
        this.totalCents = totalCents;
        this.byCategoryCents = byCategoryCents;
    }
}
