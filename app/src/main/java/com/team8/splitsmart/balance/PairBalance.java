package com.team8.splitsmart.balance;

/** Net balance between the current user and one other member. */
public class PairBalance {
    public final long otherUserId;
    public final String otherUserName;
    /** Positive: they owe you. Negative: you owe them. */
    public final int netCents;

    public PairBalance(long otherUserId, String otherUserName, int netCents) {
        this.otherUserId = otherUserId;
        this.otherUserName = otherUserName;
        this.netCents = netCents;
    }
}
