package com.team8.splitsmart.data;

import com.google.gson.annotations.SerializedName;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Expense {
    public long expenseId;
    public long groupId;
    public String title;
    public int amountCents;
    public Category category = Category.OTHER;
    public long payerId;
    public long createdBy;
    public SplitMethod splitMethod;
    public LocalDate createdDate;
    public String note;
    /** Server insertion timestamp, used only to order transactions created on the same day. */
    public String createdAt;

    @SerializedName("expense_splits")
    public List<ExpenseSplit> splits = new ArrayList<>();
}
