package com.team8.splitsmart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.team8.splitsmart.balance.BalanceCalculator;
import com.team8.splitsmart.data.DataException;
import com.team8.splitsmart.data.Expense;
import com.team8.splitsmart.data.ExpenseSplit;
import com.team8.splitsmart.data.Settlement;
import com.team8.splitsmart.data.SplitMethod;
import com.team8.splitsmart.data.User;
import com.team8.splitsmart.data.UserRepository;
import com.team8.splitsmart.split.SplitCalculator;
import com.team8.splitsmart.split.SplitResult;
import com.team8.splitsmart.usergroup.AuthService;
import com.team8.splitsmart.usergroup.EmailValidator;
import com.team8.splitsmart.usergroup.PasswordHasher;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * White-box tests written from the source code. Each test targets specific branches
 * (named in its comment) that the other unit tests don't reach.
 */
public class WhiteBoxTest {
    private static final long ALICE = 1, BOB = 2, CAROL = 3;

    /** In-memory stand-in for the Supabase-backed repository. */
    private static class FakeUserRepository extends UserRepository {
        boolean emailTaken;
        boolean uscIdTaken;
        boolean failSaveWithUniqueViolation;
        User saved;

        FakeUserRepository() {
            super(null);
        }

        @Override
        public boolean existsByEmail(String email) {
            return emailTaken;
        }

        @Override
        public boolean existsByUscId(String uscId) {
            return uscIdTaken;
        }

        @Override
        public User save(User user) {
            if (failSaveWithUniqueViolation) {
                throw new DataException.UniqueViolation("duplicate key value violates unique constraint");
            }
            user.userId = 42;
            saved = user;
            return user;
        }
    }

    private static AuthService auth(FakeUserRepository repo) {
        // 1 iteration keeps hashing fast; the hasher itself is covered in UserValidationTest.
        return new AuthService(repo, new EmailValidator(), new PasswordHasher(1, 4));
    }

    /**
     * AuthService.register: both outcomes of {@code password.length() < MIN_PASSWORD_LENGTH},
     * at the boundary (5 is rejected, 6 is accepted), and the success path that sets the current user.
     */
    @Test
    public void registerPasswordLengthBoundary() {
        FakeUserRepository repo = new FakeUserRepository();
        AuthService service = auth(repo);

        ValidationException e = assertThrows(ValidationException.class,
                () -> service.register("Tommy", "tommy@usc.edu", "1234567890", "12345"));
        assertEquals("Password must be at least 6 characters", e.getMessage());
        assertEquals(null, repo.saved);

        User user = service.register("  Tommy  ", "  Tommy@USC.edu ", " 1234567890 ", "123456");
        assertEquals("Tommy", user.name);
        assertEquals("tommy@usc.edu", user.email);
        assertEquals("1234567890", user.uscId);
        assertSame(user, service.getCurrentUser());
    }

    /**
     * AuthService.register: the duplicate-email branch, the duplicate-USC-ID branch, and the
     * catch block for a UniqueViolation thrown by the database (two people registering at once).
     */
    @Test
    public void registerDuplicateAccountBranches() {
        FakeUserRepository repo = new FakeUserRepository();
        AuthService service = auth(repo);

        repo.emailTaken = true;
        assertEquals("An account with this email already exists",
                assertThrows(ValidationException.class,
                        () -> service.register("Tommy", "tommy@usc.edu", "1234567890", "secret1")).getMessage());

        repo.emailTaken = false;
        repo.uscIdTaken = true;
        assertEquals("An account with this USC ID already exists",
                assertThrows(ValidationException.class,
                        () -> service.register("Tommy", "tommy@usc.edu", "1234567890", "secret1")).getMessage());

        repo.uscIdTaken = false;
        repo.failSaveWithUniqueViolation = true;
        assertEquals("An account with this email or USC ID already exists",
                assertThrows(ValidationException.class,
                        () -> service.register("Tommy", "tommy@usc.edu", "1234567890", "secret1")).getMessage());
        assertEquals(null, service.getCurrentUser());
    }

