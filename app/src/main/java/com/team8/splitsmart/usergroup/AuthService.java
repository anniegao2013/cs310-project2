package com.team8.splitsmart.usergroup;

import com.team8.splitsmart.ValidationException;
import com.team8.splitsmart.data.DataException;
import com.team8.splitsmart.data.User;
import com.team8.splitsmart.data.UserRepository;

import java.util.Locale;

/** Registers users, logs in and out, and holds the logged-in user. */
public class AuthService {
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final UserRepository userRepo;
    private final EmailValidator emailValidator;
    private final PasswordHasher hasher;
    private volatile User currentUser;

    public AuthService(UserRepository userRepo, EmailValidator emailValidator, PasswordHasher hasher) {
        this.userRepo = userRepo;
        this.emailValidator = emailValidator;
        this.hasher = hasher;
    }

    public User register(String name, String email, String uscId, String password) {
        String cleanName = name == null ? "" : name.trim();
        String cleanEmail = normalizeEmail(email);
        String cleanUscId = uscId == null ? "" : uscId.trim();
        if (cleanName.isEmpty()) {
            throw new ValidationException("Enter your name");
        }
        if (!emailValidator.isUscEmail(cleanEmail)) {
            throw new ValidationException("Email must be a USC address ending in @usc.edu");
        }
        if (!emailValidator.isValidUscId(cleanUscId)) {
            throw new ValidationException("USC ID must be exactly 10 digits");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (userRepo.existsByEmail(cleanEmail)) {
            throw new ValidationException("An account with this email already exists");
        }
        if (userRepo.existsByUscId(cleanUscId)) {
            throw new ValidationException("An account with this USC ID already exists");
        }
        User user;
        try {
            user = userRepo.save(new User(cleanName, cleanEmail, cleanUscId, hasher.hash(password)));
        } catch (DataException.UniqueViolation e) {
            throw new ValidationException("An account with this email or USC ID already exists");
        }
        currentUser = user;
        return user;
    }

    public User login(String email, String password) {
        User user = userRepo.findByEmail(normalizeEmail(email));
        if (user == null || !hasher.verify(password, user.passwordHash)) {
            throw new ValidationException("Incorrect email or password");
        }
        currentUser = user;
        return user;
    }

    public void logout() {
        currentUser = null;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    /** Only the display name can change; email and USC ID are fixed after registration. */
    public User updateName(String name) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty()) {
            throw new ValidationException("Name cannot be empty");
        }
        User user = currentUser;
        if (user == null) {
            throw new ValidationException("Please log in again");
        }
        User updated = userRepo.updateName(user.userId, cleanName);
        currentUser = updated;
        return updated;
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.US);
    }
}
