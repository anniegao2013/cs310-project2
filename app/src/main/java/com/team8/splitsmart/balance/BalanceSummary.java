package com.team8.splitsmart.balance;

import java.util.List;

public class BalanceSummary {
    public final int totalToPayCents;
    public final int totalToReceiveCents;
    public final List<PairBalance> pairs;

    public BalanceSummary(int totalToPayCents, int totalToReceiveCents, List<PairBalance> pairs) {
        this.totalToPayCents = totalToPayCents;
        this.totalToReceiveCents = totalToReceiveCents;
        this.pairs = pairs;
    }
}
