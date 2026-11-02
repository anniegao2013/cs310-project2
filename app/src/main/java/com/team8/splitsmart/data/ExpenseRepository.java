package com.team8.splitsmart.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public class ExpenseRepository {
    private static final String TABLE = "expenses";
    private static final String WITH_SPLITS = "*,expense_splits(*)";

    private final SplitSmartDatabase db;

    public ExpenseRepository(SplitSmartDatabase db) {
        this.db = db;
    }

    /** Saves an expense and its splits in one transaction and returns the stored expense. */
    public Expense saveWithSplits(Expense expense, List<ExpenseSplit> splits) {
        JsonObject args = new JsonObject();
        args.add("p_expense", expenseFields(expense));
        args.add("p_splits", splitsJson(splits));
        long id = db.rpc("save_expense_with_splits", args).getAsLong();
        return findById(id);
    }

    /** Replaces an expense's editable fields and its splits in one transaction. */
    public void updateWithSplits(Expense expense, List<ExpenseSplit> splits) {
        JsonObject args = new JsonObject();
        args.addProperty("p_expense_id", expense.expenseId);
        args.add("p_expense", expenseFields(expense));
        args.add("p_splits", splitsJson(splits));
        db.rpc("update_expense_with_splits", args);
    }

    public void delete(long expenseId) {
        db.delete(TABLE, "expense_id", "eq." + expenseId);
    }

    public Expense findById(long expenseId) {
        List<Expense> rows = toList(db.select(TABLE, "select", WITH_SPLITS, "expense_id", "eq." + expenseId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Expense> findByGroup(long groupId) {
        return toList(db.select(TABLE,
                "select", WITH_SPLITS,
                "group_id", "eq." + groupId,
                "order", "created_date.desc,created_at.desc"));
    }

    public List<Expense> findByGroupAndMonth(long groupId, YearMonth month) {
        return toList(db.select(TABLE,
                "select", WITH_SPLITS,
                "group_id", "eq." + groupId,
                "created_date", "gte." + month.atDay(1),
                "created_date", "lte." + month.atEndOfMonth()));
    }

    private JsonObject expenseFields(Expense expense) {
        JsonObject row = new JsonObject();
        row.addProperty("group_id", expense.groupId);
        row.addProperty("title", expense.title);
        row.addProperty("amount_cents", expense.amountCents);
        row.addProperty("category", expense.category.name());
        row.addProperty("payer_id", expense.payerId);
        row.addProperty("created_by", expense.createdBy);
        row.addProperty("split_method", expense.splitMethod.name());
        row.addProperty("created_date", expense.createdDate.toString());
        row.addProperty("note", expense.note);
        return row;
    }

    private JsonArray splitsJson(List<ExpenseSplit> splits) {
        JsonArray array = new JsonArray();
        for (ExpenseSplit split : splits) {
            JsonObject row = new JsonObject();
            row.addProperty("user_id", split.userId);
            row.addProperty("share_cents", split.shareCents);
            row.addProperty("input_value", split.inputValue);
            array.add(row);
        }
        return array;
    }

    private List<Expense> toList(JsonArray rows) {
        List<Expense> expenses = new ArrayList<>();
        for (JsonElement row : rows) {
            expenses.add(db.gson().fromJson(row, Expense.class));
        }
        return expenses;
    }
}
