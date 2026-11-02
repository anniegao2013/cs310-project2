package com.team8.splitsmart.dashboard;

import com.team8.splitsmart.data.Category;
import com.team8.splitsmart.data.SplitMethod;
import com.team8.splitsmart.data.TransactionType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One row of group history: an expense or a settlement, with names already resolved. */
public class TransactionItem {
    public static final String SETTLEMENT_LABEL = "Settlement";

    /** Newest first: by date, then by insertion time within the same day. */
    public static final Comparator<TransactionItem> NEWEST_FIRST = Comparator
            .comparing((TransactionItem item) -> item.date)
            .thenComparingLong(TransactionItem::createdAtMillis)
            .thenComparingLong(item -> item.id)
            .reversed();

    public TransactionType type;
    /** expenseId or settlementId depending on type. */
    public long id;
    /** The expense title, or "Settlement". */
    public String title;
    public int amountCents;
    public LocalDate date;
    public String createdAt;
    public String note;
    public long createdBy;
    public List<Long> involvedUserIds = new ArrayList<>();
    public List<String> involvedUserNames = new ArrayList<>();

    // Expenses only
    public Category category;
    public SplitMethod splitMethod;
    public long payerId;
    public String payerName;
    /** Participant name to share in cents, in participant order. */
    public Map<String, Integer> sharesByName = new LinkedHashMap<>();

    // Settlements only
    public String settledBy;
    public long payeeId;
    public String payeeName;

    long createdAtMillis() {
        if (createdAt == null) {
            return 0;
        }
        try {
            return OffsetDateTime.parse(createdAt).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            return 0;
        }
    }
}
