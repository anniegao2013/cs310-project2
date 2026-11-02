package com.team8.splitsmart.expense;

import com.team8.splitsmart.data.Category;

import java.util.ArrayList;
import java.util.List;

/** Unsaved form input for an expense. */
public class ExpenseDraft {
    public long groupId;
    public String title;
    public String amountText;
    public Category category = Category.OTHER;
    public long payerId;
    /** Includes the payer. */
    public List<Long> participantIds = new ArrayList<>();
    public String note;
}
