package com.team8.splitsmart.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.team8.splitsmart.Money;
import com.team8.splitsmart.R;
import com.team8.splitsmart.SplitSmartApp;
import com.team8.splitsmart.ValidationException;
import com.team8.splitsmart.data.Category;
import com.team8.splitsmart.data.Expense;
import com.team8.splitsmart.data.ExpenseSplit;
import com.team8.splitsmart.data.SplitMethod;
import com.team8.splitsmart.data.User;
import com.team8.splitsmart.expense.AmountValidator;
import com.team8.splitsmart.expense.ExpenseDraft;
import com.team8.splitsmart.expense.ExpenseService;
import com.team8.splitsmart.split.SplitCalculator;
import com.team8.splitsmart.split.SplitResult;
import com.team8.splitsmart.usergroup.GroupService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Add / edit expense form with a live preview of each participant's share. */
public class ExpenseFormActivity extends AppCompatActivity {
    public static final String EXTRA_GROUP_ID = "groupId";
    public static final String EXTRA_EXPENSE_ID = "expenseId";

    private final AmountValidator amountValidator = new AmountValidator();
    private ExpenseService expenseService;
    private SplitCalculator splitCalculator;
    private GroupService groupService;
    private User user;
    private long groupId;
    private long expenseId;

    private List<User> members = new ArrayList<>();
    private final List<ParticipantRow> rows = new ArrayList<>();
    private SplitMethod method = SplitMethod.EQUAL;
    private long payerId;
    private SplitResult lastResult;
    private boolean binding;

    private TextInputEditText titleInput;
    private TextInputEditText amountInput;
    private TextInputLayout amountLayout;
    private TextInputEditText noteInput;
    private Spinner categorySpinner;
    private Spinner payerSpinner;
    private MaterialButtonToggleGroup methodToggle;
    private TextView previewStatus;
    private Button saveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        user = Screens.requireUser(this);
        if (user == null) {
            return;
        }
        setContentView(R.layout.activity_expense_form);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        SplitSmartApp app = SplitSmartApp.get();
        expenseService = app.expenses();
        splitCalculator = app.splitCalculator();
        groupService = app.groups();
        groupId = getIntent().getLongExtra(EXTRA_GROUP_ID, 0);
        expenseId = getIntent().getLongExtra(EXTRA_EXPENSE_ID, 0);
        setTitle(expenseId == 0 ? R.string.add_expense : R.string.edit_expense);

        titleInput = findViewById(R.id.titleInput);
        amountInput = findViewById(R.id.amountInput);
        amountLayout = findViewById(R.id.amountLayout);
        noteInput = findViewById(R.id.noteInput);
        categorySpinner = findViewById(R.id.categorySpinner);
        payerSpinner = findViewById(R.id.payerSpinner);
        methodToggle = findViewById(R.id.methodToggle);
        previewStatus = findViewById(R.id.previewStatus);
        saveButton = findViewById(R.id.saveButton);

        ArrayAdapter<Category> categoryAdapter = new ArrayAdapter<>(this,
                R.layout.item_spinner, Category.values());
        categoryAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        categorySpinner.setAdapter(categoryAdapter);
        categorySpinner.setSelection(Category.OTHER.ordinal());

