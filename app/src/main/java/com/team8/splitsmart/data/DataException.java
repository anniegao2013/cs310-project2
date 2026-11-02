package com.team8.splitsmart.data;

/** A failure talking to the central repository (network error or rejected query). */
public class DataException extends RuntimeException {
    public DataException(String message) {
        super(message);
    }

    public DataException(String message, Throwable cause) {
        super(message, cause);
    }

    /** A unique constraint was violated, e.g. a duplicate email or USC ID. */
    public static class UniqueViolation extends DataException {
        public UniqueViolation(String message) {
            super(message);
        }
    }
}
