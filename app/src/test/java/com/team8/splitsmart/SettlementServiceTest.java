package com.team8.splitsmart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.team8.splitsmart.balance.BalanceService;
import com.team8.splitsmart.balance.BalanceSummary;
import com.team8.splitsmart.balance.PairBalance;
import com.team8.splitsmart.data.GroupRepository;
import com.team8.splitsmart.data.Settlement;
import com.team8.splitsmart.data.SettlementRepository;
import com.team8.splitsmart.data.User;
import com.team8.splitsmart.expense.AmountValidator;
import com.team8.splitsmart.settlement.SettlementService;

import org.junit.Test;

import java.util.Arrays;

public class SettlementServiceTest {
    private static final long GROUP = 1;
    private static final long ALICE = 1;
    private static final long BOB = 2;

    /** Alice's net balance with Bob; negative means Alice owes Bob. */
    private int aliceNetCents;

    private final SettlementService service = new SettlementService(
            new SettlementRepository(null) {
                @Override
                public Settlement save(Settlement settlement) {
                    return settlement;
                }

                @Override
                public Settlement update(Settlement settlement) {
                    return settlement;
                }

                @Override
                public Settlement findById(long settlementId) {
                    Settlement existing = new Settlement();
                    existing.settlementId = settlementId;
                    existing.groupId = GROUP;
                    existing.payerId = ALICE;
                    existing.payeeId = BOB;
                    existing.amountCents = 1000;
                    existing.createdBy = ALICE;
                    return existing;
                }
            },
            new GroupRepository(null) {
                @Override
                public boolean isMember(long groupId, long userId) {
                    return true;
                }
            },
            new AmountValidator(),
            new BalanceService(null, null, null, null) {
                @Override
                public BalanceSummary getBalances(long groupId, long userId) {
                    return new BalanceSummary(0, 0,
                            Arrays.asList(new PairBalance(BOB, "Bob", aliceNetCents)));
                }
            });

    private static User user(long id) {
        User user = new User();
        user.userId = id;
        return user;
    }

    private static User alice() {
        return user(ALICE);
    }

    @Test
    public void settlementCanPayUpToWhatIsOwed() {
        aliceNetCents = -2500;
        assertEquals(2500, service.recordSettlement(GROUP, alice(), ALICE, BOB, "25.00", null).amountCents);
    }

    @Test
    public void settlementCannotExceedWhatIsOwed() {
        aliceNetCents = -2500;
        assertThrows(ValidationException.class,
                () -> service.recordSettlement(GROUP, alice(), ALICE, BOB, "25.01", null));
    }

    @Test
    public void cannotSettleWhenNothingIsOwed() {
        aliceNetCents = 0;
        assertThrows(ValidationException.class,
                () -> service.recordSettlement(GROUP, alice(), ALICE, BOB, "1.00", null));
        aliceNetCents = 500; // Bob owes Alice
        assertThrows(ValidationException.class,
                () -> service.recordSettlement(GROUP, alice(), ALICE, BOB, "1.00", null));
    }

    @Test
    public void recipientCanRecordAPaymentTheyReceived() {
        aliceNetCents = -2500;
        Settlement saved = service.recordSettlement(GROUP, user(BOB), ALICE, BOB, "5.00", null);
        assertEquals(ALICE, saved.payerId);
        assertEquals(BOB, saved.payeeId);
        assertEquals(BOB, saved.createdBy);
        assertThrows(ValidationException.class,
                () -> service.recordSettlement(GROUP, user(BOB), ALICE, BOB, "25.01", null));
    }

    @Test
    public void onlyThePayerOrRecipientCanRecord() {
        aliceNetCents = -2500;
        assertThrows(ValidationException.class,
                () -> service.recordSettlement(GROUP, user(3), ALICE, BOB, "5.00", null));
    }

    @Test
    public void editingCountsTheDebtWithoutThisSettlement() {
        aliceNetCents = -2500; // $25.00 still owed after the existing $10.00 settlement
        assertEquals(3500, service.updateSettlement(5, alice(), "35.00", null).amountCents);
        assertThrows(ValidationException.class, () -> service.updateSettlement(5, alice(), "35.01", null));
    }

    @Test
    public void editingAllowsTheOriginalAmountWhenFullyPaidOff() {
        aliceNetCents = 0; // the existing $10.00 settlement paid the debt exactly
        assertEquals(1000, service.updateSettlement(5, alice(), "10.00", null).amountCents);
        assertThrows(ValidationException.class, () -> service.updateSettlement(5, alice(), "10.01", null));
    }
}
