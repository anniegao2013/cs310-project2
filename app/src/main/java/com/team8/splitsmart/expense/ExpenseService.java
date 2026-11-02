package com.team8.splitsmart.expense;

import com.team8.splitsmart.ValidationException;
import com.team8.splitsmart.data.Expense;
import com.team8.splitsmart.data.ExpenseRepository;
import com.team8.splitsmart.data.ExpenseSplit;
import com.team8.splitsmart.data.GroupRepository;
import com.team8.splitsmart.data.User;
import com.team8.splitsmart.split.SplitCalculator;
import com.team8.splitsmart.split.SplitResult;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates, creates, edits, deletes and retrieves expenses; stamps the creation date. */
public class ExpenseService {
    private final ExpenseRepository expenseRepo;
    private final GroupRepository groupRepo;
    private final AmountValidator amountValidator;
    private final SplitCalculator splitCalculator;

    public ExpenseService(ExpenseRepository expenseRepo, GroupRepository groupRepo,
                          AmountValidator amountValidator, SplitCalculator splitCalculator) {
        this.expenseRepo = expenseRepo;
        this.groupRepo = groupRepo;
        this.amountValidator = amountValidator;
        this.splitCalculator = splitCalculator;
    }

    /** Checks the draft against the expense rules and returns the total in cents. */
    public int validateDraft(ExpenseDraft draft) {
        if (draft.title == null || draft.title.trim().isEmpty()) {
            throw new ValidationException("Enter a title");
        }
        int totalCents = amountValidator.parseCents(draft.amountText);
        if (draft.category == null) {
            throw new ValidationException("Select a category");
        }
        if (!draft.participantIds.contains(draft.payerId)) {
            throw new ValidationException("The payer must be one of the participants");
        }
        Set<Long> participants = new HashSet<>(draft.participantIds);
        if (participants.size() < 2) {
            throw new ValidationException("Select at least one participant besides the payer");
        }
        Set<Long> memberIds = new HashSet<>();
        for (User member : groupRepo.getMembers(draft.groupId)) {
            memberIds.add(member.userId);
        }
        if (!memberIds.containsAll(participants)) {
            throw new ValidationException("The payer and all participants must be group members");
        }
        return totalCents;
    }

    public Expense createExpense(ExpenseDraft draft, SplitResult split, User creator) {
        if (!groupRepo.isMember(draft.groupId, creator.userId)) {
            throw new ValidationException("Only group members can add expenses");
        }
        int totalCents = validateDraft(draft);
        verifySplit(draft, split, totalCents);

        Expense expense = new Expense();
        expense.groupId = draft.groupId;
        expense.createdBy = creator.userId;
        expense.createdDate = LocalDate.now();
        applyDraft(expense, draft, split, totalCents);
        return expenseRepo.saveWithSplits(expense, toSplits(split));
    }

    /** Only the creator may edit; the creation date and creator never change. */
    public Expense updateExpense(long expenseId, ExpenseDraft draft, SplitResult split, User requester) {
        Expense expense = requireCreator(expenseId, requester);
        draft.groupId = expense.groupId;
        int totalCents = validateDraft(draft);
        verifySplit(draft, split, totalCents);

        applyDraft(expense, draft, split, totalCents);
        expenseRepo.updateWithSplits(expense, toSplits(split));
        return expenseRepo.findById(expenseId);
    }

    public void deleteExpense(long expenseId, User requester) {
        requireCreator(expenseId, requester);
        expenseRepo.delete(expenseId);
    }

    public Expense getExpense(long expenseId) {
        Expense expense = expenseRepo.findById(expenseId);
        if (expense == null) {
            throw new ValidationException("This expense no longer exists");
        }
        return expense;
    }

    private Expense requireCreator(long expenseId, User requester) {
        Expense expense = getExpense(expenseId);
        if (expense.createdBy != requester.userId) {
            throw new ValidationException("Only the person who created this expense can change it");
        }
        return expense;
    }

    private void verifySplit(ExpenseDraft draft, SplitResult split, int totalCents) {
        if (split == null || !split.valid) {
            throw new ValidationException(split == null ? "Choose a split method" : split.errorMessage);
        }
        if (!split.sharesCents.keySet().equals(new HashSet<>(draft.participantIds))) {
            throw new ValidationException("The split does not match the selected participants");
        }
        if (!splitCalculator.verify(totalCents, split)) {
            throw new ValidationException("Shares must add up to the total amount");
        }
    }

    private static void applyDraft(Expense expense, ExpenseDraft draft, SplitResult split, int totalCents) {
        expense.title = draft.title.trim();
        expense.amountCents = totalCents;
        expense.category = draft.category;
        expense.payerId = draft.payerId;
        expense.splitMethod = split.method;
        expense.note = draft.note == null || draft.note.trim().isEmpty() ? null : draft.note.trim();
    }

    private static List<ExpenseSplit> toSplits(SplitResult split) {
        List<ExpenseSplit> splits = new ArrayList<>();
        for (Map.Entry<Long, Integer> share : split.sharesCents.entrySet()) {
            splits.add(new ExpenseSplit(share.getKey(), share.getValue(), split.inputs.get(share.getKey())));
        }
        return splits;
    }
}
