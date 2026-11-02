package com.team8.splitsmart.split;

import com.team8.splitsmart.data.SplitMethod;

import java.util.Map;

/** Per-participant shares (in participant order), whether they are valid, and why not. */
public class SplitResult {
    public final SplitMethod method;
    public final Map<Long, Integer> sharesCents;
    /** The raw input per participant (exact amount or percentage text); empty for equal splits. */
    public final Map<Long, String> inputs;
    public final boolean valid;
    public final String errorMessage;

    public SplitResult(SplitMethod method, Map<Long, Integer> sharesCents, Map<Long, String> inputs,
                       boolean valid, String errorMessage) {
        this.method = method;
        this.sharesCents = sharesCents;
        this.inputs = inputs;
        this.valid = valid;
        this.errorMessage = errorMessage;
    }
}
