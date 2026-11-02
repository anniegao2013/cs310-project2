package com.team8.splitsmart.data;

public class ExpenseSplit {
    public long expenseId;
    public long userId;
    public int shareCents;
    /** What the user typed for this participant (exact amount or percentage); null for equal splits. */
    public String inputValue;

    public ExpenseSplit() {
    }

    public ExpenseSplit(long userId, int shareCents, String inputValue) {
        this.userId = userId;
        this.shareCents = shareCents;
        this.inputValue = inputValue;
    }
}
