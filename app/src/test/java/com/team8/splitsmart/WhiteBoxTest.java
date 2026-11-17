package com.team8.splitsmart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.team8.splitsmart.balance.BalanceCalculator;
import com.team8.splitsmart.data.Category;
import com.team8.splitsmart.data.DataException;
import com.team8.splitsmart.data.Expense;
import com.team8.splitsmart.data.ExpenseRepository;
import com.team8.splitsmart.data.ExpenseSplit;
import com.team8.splitsmart.data.Group;
import com.team8.splitsmart.data.GroupRepository;
import com.team8.splitsmart.data.Settlement;
import com.team8.splitsmart.data.SplitMethod;
import com.team8.splitsmart.data.User;
import com.team8.splitsmart.data.UserRepository;
import com.team8.splitsmart.expense.AmountValidator;
import com.team8.splitsmart.expense.ExpenseDraft;
import com.team8.splitsmart.expense.ExpenseService;
import com.team8.splitsmart.split.SplitCalculator;
import com.team8.splitsmart.split.SplitResult;
import com.team8.splitsmart.usergroup.AuthService;
import com.team8.splitsmart.usergroup.EmailValidator;
import com.team8.splitsmart.usergroup.GroupService;
import com.team8.splitsmart.usergroup.PasswordHasher;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
        int registrationRepositoryCalls;
        boolean emailTaken;
        boolean uscIdTaken;
        boolean failSaveWithUniqueViolation;
        User saved;
        final Map<String, User> users = new HashMap<>();
        String lastFindByEmail;
        String lastUpdatedName;
        long lastUpdatedUserId;

        FakeUserRepository() {
            super(null);
        }

        @Override
        public boolean existsByEmail(String email) {
            registrationRepositoryCalls++;
            return emailTaken;
        }

        @Override
        public boolean existsByUscId(String uscId) {
            registrationRepositoryCalls++;
            return uscIdTaken;
        }

        @Override
        public User save(User user) {
            registrationRepositoryCalls++;
            if (failSaveWithUniqueViolation) {
                throw new DataException.UniqueViolation("duplicate key value violates unique constraint");
            }
            user.userId = 42;
            saved = user;
            return user;
        }

        @Override
        public User findByEmail(String email) {
            lastFindByEmail = email;
            return users.get(email);
        }

        @Override
        public User updateName(long userId, String name) {
            lastUpdatedUserId = userId;
            lastUpdatedName = name;
            User updated = new User();
            updated.userId = userId;
            updated.name = name;
            return updated;
        }
    }

    /** In-memory group repository used by the group and expense tests. */
    private static class FakeGroupRepository extends GroupRepository {
        Group group;
        final List<User> members = new ArrayList<>();
        boolean deleted;

        FakeGroupRepository() {
            super(null);
        }

        @Override
        public Group save(Group group) {
            group.groupId = 10;
            this.group = group;
            return group;
        }

        @Override
        public Group update(Group group) {
            this.group = group;
            return group;
        }

        @Override
        public Group findById(long groupId) {
            return group != null && group.groupId == groupId ? group : null;
        }

        @Override
        public void addMember(long groupId, long userId) {
            User member = new User();
            member.userId = userId;
            members.add(member);
        }

        @Override
        public List<User> getMembers(long groupId) {
            return new ArrayList<>(members);
        }

        @Override
        public boolean isMember(long groupId, long userId) {
            for (User member : members) {
                if (member.userId == userId) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void delete(long groupId) {
            deleted = true;
            group = null;
            members.clear();
        }
    }

    /** In-memory expense repository that records what the service saved. */
    private static class FakeExpenseRepository extends ExpenseRepository {
        Expense stored;
        List<ExpenseSplit> storedSplits;
        long deletedId = -1;

        FakeExpenseRepository() {
            super(null);
        }

        @Override
        public Expense saveWithSplits(Expense expense, List<ExpenseSplit> splits) {
            expense.expenseId = 55;
            stored = expense;
            storedSplits = new ArrayList<>(splits);
            return expense;
        }

        @Override
        public void updateWithSplits(Expense expense, List<ExpenseSplit> splits) {
            stored = expense;
            storedSplits = new ArrayList<>(splits);
        }

        @Override
        public Expense findById(long expenseId) {
            return stored != null && stored.expenseId == expenseId ? stored : null;
        }

        @Override
        public void delete(long expenseId) {
            deletedId = expenseId;
            stored = null;
        }
    }

    private static AuthService auth(FakeUserRepository repo) {
        // 1 iteration keeps hashing fast; the hasher itself is covered in UserValidationTest.
        return new AuthService(repo, new EmailValidator(), new PasswordHasher(1, 4));
    }

    private static User user(long id, String name) {
        User user = new User();
        user.userId = id;
        user.name = name;
        return user;
    }

    private static ExpenseDraft draft(long groupId, String title, String amount, Category category,
                                      long payerId, Long... participantIds) {
        ExpenseDraft draft = new ExpenseDraft();
        draft.groupId = groupId;
        draft.title = title;
        draft.amountText = amount;
        draft.category = category;
        draft.payerId = payerId;
        draft.participantIds.addAll(Arrays.asList(participantIds));
        return draft;
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

    /**
     * AuthService.login: the unknown-user branch, the wrong-password branch, the success path with
     * a mixed-case/padded email, the null email/password paths, and logout clearing the session.
     */
    @Test
    public void loginRejectsBadCredentialsAndNormalizesEmail() {
        FakeUserRepository repo = new FakeUserRepository();
        PasswordHasher hasher = new PasswordHasher(1, 4);
        User tommy = new User("Tommy", "tommy@usc.edu", "1234567890", hasher.hash("fighton"));
        tommy.userId = 7;
        repo.users.put("tommy@usc.edu", tommy);
        AuthService service = new AuthService(repo, new EmailValidator(), hasher);

        assertEquals("Incorrect email or password", assertThrows(ValidationException.class,
                () -> service.login("nobody@usc.edu", "fighton")).getMessage());
        assertEquals(null, service.getCurrentUser());

        assertEquals("Incorrect email or password", assertThrows(ValidationException.class,
                () -> service.login("tommy@usc.edu", "wrong")).getMessage());
        assertEquals(null, service.getCurrentUser());

        assertEquals("Incorrect email or password", assertThrows(ValidationException.class,
                () -> service.login(null, "fighton")).getMessage());
        assertEquals("", repo.lastFindByEmail);

        assertEquals("Incorrect email or password", assertThrows(ValidationException.class,
                () -> service.login("tommy@usc.edu", null)).getMessage());

        User loggedIn = service.login("  Tommy@USC.edu ", "fighton");
        assertSame(tommy, loggedIn);
        assertSame(tommy, service.getCurrentUser());
        assertEquals("tommy@usc.edu", repo.lastFindByEmail);

        service.logout();
        assertEquals(null, service.getCurrentUser());
    }

    /**
     * AuthService.updateName: the logged-out branch, both empty-name inputs, and the success path
     * that trims the name and replaces the cached current user.
     */
    @Test
    public void updateNameRequiresLoginAndTrimsName() {
        FakeUserRepository repo = new FakeUserRepository();
        AuthService service = auth(repo);

        assertEquals("Please log in again", assertThrows(ValidationException.class,
                () -> service.updateName("Tommy")).getMessage());

        service.register("Tommy", "tommy@usc.edu", "1234567890", "secret1");
        assertEquals("Name cannot be empty", assertThrows(ValidationException.class,
                () -> service.updateName("   ")).getMessage());
        assertEquals("Name cannot be empty", assertThrows(ValidationException.class,
                () -> service.updateName(null)).getMessage());

        User updated = service.updateName("  Tommy Trojan  ");
        assertEquals("Tommy Trojan", updated.name);
        assertEquals("Tommy Trojan", repo.lastUpdatedName);
        assertEquals(42L, repo.lastUpdatedUserId);
        assertSame(updated, service.getCurrentUser());
    }

    /**
     * GroupService: create-group name/description cleaning, add-member null/blank/unknown/duplicate
     * branches, owner-only checks, and the missing-group branch.
     */
    @Test
    public void groupCreationAndMembershipValidationBranches() {
        FakeGroupRepository groupRepo = new FakeGroupRepository();
        FakeUserRepository userRepo = new FakeUserRepository();
        GroupService service = new GroupService(groupRepo, userRepo);

        User owner = user(ALICE, "Alice");
        User bob = user(BOB, "Bob");
        userRepo.users.put("bob@usc.edu", bob);

        assertEquals("Group name cannot be empty", assertThrows(ValidationException.class,
                () -> service.createGroup("   ", "description", owner)).getMessage());
        assertEquals("Group name cannot be empty", assertThrows(ValidationException.class,
                () -> service.createGroup(null, null, owner)).getMessage());

        Group blankDescription = service.createGroup("Blank description", "   ", owner);
        assertEquals(null, blankDescription.description);

        Group group = service.createGroup("  Roommates  ", null, owner);
        assertEquals("Roommates", group.name);
        assertEquals(null, group.description);
        assertEquals(ALICE, group.ownerId);
        assertEquals(LocalDate.now(), group.createdDate);

        // create_group_with_owner adds the owner as the first member in production.
        groupRepo.members.add(owner);

        assertEquals("Enter the member's USC email", assertThrows(ValidationException.class,
                () -> service.addMember(group.groupId, owner, null)).getMessage());
        assertEquals("Enter the member's USC email", assertThrows(ValidationException.class,
                () -> service.addMember(group.groupId, owner, "   ")).getMessage());
        assertEquals("No registered user has the email ghost@usc.edu", assertThrows(ValidationException.class,
                () -> service.addMember(group.groupId, owner, " GHOST@USC.EDU ")).getMessage());
        assertEquals("ghost@usc.edu", userRepo.lastFindByEmail);
        assertEquals("Only the group owner can do this", assertThrows(ValidationException.class,
                () -> service.addMember(group.groupId, bob, "bob@usc.edu")).getMessage());

        List<User> members = service.addMember(group.groupId, owner, " BOB@USC.EDU ");
        assertEquals(2, members.size());
        assertTrue(groupRepo.isMember(group.groupId, BOB));

        assertEquals("Bob is already in this group", assertThrows(ValidationException.class,
                () -> service.addMember(group.groupId, owner, "bob@usc.edu")).getMessage());
        assertEquals("This group no longer exists", assertThrows(ValidationException.class,
                () -> service.getGroup(999)).getMessage());

        Group edited = service.editGroup(group.groupId, owner, "  New Name  ", "  New description  ");
        assertEquals("New Name", edited.name);
        assertEquals("New description", edited.description);

        assertEquals("Only the group owner can do this", assertThrows(ValidationException.class,
                () -> service.deleteGroup(group.groupId, bob)).getMessage());
        service.deleteGroup(group.groupId, owner);
        assertTrue(groupRepo.deleted);
    }

    /**
     * ExpenseService.validateDraft: the title, amount, category, payer-participant, participant-count,
     * and group-membership rules, including the valid path returning the parsed total.
     */
    @Test
    public void expenseDraftValidationRejectsEachRule() {
        FakeGroupRepository groupRepo = new FakeGroupRepository();
        groupRepo.members.add(user(ALICE, "Alice"));
        groupRepo.members.add(user(BOB, "Bob"));
        ExpenseService service = new ExpenseService(new FakeExpenseRepository(), groupRepo,
                new AmountValidator(), new SplitCalculator());
        long groupId = 10;

        assertEquals("Enter a title", assertThrows(ValidationException.class,
                () -> service.validateDraft(draft(groupId, null, "10.00", Category.FOOD, ALICE, ALICE, BOB))).getMessage());
        assertEquals("Enter a title", assertThrows(ValidationException.class,
                () -> service.validateDraft(draft(groupId, "   ", "10.00", Category.FOOD, ALICE, ALICE, BOB))).getMessage());
        assertEquals("Amount must be greater than $0.00", assertThrows(ValidationException.class,
                () -> service.validateDraft(draft(groupId, "Dinner", "0", Category.FOOD, ALICE, ALICE, BOB))).getMessage());
        assertEquals("Select a category", assertThrows(ValidationException.class,
                () -> service.validateDraft(draft(groupId, "Dinner", "10.00", null, ALICE, ALICE, BOB))).getMessage());
        assertEquals("The payer must be one of the participants", assertThrows(ValidationException.class,
                () -> service.validateDraft(draft(groupId, "Dinner", "10.00", Category.FOOD, ALICE, BOB))).getMessage());
        assertEquals("Select at least one participant besides the payer", assertThrows(ValidationException.class,
                () -> service.validateDraft(draft(groupId, "Dinner", "10.00", Category.FOOD, ALICE, ALICE))).getMessage());
        assertEquals("The payer and all participants must be group members", assertThrows(ValidationException.class,
                () -> service.validateDraft(draft(groupId, "Dinner", "10.00", Category.FOOD, ALICE, ALICE, CAROL))).getMessage());

        assertEquals(1234, service.validateDraft(
                draft(groupId, "Dinner", "12.34", Category.FOOD, ALICE, ALICE, BOB)));
    }

    /**
     * ExpenseService.createExpense/updateExpense/deleteExpense: creator membership, null/invalid
     * splits, participant-key and sum checks, note normalization, saved splits, and creator-only
     * update/delete permissions.
     */
    @Test
    public void expenseCreationVerifiesSplitAndEnforcesCreator() {
        FakeGroupRepository groupRepo = new FakeGroupRepository();
        User alice = user(ALICE, "Alice");
        User bob = user(BOB, "Bob");
        User carol = user(CAROL, "Carol");
        groupRepo.members.add(alice);
        groupRepo.members.add(bob);
        FakeExpenseRepository expenseRepo = new FakeExpenseRepository();
        SplitCalculator calculator = new SplitCalculator();
        ExpenseService service = new ExpenseService(expenseRepo, groupRepo, new AmountValidator(), calculator);
        long groupId = 10;

        ExpenseDraft draft = draft(groupId, " Dinner ", "10.00", Category.FOOD, ALICE, ALICE, BOB);
        draft.note = "   ";
        SplitResult valid = calculator.preview(SplitMethod.EQUAL, 1000,
                Arrays.asList(ALICE, BOB), new HashMap<>());

        assertEquals("Only group members can add expenses", assertThrows(ValidationException.class,
                () -> service.createExpense(draft, valid, carol)).getMessage());
        assertEquals("Choose a split method", assertThrows(ValidationException.class,
                () -> service.createExpense(draft, null, alice)).getMessage());

        SplitResult invalid = new SplitResult(SplitMethod.EQUAL, new LinkedHashMap<>(),
                new LinkedHashMap<>(), false, "bad split");
        assertEquals("bad split", assertThrows(ValidationException.class,
                () -> service.createExpense(draft, invalid, alice)).getMessage());

        Map<Long, Integer> onlyAlice = new LinkedHashMap<>();
        onlyAlice.put(ALICE, 1000);
        SplitResult mismatch = new SplitResult(SplitMethod.EQUAL, onlyAlice, new LinkedHashMap<>(), true, null);
        assertEquals("The split does not match the selected participants", assertThrows(ValidationException.class,
                () -> service.createExpense(draft, mismatch, alice)).getMessage());

        Map<Long, Integer> wrongTotal = new LinkedHashMap<>();
        wrongTotal.put(ALICE, 400);
        wrongTotal.put(BOB, 400);
        SplitResult sumMismatch = new SplitResult(SplitMethod.EQUAL, wrongTotal, new LinkedHashMap<>(), true, null);
        assertEquals("Shares must add up to the total amount", assertThrows(ValidationException.class,
                () -> service.createExpense(draft, sumMismatch, alice)).getMessage());

        Expense saved = service.createExpense(draft, valid, alice);
        assertEquals(55L, saved.expenseId);
        assertEquals("Dinner", saved.title);
        assertEquals(1000, saved.amountCents);
        assertEquals(Category.FOOD, saved.category);
        assertEquals(ALICE, saved.payerId);
        assertEquals(ALICE, saved.createdBy);
        assertEquals(SplitMethod.EQUAL, saved.splitMethod);
        assertEquals(LocalDate.now(), saved.createdDate);
        assertEquals(null, saved.note);
        assertEquals(2, expenseRepo.storedSplits.size());

        ExpenseDraft update = draft(groupId, "  Dinner 2  ", "20.00", Category.HOUSING, ALICE, ALICE, BOB);
        update.note = "  thanks  ";
        SplitResult updateSplit = calculator.preview(SplitMethod.EQUAL, 2000,
                Arrays.asList(ALICE, BOB), new HashMap<>());

        assertEquals("Only the person who created this expense can change it",
                assertThrows(ValidationException.class,
                        () -> service.updateExpense(55, update, updateSplit, bob)).getMessage());
        assertEquals("This expense no longer exists", assertThrows(ValidationException.class,
                () -> service.getExpense(999)).getMessage());

        Expense updated = service.updateExpense(55, update, updateSplit, alice);
        assertEquals("Dinner 2", updated.title);
        assertEquals(2000, updated.amountCents);
        assertEquals(Category.HOUSING, updated.category);
        assertEquals("thanks", updated.note);
        assertEquals(ALICE, updated.createdBy);
        assertEquals(LocalDate.now(), updated.createdDate);

        assertEquals("Only the person who created this expense can change it",
                assertThrows(ValidationException.class,
                        () -> service.deleteExpense(55, bob)).getMessage());
        service.deleteExpense(55, alice);
        assertEquals(55L, expenseRepo.deletedId);
    }

    /** Covers the null-input paths and verifies validation precedes repository access. */
    @Test
    public void registerRejectsMissingRequiredFieldsBeforeRepositoryLookup() {
        FakeUserRepository repo = new FakeUserRepository();
        AuthService service = auth(repo);
        String name = "Alice";
        String email = "alice@usc.edu";
        String uscId = "1234567890";
        String password = "fighton";

        assertEquals("Enter your name", assertThrows(ValidationException.class,
                () -> service.register(null, email, uscId, password)).getMessage());
        assertEquals("Email must be a USC address ending in @usc.edu", assertThrows(ValidationException.class,
                () -> service.register(name, null, uscId, password)).getMessage());
        assertEquals("USC ID must be exactly 10 digits", assertThrows(ValidationException.class,
                () -> service.register(name, email, null, password)).getMessage());
        assertEquals("Password must be at least 6 characters", assertThrows(ValidationException.class,
                () -> service.register(name, email, uscId, null)).getMessage());

        assertEquals(0, repo.registrationRepositoryCalls);
        assertNull(service.getCurrentUser());
    }

    /** Covers the null side of the short-circuit note check in applyDraft. */
    @Test
    public void createExpenseWithNullNoteSavesNull() {
        User alice = user(ALICE, "Alice");
        FakeGroupRepository groupRepo = new FakeGroupRepository();
        groupRepo.members.add(alice);
        groupRepo.members.add(user(BOB, "Bob"));
        FakeExpenseRepository expenseRepo = new FakeExpenseRepository();
        SplitCalculator calculator = new SplitCalculator();
        ExpenseService service = new ExpenseService(expenseRepo, groupRepo, new AmountValidator(), calculator);
        ExpenseDraft draft = draft(10, "Dinner", "10.00", Category.FOOD, ALICE, ALICE, BOB);
        draft.note = null;
        SplitResult split = calculator.preview(SplitMethod.EQUAL, 1000,
                draft.participantIds, Collections.emptyMap());

        Expense saved = service.createExpense(draft, split, alice);

        assertNotNull(expenseRepo.stored);
        assertSame(saved, expenseRepo.stored);
        assertNull(expenseRepo.stored.note);
    }
}
