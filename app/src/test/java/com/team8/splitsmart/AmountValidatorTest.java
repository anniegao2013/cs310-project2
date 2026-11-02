package com.team8.splitsmart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.team8.splitsmart.expense.AmountValidator;

import org.junit.Test;

public class AmountValidatorTest {
    private final AmountValidator validator = new AmountValidator();

    @Test
    public void parsesValidAmounts() {
        assertEquals(1234, validator.parseCents("12.34"));
        assertEquals(1200, validator.parseCents("12"));
        assertEquals(1250, validator.parseCents("12.5"));
        assertEquals(50, validator.parseCents(".50"));
        assertEquals(1, validator.parseCents("0.01"));
    }

    @Test
    public void rejectsInvalidAmounts() {
        assertFalse(validator.isValidAmount("1.234"));
        assertFalse(validator.isValidAmount("0"));
        assertFalse(validator.isValidAmount("0.00"));
        assertFalse(validator.isValidAmount("-5"));
        assertFalse(validator.isValidAmount("abc"));
        assertFalse(validator.isValidAmount(""));
        assertFalse(validator.isValidAmount(null));
        assertFalse(validator.isValidAmount("1000000.01"));
        assertTrue(validator.isValidAmount("1000000.00"));
    }

    @Test(expected = ValidationException.class)
    public void parseCentsThrowsForZero() {
        validator.parseCents("0");
    }

    @Test
    public void formatsMoney() {
        assertEquals("$10.00", Money.format(1000));
        assertEquals("$1,234.05", Money.format(123405));
        assertEquals("$0.07", Money.format(7));
        assertEquals("3.34", Money.toInput(334));
    }
}
