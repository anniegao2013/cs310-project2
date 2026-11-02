package com.team8.splitsmart.usergroup;

import java.util.regex.Pattern;

public class EmailValidator {
    private static final Pattern USC_EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@usc\\.edu$", Pattern.CASE_INSENSITIVE);
    private static final Pattern USC_ID = Pattern.compile("^[0-9]{10}$");

    public boolean isUscEmail(String email) {
        return email != null && USC_EMAIL.matcher(email.trim()).matches();
    }

    public boolean isValidUscId(String uscId) {
        return uscId != null && USC_ID.matcher(uscId.trim()).matches();
    }
}