    /**
     * PercentageSplitStrategy: the tie-breaking side of the remainder comparator
     * ({@code b[1] == a[1]}, so the lower participant index wins). Splitting 1 cent 50/50 leaves
     * both participants with the same remainder, so the extra cent must go to the first one.
     */
    @Test
    public void percentageSplitTieGoesToFirstParticipant() {
        SplitCalculator calculator = new SplitCalculator();
        Map<Long, String> inputs = new HashMap<>();
        inputs.put(ALICE, "50");
        inputs.put(BOB, "50");

        SplitResult result = calculator.preview(SplitMethod.PERCENTAGE, 1, Arrays.asList(ALICE, BOB), inputs);

        assertTrue(result.valid);
        assertEquals(Integer.valueOf(1), result.sharesCents.get(ALICE));
        assertEquals(Integer.valueOf(0), result.sharesCents.get(BOB));
        assertTrue(calculator.verify(1, result));
    }

    /**
     * PercentageSplitStrategy.toBasisPoints: the {@code points > FULL} branch (a single value over
     * 100% is rejected as invalid input, not reported as a wrong total), the regex-mismatch branch
     * (more than two decimals), the null-input branch, and the "%" stripping on the valid path.
     */
    @Test
    public void percentageInputOverOneHundredIsInvalidInput() {
        SplitCalculator calculator = new SplitCalculator();
        List<Long> people = Arrays.asList(ALICE, BOB);

        Map<Long, String> over = new HashMap<>();
        over.put(ALICE, "100.01");
        over.put(BOB, "0");
        SplitResult overResult = calculator.preview(SplitMethod.PERCENTAGE, 1000, people, over);
        assertFalse(overResult.valid);
        assertEquals("Enter a percentage (up to two decimals) for every participant", overResult.errorMessage);

        Map<Long, String> tooPrecise = new HashMap<>();
        tooPrecise.put(ALICE, "33.333");
        tooPrecise.put(BOB, "66.667");
        assertFalse(calculator.preview(SplitMethod.PERCENTAGE, 1000, people, tooPrecise).valid);

        Map<Long, String> missing = new HashMap<>();
        missing.put(ALICE, "100");
        SplitResult missingResult = calculator.preview(SplitMethod.PERCENTAGE, 1000, people, missing);
        assertFalse(missingResult.valid);
        assertFalse(calculator.verify(1000, missingResult));

        Map<Long, String> withSign = new HashMap<>();
        withSign.put(ALICE, "100%");
        withSign.put(BOB, "0%");
        SplitResult signResult = calculator.preview(SplitMethod.PERCENTAGE, 1000, people, withSign);
        assertTrue(signResult.valid);
        assertEquals(Integer.valueOf(1000), signResult.sharesCents.get(ALICE));
    }

    /**
     * BalanceCalculator.computePairwise: the branches where the user is not involved
     * (an expense between two other members, a settlement between two other members) and the
     * branch where the user is the payee of a settlement.
     */
    @Test
    public void pairwiseIgnoresOtherPairsAndHandlesUserAsPayee() {
        BalanceCalculator calculator = new BalanceCalculator();

        Expense bobPaysForCarol = new Expense();
        bobPaysForCarol.payerId = BOB;
        bobPaysForCarol.amountCents = 2000;
        bobPaysForCarol.splits = Arrays.asList(
                new ExpenseSplit(BOB, 1000, null),
                new ExpenseSplit(CAROL, 1000, null));

        Expense alicePaysForBob = new Expense();
        alicePaysForBob.payerId = ALICE;
        alicePaysForBob.amountCents = 3000;
        alicePaysForBob.splits = Arrays.asList(
                new ExpenseSplit(ALICE, 1500, null),
                new ExpenseSplit(BOB, 1500, null));

        Settlement bobPaysAlice = new Settlement();
        bobPaysAlice.payerId = BOB;
        bobPaysAlice.payeeId = ALICE;
        bobPaysAlice.amountCents = 500;

        Settlement carolPaysBob = new Settlement();
        carolPaysBob.payerId = CAROL;
        carolPaysBob.payeeId = BOB;
        carolPaysBob.amountCents = 1000;

        Map<Long, Integer> net = calculator.computePairwise(ALICE,
                Arrays.asList(bobPaysForCarol, alicePaysForBob),
                Arrays.asList(bobPaysAlice, carolPaysBob));

        assertEquals(Integer.valueOf(1000), net.get(BOB)); // 1500 owed - 500 paid back
        assertFalse(net.containsKey(CAROL));
        assertEquals(1, net.size());
        assertNotNull(calculator.computePairwise(ALICE, Collections.emptyList(), Collections.emptyList()));
    }
}
