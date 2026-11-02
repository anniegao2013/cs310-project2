package com.team8.splitsmart.dashboard;

import com.team8.splitsmart.data.Expense;
import com.team8.splitsmart.data.ExpenseRepository;
import com.team8.splitsmart.data.ExpenseSplit;
import com.team8.splitsmart.data.Settlement;
import com.team8.splitsmart.data.SettlementRepository;
import com.team8.splitsmart.data.TransactionType;
import com.team8.splitsmart.data.User;
import com.team8.splitsmart.data.UserRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds the combined expense and settlement history of a group; searches and filters it. */
public class HistoryService {
    private final ExpenseRepository expenseRepo;
    private final SettlementRepository settlementRepo;
    private final UserRepository userRepo;

    public HistoryService(ExpenseRepository expenseRepo, SettlementRepository settlementRepo,
                          UserRepository userRepo) {
        this.expenseRepo = expenseRepo;
        this.settlementRepo = settlementRepo;
        this.userRepo = userRepo;
    }

    /** All expenses and settlements in the group, newest first. */
    public List<TransactionItem> getHistory(long groupId) {
        Map<Long, String> names = new HashMap<>();
        for (User member : userRepo.findByGroup(groupId)) {
            names.put(member.userId, member.name);
        }
        List<TransactionItem> items = new ArrayList<>();
        for (Expense expense : expenseRepo.findByGroup(groupId)) {
            items.add(fromExpense(expense, names));
        }
        for (Settlement settlement : settlementRepo.findByGroup(groupId)) {
            items.add(fromSettlement(settlement, names));
        }
        items.sort(TransactionItem.NEWEST_FIRST);
        return items;
    }

    /** Keyword search over expense titles and transaction notes, case-insensitive. */
    public List<TransactionItem> search(List<TransactionItem> items, String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>(items);
        }
        String needle = query.trim().toLowerCase(Locale.US);
        List<TransactionItem> result = new ArrayList<>();
        for (TransactionItem item : items) {
            boolean inTitle = item.type == TransactionType.EXPENSE
                    && item.title != null && item.title.toLowerCase(Locale.US).contains(needle);
            boolean inNote = item.note != null && item.note.toLowerCase(Locale.US).contains(needle);
            if (inTitle || inNote) {
                result.add(item);
            }
        }
        return result;
    }

    /** Applies the filter and sorts by the requested direction. */
    public List<TransactionItem> filter(List<TransactionItem> items, TransactionFilter filter) {
        List<TransactionItem> result = new ArrayList<>();
        for (TransactionItem item : items) {
            if (filter.matches(item)) {
                result.add(item);
            }
        }
        result.sort(TransactionItem.NEWEST_FIRST);
        if (!filter.newestFirst) {
            Collections.reverse(result);
        }
        return result;
    }

    private static TransactionItem fromExpense(Expense expense, Map<Long, String> names) {
        TransactionItem item = new TransactionItem();
        item.type = TransactionType.EXPENSE;
        item.id = expense.expenseId;
        item.title = expense.title;
        item.amountCents = expense.amountCents;
        item.date = expense.createdDate;
        item.createdAt = expense.createdAt;
        item.note = expense.note;
        item.createdBy = expense.createdBy;
        item.category = expense.category;
        item.splitMethod = expense.splitMethod;
        item.payerId = expense.payerId;
        item.payerName = nameOf(names, expense.payerId);
        addInvolved(item, expense.payerId, names);
        for (ExpenseSplit split : expense.splits) {
            addInvolved(item, split.userId, names);
            item.sharesByName.put(nameOf(names, split.userId), split.shareCents);
        }
        return item;
    }

    private static TransactionItem fromSettlement(Settlement settlement, Map<Long, String> names) {
        TransactionItem item = new TransactionItem();
        item.type = TransactionType.SETTLEMENT;
        item.id = settlement.settlementId;
        item.title = TransactionItem.SETTLEMENT_LABEL;
        item.amountCents = settlement.amountCents;
        item.date = settlement.date;
        item.createdAt = settlement.createdAt;
        item.note = settlement.note;
        item.createdBy = settlement.createdBy;
        item.payerId = settlement.payerId;
        item.payeeId = settlement.payeeId;
        item.settledBy = nameOf(names, settlement.payerId);
        item.payerName = item.settledBy;
        item.payeeName = nameOf(names, settlement.payeeId);
        addInvolved(item, settlement.payerId, names);
        addInvolved(item, settlement.payeeId, names);
        return item;
    }

    private static void addInvolved(TransactionItem item, long userId, Map<Long, String> names) {
        if (!item.involvedUserIds.contains(userId)) {
            item.involvedUserIds.add(userId);
            item.involvedUserNames.add(nameOf(names, userId));
        }
    }

    private static String nameOf(Map<Long, String> names, long userId) {
        String name = names.get(userId);
        return name != null ? name : "Unknown user";
    }
}
