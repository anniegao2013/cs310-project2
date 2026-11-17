package com.team8.splitsmart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.team8.splitsmart.balance.BalanceService;
import com.team8.splitsmart.balance.BalanceSummary;
import com.team8.splitsmart.balance.PairBalance;
import com.team8.splitsmart.dashboard.HistoryService;
import com.team8.splitsmart.dashboard.TransactionItem;
import com.team8.splitsmart.data.GroupRepository;
import com.team8.splitsmart.data.Settlement;
import com.team8.splitsmart.data.SettlementRepository;
import com.team8.splitsmart.data.TransactionType;
import com.team8.splitsmart.data.User;
import com.team8.splitsmart.expense.AmountValidator;
import com.team8.splitsmart.settlement.SettlementService;
import com.team8.splitsmart.split.ExactAmountSplitStrategy;
import com.team8.splitsmart.split.SplitResult;
import com.team8.splitsmart.usergroup.PasswordHasher;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * White-box tests WB-16 through WB-20 (author: Angela Zhuang). Each test targets branches that the
 * JaCoCo report showed were never taken by WB-1 through WB-15 or by the other unit tests.
 */
public class CoverageGapWhiteBoxTest {
    private static final long GROUP = 1;
    private static final long ALICE = 1;
    private static final long BOB = 2;

    private static User user(long id) {
        User user = new User();
        user.userId = id;
        return user;
    }

    /** WB-16: the payer == payee guard in recordSettlement, the only branch the suite never took. */
    @Test
    public void recordSettlementRejectsPayingYourself() {
        SettlementService service = new SettlementService(new SettlementRepository(null) {
            @Override
            public Settlement save(Settlement settlement) {
                throw new AssertionError("A self-payment reached save");
            }
        }, new GroupRepository(null) {
            @Override
            public boolean isMember(long groupId, long userId) {
                throw new AssertionError("A self-payment reached the membership check");
            }
        }, new AmountValidator(), new BalanceService(null, null, null, null) {
            @Override
            public BalanceSummary getBalances(long groupId, long userId) {
                throw new AssertionError("A self-payment reached the debt lookup");
            }
        });

        ValidationException error = assertThrows(ValidationException.class,
                () -> service.recordSettlement(GROUP, user(ALICE), ALICE, ALICE, "5.00", null));
        assertEquals("Choose another member", error.getMessage());
    }

    /** WB-17: the item.title == null side of the title check in HistoryService.search. */
    @Test
    public void searchMatchesUntitledExpenseOnlyThroughItsNote() {
        TransactionItem untitledWithNote = item(1, TransactionType.EXPENSE, null, "Dinner at Ramen-ya");
        TransactionItem untitledNoNote = item(2, TransactionType.EXPENSE, null, null);
        TransactionItem titled = item(3, TransactionType.EXPENSE, "Dinner", null);
        TransactionItem settlement = item(4, TransactionType.SETTLEMENT, TransactionItem.SETTLEMENT_LABEL, null);
        List<TransactionItem> items = Arrays.asList(untitledWithNote, untitledNoNote, titled, settlement);
        HistoryService history = new HistoryService(null, null, null);

        // A null title must not throw: the untitled expense is found through its note, the
        // untitled expense with no note is skipped, and order is kept.
        assertEquals(Arrays.asList(untitledWithNote, titled), history.search(items, "Dinner"));
        // A settlement's "Settlement" label is not an expense title, so it never matches by title.
        assertEquals(Collections.emptyList(), history.search(items, "Settlement"));
    }

    private static TransactionItem item(long id, TransactionType type, String title, String note) {
        TransactionItem item = new TransactionItem();
        item.id = id;
        item.type = type;
        item.title = title;
        item.note = note;
        return item;
    }

