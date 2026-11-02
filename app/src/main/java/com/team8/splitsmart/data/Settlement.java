package com.team8.splitsmart.data;

import java.time.LocalDate;

public class Settlement {
    public long settlementId;
    public long groupId;
    public long payerId;
    public long payeeId;
    public int amountCents;
    public LocalDate date;
    public String note;
    public long createdBy;
    /** Server insertion timestamp, used only to order transactions created on the same day. */
    public String createdAt;
}
