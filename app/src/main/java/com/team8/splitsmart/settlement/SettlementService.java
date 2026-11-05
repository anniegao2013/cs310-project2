package com.team8.splitsmart.settlement;

import com.team8.splitsmart.Money;
import com.team8.splitsmart.ValidationException;
import com.team8.splitsmart.balance.BalanceService;
import com.team8.splitsmart.balance.PairBalance;
import com.team8.splitsmart.data.GroupRepository;
import com.team8.splitsmart.data.Settlement;
import com.team8.splitsmart.data.SettlementRepository;
import com.team8.splitsmart.data.User;
import com.team8.splitsmart.expense.AmountValidator;

import java.time.LocalDate;

/**
 * Records payments made outside the app. Either side of the payment can record it: the member who
 * paid or the member who received it. The amount can never exceed what the payer currently owes.
 */
public class SettlementService {
    private final SettlementRepository settlementRepo;
    private final GroupRepository groupRepo;
    private final AmountValidator amountValidator;
    private final BalanceService balanceService;

    public SettlementService(SettlementRepository settlementRepo, GroupRepository groupRepo,
                             AmountValidator amountValidator, BalanceService balanceService) {
        this.settlementRepo = settlementRepo;
        this.groupRepo = groupRepo;
        this.amountValidator = amountValidator;
        this.balanceService = balanceService;
    }

    public Settlement recordSettlement(long groupId, User creator, long payerId, long payeeId,
                                       String amountText, String note) {
        if (payeeId == payerId) {
            throw new ValidationException("Choose another member");
        }
        if (creator.userId != payerId && creator.userId != payeeId) {
            throw new ValidationException("Only the payer or the recipient can record a settlement");
        }
        if (!groupRepo.isMember(groupId, payerId) || !groupRepo.isMember(groupId, payeeId)) {
            throw new ValidationException("Both users must be members of the group");
        }
        int cents = amountValidator.parseCents(amountText);
        checkWithinOwed(cents, currentDebt(groupId, payerId, payeeId));

        Settlement settlement = new Settlement();
        settlement.groupId = groupId;
        settlement.payerId = payerId;
        settlement.payeeId = payeeId;
        settlement.amountCents = cents;
        settlement.date = LocalDate.now();
        settlement.note = blankToNull(note);
        settlement.createdBy = creator.userId;
        return settlementRepo.save(settlement);
    }

    /** Only the creator may edit; the amount is re-checked against the debt without this settlement. */
    public Settlement updateSettlement(long settlementId, User requester, String amountText, String note) {
        Settlement settlement = requireCreator(settlementId, requester);
        int cents = amountValidator.parseCents(amountText);
        checkWithinOwed(cents, currentDebt(settlement.groupId, settlement.payerId, settlement.payeeId)
                + settlement.amountCents);
        settlement.amountCents = cents;
        settlement.note = blankToNull(note);
        return settlementRepo.update(settlement);
    }

    public void deleteSettlement(long settlementId, User requester) {
        requireCreator(settlementId, requester);
        settlementRepo.delete(settlementId);
    }

    public Settlement getSettlement(long settlementId) {
        Settlement settlement = settlementRepo.findById(settlementId);
        if (settlement == null) {
            throw new ValidationException("This settlement no longer exists");
        }
        return settlement;
    }

    private Settlement requireCreator(long settlementId, User requester) {
        Settlement settlement = getSettlement(settlementId);
        if (settlement.createdBy != requester.userId) {
            throw new ValidationException("Only the person who recorded this settlement can change it");
        }
        return settlement;
    }

    /** What {@code payerId} owes {@code payeeId}; negative when {@code payeeId} owes {@code payerId} instead. */
    private int currentDebt(long groupId, long payerId, long payeeId) {
        for (PairBalance pair : balanceService.getBalances(groupId, payerId).pairs) {
            if (pair.otherUserId == payeeId) {
                return -pair.netCents;
            }
        }
        return 0;
    }

    private static void checkWithinOwed(int cents, int owedCents) {
        if (owedCents <= 0) {
            throw new ValidationException("Nothing is owed between these members");
        }
        if (cents > owedCents) {
            throw new ValidationException("Amount cannot be more than the " + Money.format(owedCents) + " owed");
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.trim().isEmpty() ? null : text.trim();
    }
}
