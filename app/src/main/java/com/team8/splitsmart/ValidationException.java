package com.team8.splitsmart;

/** Thrown when user input or a business rule is violated; the message is shown to the user. */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
