package com.team8.splitsmart.data;

public enum Category {
    FOOD("Food"),
    HOUSING("Housing"),
    TRANSPORTATION("Transportation"),
    ENTERTAINMENT("Entertainment"),
    OTHER("Other");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
