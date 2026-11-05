package com.team8.splitsmart;

import static org.junit.Assert.assertEquals;

import com.team8.splitsmart.dashboard.HistoryService;
import com.team8.splitsmart.dashboard.MonthlySummary;
import com.team8.splitsmart.dashboard.MonthlySummaryService;
import com.team8.splitsmart.dashboard.TransactionFilter;
import com.team8.splitsmart.dashboard.TransactionItem;
import com.team8.splitsmart.data.Category;
import com.team8.splitsmart.data.Expense;
import com.team8.splitsmart.data.ExpenseRepository;
import com.team8.splitsmart.data.ExpenseSplit;
import com.team8.splitsmart.data.Settlement;
import com.team8.splitsmart.data.SettlementRepository;
import com.team8.splitsmart.data.TransactionType;
import com.team8.splitsmart.data.User;
import com.team8.splitsmart.data.UserRepository;

import org.junit.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

public class DashboardTest {
    private static final long GROUP_ID = 1;

    /** The repositories return sample data, so the services can be tested without a database. */
    private final HistoryService history = new HistoryService(
            new ExpenseRepository(null) {
                @Override
                public List<Expense> findByGroup(long groupId) {
                    return Arrays.asList(
                            expense(1, "Groceries", Category.FOOD, 3000, "2026-09-17", null),
                            expense(2, "Rent", Category.HOUSING, 120000, "2026-10-01", "October"),
                            expense(3, "Uber", Category.TRANSPORTATION, 2500, "2026-10-15", null));
                }
            },
            new SettlementRepository(null) {
                @Override
                public List<Settlement> findByGroup(long groupId) {
                    return Arrays.asList(settlement(1, "2026-10-20", "venmo for groceries"));
                }
            },
            new UserRepository(null) {
                @Override
                public List<User> findByGroup(long groupId) {
                    return Arrays.asList(user(1, "Alice"), user(2, "Bob"), user(3, "Carol"));
                }
            });

    private final MonthlySummaryService summaryService = new MonthlySummaryService(new ExpenseRepository(null) {
        @Override
        public List<Expense> findByGroupAndMonth(long groupId, YearMonth month) {
            return Arrays.asList(
                    expense(1, "Groceries", Category.FOOD, 3000, "2026-09-17", null),
                    expense(2, "Rent", Category.HOUSING, 120000, "2026-10-01", null),
                    expense(3, "Uber", Category.TRANSPORTATION, 2500, "2026-10-15", null),
                    expense(4, "Pizza", Category.FOOD, 1999, "2026-10-31", null));
        }
    });

    private static User user(long id, String name) {
        User user = new User();
        user.userId = id;
        user.name = name;
        return user;
    }

    private static Expense expense(long id, String title, Category category, int cents, String date, String note) {
        Expense expense = new Expense();
        expense.expenseId = id;
        expense.title = title;
        expense.category = category;
        expense.amountCents = cents;
        expense.createdDate = LocalDate.parse(date);
        expense.note = note;
        expense.payerId = 1;
        expense.splits = Arrays.asList(new ExpenseSplit(1, cents / 2, null), new ExpenseSplit(2, cents - cents / 2, null));
        return expense;
    }

    private static Settlement settlement(long id, String date, String note) {
        Settlement settlement = new Settlement();
        settlement.settlementId = id;
        settlement.payerId = 2;
        settlement.payeeId = 3;
        settlement.amountCents = 500;
        settlement.date = LocalDate.parse(date);
        settlement.note = note;
        return settlement;
    }

    private List<TransactionItem> items() {
        return history.getHistory(GROUP_ID);
    }

    @Test
    public void historyIsNewestFirstAndLabelsSettlements() {
        List<TransactionItem> items = items();
        assertEquals(4, items.size());
        assertEquals(TransactionType.SETTLEMENT, items.get(0).type);
        assertEquals("Settlement", items.get(0).title);
        assertEquals("Groceries", items.get(3).title);
        assertEquals(Arrays.asList("Bob", "Carol"), items.get(0).involvedUserNames);
    }

    @Test
    public void keywordSearchesTitlesAndNotesCaseSensitively() {
        List<TransactionItem> title = history.search(items(), "Grocer");
        assertEquals(1, title.size());
        assertEquals("Groceries", title.get(0).title);

        List<TransactionItem> settlementNote = history.search(items(), "grocer");
        assertEquals(1, settlementNote.size());
        assertEquals(TransactionType.SETTLEMENT, settlementNote.get(0).type);

        assertEquals(1, history.search(items(), "October").size());
        assertEquals(0, history.search(items(), "OCTOBER").size());
    }

    @Test
    public void filtersByTypeCategoryMemberAndSort() {
        TransactionFilter filter = new TransactionFilter();
        filter.type = TransactionType.EXPENSE;
        assertEquals(3, history.filter(items(), filter).size());

        filter = new TransactionFilter();
        filter.category = Category.HOUSING;
        assertEquals("Rent", history.filter(items(), filter).get(0).title);
        assertEquals(1, history.filter(items(), filter).size());

        filter = new TransactionFilter();
        filter.memberId = 3;
        assertEquals(1, history.filter(items(), filter).size());

        filter = new TransactionFilter();
        filter.newestFirst = false;
        assertEquals("Groceries", history.filter(items(), filter).get(0).title);
    }

    @Test
    public void monthlySummaryCountsOnlyThatMonthsExpenses() {
        MonthlySummary october = summaryService.getSummary(GROUP_ID, YearMonth.of(2026, 10));
        assertEquals(3, october.expenseCount);
        assertEquals(124499, october.totalCents);
        assertEquals(1999, (int) october.byCategoryCents.get(Category.FOOD));
        assertEquals(0, (int) october.byCategoryCents.get(Category.ENTERTAINMENT));
        int sum = 0;
        for (int cents : october.byCategoryCents.values()) {
            sum += cents;
        }
        assertEquals(october.totalCents, sum);
    }
}