        titleInput.addTextChangedListener(watcher());
        amountInput.addTextChangedListener(watcher());
        methodToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            onSplitMethodSelected(checkedId == R.id.exactButton ? SplitMethod.EXACT_AMOUNT
                    : checkedId == R.id.percentButton ? SplitMethod.PERCENTAGE : SplitMethod.EQUAL);
        });
        saveButton.setOnClickListener(v -> onSubmitClicked());

        load();
    }

    private void load() {
        UiAsync.run(this, () -> {
            FormData data = new FormData();
            data.members = groupService.getMembers(groupId);
            data.expense = expenseId == 0 ? null : expenseService.getExpense(expenseId);
            return data;
        }, data -> {
            members = data.members;
            buildForm(data.expense);
            findViewById(R.id.progress).setVisibility(View.GONE);
            findViewById(R.id.content).setVisibility(View.VISIBLE);
        }, message -> {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            finish();
        });
    }

    private void buildForm(Expense expense) {
        binding = true;
        payerId = expense != null ? expense.payerId : user.userId;

        List<String> names = new ArrayList<>();
        int payerIndex = 0;
        for (int i = 0; i < members.size(); i++) {
            User member = members.get(i);
            names.add(member.userId == user.userId ? member.name + " (you)" : member.name);
            if (member.userId == payerId) {
                payerIndex = i;
            }
        }
        ArrayAdapter<String> payerAdapter = new ArrayAdapter<>(this, R.layout.item_spinner, names);
        payerAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        payerSpinner.setAdapter(payerAdapter);
        payerSpinner.setSelection(payerIndex, false);
        HistoryActivity.onSelected(payerSpinner, position -> {
            payerId = members.get(position).userId;
            recompute();
        });

        Map<Long, ExpenseSplit> existingSplits = new HashMap<>();
        if (expense != null) {
            for (ExpenseSplit split : expense.splits) {
                existingSplits.put(split.userId, split);
            }
        }
        LinearLayout container = findViewById(R.id.participantRows);
        container.removeAllViews();
        rows.clear();
        for (int i = 0; i < members.size(); i++) {
            ParticipantRow row = new ParticipantRow(container, members.get(i), names.get(i));
            ExpenseSplit split = existingSplits.get(row.user.userId);
            row.checkbox.setChecked(expense == null ? row.user.userId == payerId : split != null);
            if (split != null && split.inputValue != null) {
                row.input.setText(split.inputValue);
            }
            rows.add(row);
            container.addView(row.view);
        }

        if (expense != null) {
            titleInput.setText(expense.title);
            amountInput.setText(Money.toInput(expense.amountCents));
            categorySpinner.setSelection(expense.category.ordinal());
            noteInput.setText(expense.note);
            method = expense.splitMethod;
            methodToggle.check(method == SplitMethod.EXACT_AMOUNT ? R.id.exactButton
                    : method == SplitMethod.PERCENTAGE ? R.id.percentButton : R.id.equalButton);
            ((TextView) findViewById(R.id.dateText)).setText("Created " + expense.createdDate
                    + " (the date is not editable)");
        } else {
            ((TextView) findViewById(R.id.dateText)).setText("Date: " + LocalDate.now()
                    + " (recorded automatically)");
        }
        binding = false;
        recompute();
    }

    public void onSplitMethodSelected(SplitMethod method) {
        this.method = method;
        recompute();
    }

    /** Gathers the form's inputs, recalculates every participant's share and shows the result. */
    private void recompute() {
        if (binding) {
            return;
        }
        List<Long> participantIds = new ArrayList<>();
        Map<Long, String> inputs = new HashMap<>();
        for (ParticipantRow row : rows) {
            boolean isPayer = row.user.userId == payerId;
            if (isPayer && !row.checkbox.isChecked()) {
                binding = true;
                row.checkbox.setChecked(true);
                binding = false;
            }
            row.checkbox.setEnabled(!isPayer);
            row.checkbox.setText(isPayer ? row.label + " · payer" : row.label);
            boolean included = row.checkbox.isChecked();
            row.input.setVisibility(included && method != SplitMethod.EQUAL ? View.VISIBLE : View.GONE);
            row.input.setHint(method == SplitMethod.PERCENTAGE ? "%" : "$");
            if (included) {
                participantIds.add(row.user.userId);
                inputs.put(row.user.userId, row.input.getText().toString());
            }
        }

        String amountText = AuthActivity.text(amountInput);
        int totalCents = 0;
        String amountError = null;
        if (!amountText.isEmpty()) {
            try {
                totalCents = amountValidator.parseCents(amountText);
            } catch (ValidationException e) {
                amountError = e.getMessage();
            }
        }
        amountLayout.setError(amountError);

        lastResult = splitCalculator.preview(method, totalCents, participantIds, inputs);
        showPreview(lastResult);
    }

    /** Shows each participant's share and whether the expense can be saved. */
    public void showPreview(SplitResult split) {
        int sum = 0;
        for (ParticipantRow row : rows) {
            Integer share = split.sharesCents.get(row.user.userId);
            row.share.setText(row.checkbox.isChecked() && share != null ? Money.format(share) : "—");
            sum += share != null ? share : 0;
        }

        boolean hasTitle = !AuthActivity.text(titleInput).trim().isEmpty();
        if (split.valid) {
            previewStatus.setText("✓ Shares add up to " + Money.format(sum)
                    + (hasTitle ? "" : " — add a title to save"));
            previewStatus.setTextColor(ContextCompat.getColor(this, R.color.owed_to_you));
        } else {
            previewStatus.setText(split.errorMessage);
            previewStatus.setTextColor(ContextCompat.getColor(this, R.color.you_owe));
        }
        saveButton.setEnabled(split.valid && hasTitle);
    }

    public void onSubmitClicked() {
        ExpenseDraft draft = new ExpenseDraft();
        draft.groupId = groupId;
        draft.title = AuthActivity.text(titleInput);
        draft.amountText = AuthActivity.text(amountInput);
        draft.category = (Category) categorySpinner.getSelectedItem();
        draft.payerId = payerId;
        draft.note = AuthActivity.text(noteInput);
        for (ParticipantRow row : rows) {
            if (row.checkbox.isChecked()) {
                draft.participantIds.add(row.user.userId);
            }
        }
        SplitResult split = lastResult;
        saveButton.setEnabled(false);
        UiAsync.run(this, () -> expenseId == 0
                ? expenseService.createExpense(draft, split, user)
                : expenseService.updateExpense(expenseId, draft, split, user), saved -> {
            Toast.makeText(this, expenseId == 0 ? "Expense added" : "Expense updated", Toast.LENGTH_SHORT).show();
            finish();
        }, message -> {
            saveButton.setEnabled(true);
            UiAsync.showError(this, message);
        });
    }

    private TextWatcher watcher() {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                recompute();
            }
        };
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private class ParticipantRow {
        final User user;
        final String label;
        final View view;
        final CheckBox checkbox;
        final EditText input;
        final TextView share;

        ParticipantRow(LinearLayout parent, User user, String label) {
            this.user = user;
            this.label = label;
            view = LayoutInflater.from(ExpenseFormActivity.this).inflate(R.layout.item_participant, parent, false);
            checkbox = view.findViewById(R.id.checkbox);
            input = view.findViewById(R.id.shareInput);
            share = view.findViewById(R.id.shareText);
            checkbox.setText(label);
            checkbox.setOnCheckedChangeListener((button, checked) -> recompute());
            input.addTextChangedListener(watcher());
        }
    }

    private static class FormData {
        List<User> members;
        Expense expense;
    }
}
