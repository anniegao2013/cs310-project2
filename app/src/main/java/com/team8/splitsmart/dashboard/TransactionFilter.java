package com.team8.splitsmart.dashboard;

import com.team8.splitsmart.data.Category;
import com.team8.splitsmart.data.TransactionType;

/** Filter criteria for group history. Null / 0 fields mean "any". */
public class TransactionFilter {
    public TransactionType type;
    /** Filters expenses by category; settlements never match a category filter. */
    public Category category;
    /** Only transactions this user is involved in. */
    public long memberId;
    public boolean newestFirst = true;

    public boolean matches(TransactionItem item) {
        if (type != null && item.type != type) {
            return false;
        }
        if (category != null && (item.type != TransactionType.EXPENSE || item.category != category)) {
            return false;
        }
        return memberId == 0 || item.involvedUserIds.contains(memberId);
    }
}