    /** WB-18: the missing-settlement branch of getSettlement and all three outcomes of blankToNull. */
    @Test
    public void missingSettlementIsRejectedAndNotesAreNormalized() {
        int[] writes = new int[1];
        SettlementService service = new SettlementService(new SettlementRepository(null) {
            @Override
            public Settlement findById(long settlementId) {
                return null; // the settlement was deleted by the other member
            }

            @Override
            public Settlement save(Settlement settlement) {
                return settlement;
            }

            @Override
            public Settlement update(Settlement settlement) {
                writes[0]++;
                return settlement;
            }

            @Override
            public void delete(long settlementId) {
                writes[0]++;
            }
        }, new GroupRepository(null) {
            @Override
            public boolean isMember(long groupId, long userId) {
                return true;
            }
        }, new AmountValidator(), new BalanceService(null, null, null, null) {
            @Override
            public BalanceSummary getBalances(long groupId, long userId) {
                // Alice owes Bob $10.00.
                return new BalanceSummary(0, 0, Arrays.asList(new PairBalance(BOB, "Bob", -1000)));
            }
        });

        String gone = "This settlement no longer exists";
        assertEquals(gone, assertThrows(ValidationException.class,
                () -> service.getSettlement(9)).getMessage());
        assertEquals(gone, assertThrows(ValidationException.class,
                () -> service.updateSettlement(9, user(ALICE), "1.00", null)).getMessage());
        assertEquals(gone, assertThrows(ValidationException.class,
                () -> service.deleteSettlement(9, user(ALICE))).getMessage());
        assertEquals(0, writes[0]);

        assertNull(service.recordSettlement(GROUP, user(ALICE), ALICE, BOB, "1.00", null).note);
        assertNull(service.recordSettlement(GROUP, user(ALICE), ALICE, BOB, "1.00", "   ").note);
        assertEquals("paid in cash",
                service.recordSettlement(GROUP, user(ALICE), ALICE, BOB, "1.00", "  paid in cash  ").note);
    }

    /** WB-19: every early-return branch in PasswordHasher.verify. */
    @Test
    public void verifyRejectsMalformedStoredHashes() {
        PasswordHasher hasher = new PasswordHasher(1000, 16);
        String good = hasher.hash("fighton");
        String[] parts = good.split("\\$");

        assertFalse(hasher.verify(null, good));                                  // password == null
        assertFalse(hasher.verify("fighton", null));                             // storedHash == null
        assertFalse(hasher.verify("fighton", ""));                               // 1 part
        assertFalse(hasher.verify("fighton", "pbkdf2$1000$" + parts[2]));        // 3 parts
        assertFalse(hasher.verify("fighton", good + "$extra"));                  // 5 parts
        assertFalse(hasher.verify("fighton",                                     // wrong prefix
                "sha256$" + parts[1] + "$" + parts[2] + "$" + parts[3]));
        assertTrue(hasher.verify("fighton", good));                              // well formed
    }

    /** WB-20: the $/comma stripping, ".5" form, zero share and $1,000,000.00 limit in toCents. */
    @Test
    public void exactSplitAcceptsFormattedSharesUpToTheMaximum() {
        ExactAmountSplitStrategy strategy = new ExactAmountSplitStrategy();
        List<Long> people = Arrays.asList(ALICE, BOB);

        SplitResult formatted = strategy.calculate(100_100, people, shares("$1,000.50", ".5"));
        assertTrue(formatted.valid);
        assertEquals(100_050, (int) formatted.sharesCents.get(ALICE));
        assertEquals(50, (int) formatted.sharesCents.get(BOB));

        // Exactly the maximum is allowed and a share may be zero.
        SplitResult atMax = strategy.calculate(100_000_000, people, shares("1000000.00", "0"));
        assertTrue(atMax.valid);
        assertEquals(0, (int) atMax.sharesCents.get(BOB));

        // One cent over the maximum is rejected before the sum is checked.
        SplitResult overMax = strategy.calculate(100_000_001, people, shares("1000000.01", "0"));
        assertFalse(overMax.valid);
        assertEquals("Enter a valid amount (up to two decimals) for every participant", overMax.errorMessage);
        assertTrue(overMax.sharesCents.isEmpty());

        // A participant with no input at all is rejected.
        Map<Long, String> missing = new HashMap<>();
        missing.put(ALICE, "5.00");
        assertFalse(strategy.calculate(500, people, missing).valid);
    }

    private static Map<Long, String> shares(String alice, String bob) {
        Map<Long, String> inputs = new HashMap<>();
        inputs.put(ALICE, alice);
        inputs.put(BOB, bob);
        return inputs;
    }
}
