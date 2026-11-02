package com.team8.splitsmart.data;

public enum SplitMethod {
    EQUAL("Equal"),
    EXACT_AMOUNT("Exact amount"),
    PERCENTAGE("Percentage");

    private final String label;

    SplitMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
