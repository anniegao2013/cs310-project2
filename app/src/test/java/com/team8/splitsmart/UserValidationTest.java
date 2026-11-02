package com.team8.splitsmart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.team8.splitsmart.usergroup.EmailValidator;
import com.team8.splitsmart.usergroup.PasswordHasher;

import org.junit.Test;

public class UserValidationTest {
    private final EmailValidator validator = new EmailValidator();

    @Test
    public void acceptsUscEmailsOnly() {
        assertTrue(validator.isUscEmail("tommy@usc.edu"));
        assertTrue(validator.isUscEmail("Tommy.Trojan@USC.edu"));
        assertFalse(validator.isUscEmail("tommy@gmail.com"));
        assertFalse(validator.isUscEmail("tommy@usc.edu.com"));
        assertFalse(validator.isUscEmail("@usc.edu"));
        assertFalse(validator.isUscEmail(null));
    }

    @Test
    public void uscIdMustBeTenDigits() {
        assertTrue(validator.isValidUscId("1234567890"));
        assertFalse(validator.isValidUscId("123456789"));
        assertFalse(validator.isValidUscId("12345678901"));
        assertFalse(validator.isValidUscId("12345abcde"));
        assertFalse(validator.isValidUscId(null));
    }

    @Test
    public void hashesAreSaltedAndVerify() {
        PasswordHasher hasher = new PasswordHasher(1000, 16);
        String first = hasher.hash("fighton");
        String second = hasher.hash("fighton");
        assertNotEquals(first, second);
        assertFalse(first.contains("fighton"));
        assertTrue(hasher.verify("fighton", first));
        assertFalse(hasher.verify("wrong", first));
    }
}
