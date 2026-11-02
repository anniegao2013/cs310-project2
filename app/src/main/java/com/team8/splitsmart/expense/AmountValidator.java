package com.team8.splitsmart.expense;

import com.team8.splitsmart.ValidationException;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Parses typed dollar amounts into integer cents. */
public class AmountValidator {
    private static final Pattern AMOUNT = Pattern.compile("^\\d+(\\.\\d{0,2})?$|^\\.\\d{1,2}$");
    /** $1,000,000.00 keeps every sum of shares well inside an int. */
    public static final int MAX_CENTS = 100_000_000;

    /** Parses an amount that must be greater than zero with at most two decimal places. */
    public int parseCents(String amountText) {
        String text = amountText == null ? "" : amountText.trim().replace("$", "").replace(",", "");
        if (text.isEmpty()) {
            throw new ValidationException("Enter an amount");
        }
        if (!AMOUNT.matcher(text).matches()) {
            throw new ValidationException("Enter a valid dollar amount with at most two decimal places");
        }
        BigDecimal cents = new BigDecimal(text).movePointRight(2);
        if (cents.compareTo(BigDecimal.valueOf(MAX_CENTS)) > 0) {
            throw new ValidationException("Amount cannot exceed $1,000,000.00");
        }
        if (cents.signum() == 0) {
            throw new ValidationException("Amount must be greater than $0.00");
        }
        return cents.intValueExact();
    }

    public boolean isValidAmount(String amountText) {
        try {
            parseCents(amountText);
            return true;
        } catch (ValidationException e) {
            return false;
        }
    }
}
