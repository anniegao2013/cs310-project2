package com.team8.splitsmart.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.team8.splitsmart.Money;
import com.team8.splitsmart.R;
import com.team8.splitsmart.SplitSmartApp;
import com.team8.splitsmart.balance.BalanceService;
import com.team8.splitsmart.balance.BalanceSummary;
import com.team8.splitsmart.balance.PairBalance;
import com.team8.splitsmart.dashboard.HistoryService;
import com.team8.splitsmart.dashboard.MonthlySummary;
import com.team8.splitsmart.dashboard.MonthlySummaryService;
import com.team8.splitsmart.dashboard.TransactionFilter;
import com.team8.splitsmart.dashboard.TransactionItem;
import com.team8.splitsmart.data.Category;
import com.team8.splitsmart.data.Group;
import com.team8.splitsmart.data.TransactionType;
import com.team8.splitsmart.data.User;
import com.team8.splitsmart.expense.ExpenseService;
import com.team8.splitsmart.settlement.SettlementService;
import com.team8.splitsmart.usergroup.GroupService;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The group page: balance overview, transaction history with search/filter/sort, and entry points for
 * adding expenses, recording settlements, the balance page, the monthly summary and the member list.
 */
public class HistoryActivity extends AppCompatActivity {
    public static final String EXTRA_GROUP_ID = "groupId";

    private HistoryService historyService;
    private SettlementService settlementService;
    private MonthlySummaryService summaryService;
    private BalanceService balanceService;
    private ExpenseService expenseService;
    private GroupService groupService;
    private User user;
    private long groupId;
    private Group group;
    private List<User> members = new ArrayList<>();
    private List<TransactionItem> allItems = new ArrayList<>();
    private final TransactionFilter filter = new TransactionFilter();
    private String searchQuery = "";

    private SwipeRefreshLayout swipe;
    private TextView emptyText;
    private TextView historyLabel;
    private TextView toPayText;
    private TextView toReceiveText;
    private Spinner memberSpinner;
    private BindingAdapter<TransactionItem> adapter;
    private LinearLayout summaryRows;
    private View summaryProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        user = Screens.requireUser(this);
        if (user == null) {
            return;
        }
        setContentView(R.layout.activity_history);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        SplitSmartApp app = SplitSmartApp.get();
        historyService = app.history();
        settlementService = app.settlements();
        summaryService = app.monthlySummaries();
        balanceService = app.balances();
        expenseService = app.expenses();
        groupService = app.groups();
        groupId = getIntent().getLongExtra(EXTRA_GROUP_ID, 0);

        swipe = findViewById(R.id.swipe);
        emptyText = findViewById(R.id.emptyText);
        historyLabel = findViewById(R.id.historyLabel);
        toPayText = findViewById(R.id.toPayText);
        toReceiveText = findViewById(R.id.toReceiveText);
        swipe.setOnRefreshListener(this::load);

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BindingAdapter<>(R.layout.item_transaction, this::bindTransaction, this::showDetails);
        list.setAdapter(adapter);

        findViewById(R.id.fab).setOnClickListener(v -> openExpenseForm(0));
        findViewById(R.id.balanceCard).setOnClickListener(v -> openBalances());
        setUpFilters();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (user != null) {
            load();
        }
    }

    private void load() {
        swipe.setRefreshing(true);
        UiAsync.run(this, () -> {
            PageData data = new PageData();
            data.group = groupService.getGroup(groupId);
            data.members = groupService.getMembers(groupId);
            data.items = historyService.getHistory(groupId);
            data.balances = balanceService.getBalances(groupId, user.userId);
            return data;
        }, data -> {
            swipe.setRefreshing(false);
            group = data.group;
            members = data.members;
            allItems = data.items;
            setTitle(group.name);
            getSupportActionBar().setSubtitle(members.size() + (members.size() == 1 ? " member" : " members"));
            toPayText.setText(Money.format(data.balances.totalToPayCents));
            toReceiveText.setText(Money.format(data.balances.totalToReceiveCents));
            refreshMemberSpinner();
            onFilterChanged(filter);
        }, message -> {
            swipe.setRefreshing(false);
            if (group == null || message.contains("no longer exists")) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                finish();
            } else {
                UiAsync.showError(this, message);
            }
        });
    }

    // ----- Search, filter and sort -----

    private void setUpFilters() {
        TextInputEditText search = findViewById(R.id.searchInput);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                onSearch(s.toString());
            }
        });

        Spinner typeSpinner = findViewById(R.id.typeSpinner);
        typeSpinner.setAdapter(spinnerAdapter(new String[]{"All types", "Expenses", "Settlements"}));
        onSelected(typeSpinner, position -> {
            filter.type = position == 0 ? null : position == 1 ? TransactionType.EXPENSE : TransactionType.SETTLEMENT;
            onFilterChanged(filter);
        });

        Spinner categorySpinner = findViewById(R.id.categorySpinner);
        List<String> categories = new ArrayList<>();
        categories.add("All categories");
        for (Category category : Category.values()) {
            categories.add(category.getLabel());
        }
        categorySpinner.setAdapter(spinnerAdapter(categories.toArray(new String[0])));
        onSelected(categorySpinner, position -> {
            filter.category = position == 0 ? null : Category.values()[position - 1];
            onFilterChanged(filter);
        });

        memberSpinner = findViewById(R.id.memberSpinner);
        memberSpinner.setAdapter(spinnerAdapter(new String[]{"Any member"}));
        onSelected(memberSpinner, position -> {
            filter.memberId = position == 0 || position > members.size() ? 0 : members.get(position - 1).userId;
            onFilterChanged(filter);
        });

        Spinner sortSpinner = findViewById(R.id.sortSpinner);
        sortSpinner.setAdapter(spinnerAdapter(new String[]{"Newest first", "Oldest first"}));
        onSelected(sortSpinner, position -> {
            filter.newestFirst = position == 0;
            onFilterChanged(filter);
        });
    }

    private void refreshMemberSpinner() {
        List<String> names = new ArrayList<>();
        names.add("Any member");
        int selected = 0;
        for (int i = 0; i < members.size(); i++) {
            User member = members.get(i);
            names.add(member.userId == user.userId ? member.name + " (you)" : member.name);
            if (member.userId == filter.memberId) {
                selected = i + 1;
            }
        }
        memberSpinner.setAdapter(spinnerAdapter(names.toArray(new String[0])));
        memberSpinner.setSelection(selected, false);
    }

    public void onSearch(String query) {
        searchQuery = query;
        onFilterChanged(filter);
    }

    public void onFilterChanged(TransactionFilter f) {
        List<TransactionItem> shown = historyService.filter(historyService.search(allItems, searchQuery), f);
        adapter.setItems(shown);
        if (allItems.isEmpty()) {
            emptyText.setText(R.string.no_transactions);
        } else {
            emptyText.setText(R.string.no_matches);
        }
        emptyText.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        historyLabel.setText(shown.size() != allItems.size()
                ? String.format(Locale.US, "History · showing %d of %d", shown.size(), allItems.size())
                : String.format(Locale.US, "History · %d transaction%s", allItems.size(),
                allItems.size() == 1 ? "" : "s"));
    }

    private ArrayAdapter<String> spinnerAdapter(String[] items) {
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, R.layout.item_spinner, items);
        spinnerAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        return spinnerAdapter;
    }

    interface OnPosition {
        void selected(int position);
    }

    static void onSelected(Spinner spinner, OnPosition listener) {
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                listener.selected(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    // ----- History rows and details -----

    private void bindTransaction(View row, TransactionItem item) {
        TextView icon = row.findViewById(R.id.icon);
        TextView title = row.findViewById(R.id.title);
        TextView meta = row.findViewById(R.id.meta);
        TextView people = row.findViewById(R.id.people);
        TextView amount = row.findViewById(R.id.amount);

        title.setText(item.title);
        amount.setText(Money.format(item.amountCents));
        if (item.type == TransactionType.EXPENSE) {
            icon.setText(emojiFor(item.category));
            meta.setText(String.format("Expense · %s · %s", item.category.getLabel(), item.date));
            people.setText(String.format("Paid by %s · %s", item.payerName, String.join(", ", item.involvedUserNames)));
            amount.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        } else {
            icon.setText("💸");
            meta.setText(String.format("Settlement · %s", item.date));
            people.setText(String.format("%s paid %s", item.payerName, item.payeeName));
            amount.setTextColor(ContextCompat.getColor(this, R.color.settlement));
        }
    }

    static String emojiFor(Category category) {
        switch (category) {
            case FOOD:
                return "🍔";
            case HOUSING:
                return "🏠";
            case TRANSPORTATION:
                return "🚗";
            case ENTERTAINMENT:
                return "🎬";
            default:
                return "🧾";
        }
    }

    private void showDetails(TransactionItem item) {
        StringBuilder text = new StringBuilder();
        if (item.type == TransactionType.EXPENSE) {
            text.append("Type: Expense\n")
                    .append("Amount: ").append(Money.format(item.amountCents)).append('\n')
                    .append("Date: ").append(item.date).append('\n')
                    .append("Category: ").append(item.category.getLabel()).append('\n')
                    .append("Paid by: ").append(item.payerName).append('\n')
                    .append("Split method: ").append(item.splitMethod.getLabel()).append("\n\n")
                    .append("Shares:\n");
            for (Map.Entry<String, Integer> share : item.sharesByName.entrySet()) {
                text.append("  • ").append(share.getKey()).append(": ").append(Money.format(share.getValue()));
                if (share.getKey().equals(item.payerName)) {
                    text.append(" (payer)");
                }
                text.append('\n');
            }
        } else {
            text.append("Type: Settlement\n")
                    .append("Amount: ").append(Money.format(item.amountCents)).append('\n')
                    .append("Date: ").append(item.date).append('\n')
                    .append("Paid by: ").append(item.payerName).append('\n')
                    .append("Received by: ").append(item.payeeName).append('\n');
        }
        text.append("\nNote: ").append(item.note != null ? item.note : "—");
        text.append("\nRecorded by: ").append(nameOf(item.createdBy));

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(item.type == TransactionType.EXPENSE ? item.title : TransactionItem.SETTLEMENT_LABEL)
                .setMessage(text)
                .setNegativeButton(R.string.close, null);
        if (item.createdBy == user.userId) {
            builder.setPositiveButton(R.string.edit, (d, which) -> {
                if (item.type == TransactionType.EXPENSE) {
                    openExpenseForm(item.id);
                } else {
                    showSettlementDialog(null, item);
                }
            });
            builder.setNeutralButton(R.string.delete, (d, which) -> confirmDelete(item));
        }
        builder.show();
    }

    private void confirmDelete(TransactionItem item) {
        boolean expense = item.type == TransactionType.EXPENSE;
        new MaterialAlertDialogBuilder(this)
                .setTitle(expense ? "Delete expense?" : "Delete settlement?")
                .setMessage("\"" + item.title + "\" (" + Money.format(item.amountCents) + ") will be removed and "
                        + "everyone's balances will be updated.")
                .setPositiveButton(R.string.delete, (d, which) -> UiAsync.run(this, () -> {
                    if (expense) {
                        expenseService.deleteExpense(item.id, user);
                    } else {
                        settlementService.deleteSettlement(item.id, user);
                    }
                    return true;
                }, ok -> {
                    UiAsync.showError(this, expense ? "Expense deleted" : "Settlement deleted");
                    load();
                }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String nameOf(long userId) {
        for (User member : members) {
            if (member.userId == userId) {
                return member.name;
            }
        }
        return "Unknown user";
    }

    // ----- Settlements -----

    public void onRecordSettlementClicked() {
        UiAsync.run(this, () -> balanceService.getBalances(groupId, user.userId), balances -> {
            List<PairBalance> open = new ArrayList<>();
            for (PairBalance pair : balances.pairs) {
                if (pair.netCents != 0) {
                    open.add(pair);
                }
            }
            if (open.isEmpty()) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.record_settlement)
                        .setMessage("You have no open balances in this group right now. A settlement records a "
                                + "payment between you and a member you owe or who owes you.")
                        .setPositiveButton(R.string.close, null)
                        .show();
            } else {
                showSettlementDialog(open, null);
            }
        });
    }

    /** Record a new settlement (choose from the {@code open} balances, either direction) or edit {@code existing}. */
    private void showSettlementDialog(List<PairBalance> open, TransactionItem existing) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_settlement, null);
        Spinner paymentSpinner = view.findViewById(R.id.paymentSpinner);
        TextInputLayout amountLayout = view.findViewById(R.id.amountLayout);
        TextInputEditText amountInput = view.findViewById(R.id.amountInput);
        TextInputEditText noteInput = view.findViewById(R.id.noteInput);
        TextView dateText = view.findViewById(R.id.dateText);

        if (existing == null) {
            List<String> labels = new ArrayList<>();
            for (PairBalance pair : open) {
                labels.add(pair.netCents < 0
                        ? "You paid " + pair.otherUserName + " (you owe " + Money.format(-pair.netCents) + ")"
                        : pair.otherUserName + " paid you (owes you " + Money.format(pair.netCents) + ")");
            }
            paymentSpinner.setAdapter(spinnerAdapter(labels.toArray(new String[0])));
            onSelected(paymentSpinner, position -> {
                int owedCents = Math.abs(open.get(position).netCents);
                amountInput.setText(Money.toInput(owedCents));
                amountLayout.setHelperText("At most " + Money.format(owedCents));
            });
            dateText.setText("Date: " + LocalDate.now() + " (recorded automatically)");
        } else {
            paymentSpinner.setAdapter(spinnerAdapter(new String[]{existing.payerName + " paid " + existing.payeeName}));
            paymentSpinner.setEnabled(false);
            amountInput.setText(Money.toInput(existing.amountCents));
            noteInput.setText(existing.note);
            dateText.setText("Date: " + existing.date);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? "Record settlement" : "Edit settlement")
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String amount = AuthActivity.text(amountInput);
            String note = AuthActivity.text(noteInput);
            PairBalance pair = existing == null ? open.get(paymentSpinner.getSelectedItemPosition()) : null;
            UiAsync.run(this, () -> {
                if (existing == null) {
                    long payerId = pair.netCents < 0 ? user.userId : pair.otherUserId;
                    long payeeId = pair.netCents < 0 ? pair.otherUserId : user.userId;
                    return settlementService.recordSettlement(groupId, user, payerId, payeeId, amount, note);
                }
                return settlementService.updateSettlement(existing.id, user, amount, note);
            }, saved -> {
                dialog.dismiss();
                UiAsync.showError(this, existing == null ? "Settlement recorded" : "Settlement updated");
                load();
            }, amountLayout::setError);
        });
    }

    // ----- Monthly summary -----

    private void onMonthlySummaryClicked() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_monthly_summary, null);
        Spinner monthSpinner = view.findViewById(R.id.monthSpinner);
        Spinner yearSpinner = view.findViewById(R.id.yearSpinner);
        summaryRows = view.findViewById(R.id.summaryRows);
        summaryProgress = view.findViewById(R.id.summaryProgress);

        String[] months = new String[12];
        for (int i = 0; i < 12; i++) {
            months[i] = Month.of(i + 1).getDisplayName(TextStyle.FULL, Locale.US);
        }
        YearMonth now = YearMonth.now();
        int firstYear = now.getYear() - 4;
        String[] years = new String[6];
        for (int i = 0; i < years.length; i++) {
            years[i] = String.valueOf(firstYear + i);
        }
        monthSpinner.setAdapter(spinnerAdapter(months));
        yearSpinner.setAdapter(spinnerAdapter(years));
        monthSpinner.setSelection(now.getMonthValue() - 1, false);
        yearSpinner.setSelection(now.getYear() - firstYear, false);

        Supplier<YearMonth> selectedMonth = () -> YearMonth.of(firstYear + yearSpinner.getSelectedItemPosition(),
                monthSpinner.getSelectedItemPosition() + 1);
        onSelected(monthSpinner, position -> onMonthSelected(selectedMonth.get()));
        onSelected(yearSpinner, position -> onMonthSelected(selectedMonth.get()));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.monthly_summary)
                .setView(view)
                .setPositiveButton(R.string.close, null)
                .show();
        onMonthSelected(selectedMonth.get());
    }

    public void onMonthSelected(YearMonth month) {
        summaryProgress.setVisibility(View.VISIBLE);
        UiAsync.run(this, () -> summaryService.getSummary(groupId, month), summary -> {
            summaryProgress.setVisibility(View.GONE);
            showSummary(summaryRows, summary);
        }, message -> {
            summaryProgress.setVisibility(View.GONE);
            UiAsync.showError(this, message);
        });
    }

    private void showSummary(LinearLayout rows, MonthlySummary summary) {
        rows.removeAllViews();
        addSummaryRow(rows, "Expenses created", String.valueOf(summary.expenseCount), true);
        addSummaryRow(rows, "Total group spending", Money.format(summary.totalCents), true);
        TextView header = new TextView(this);
        header.setText("By category");
        header.setTextAppearance(R.style.TextAppearance_SplitSmart_TitleSmall);
        header.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        header.setPadding(0, 24, 0, 4);
        rows.addView(header);
        for (Category category : Category.values()) {
            addCategoryBar(rows, category, summary.byCategoryCents.get(category), summary.totalCents);
        }
    }

    /** One bar of the category chart. Categories keep a fixed order so months can be compared. */
    private void addCategoryBar(LinearLayout rows, Category category, int cents, int totalCents) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_category_bar, rows, false);
        ((TextView) row.findViewById(R.id.label)).setText(emojiFor(category) + "  " + category.getLabel());
        ((TextView) row.findViewById(R.id.value)).setText(Money.format(cents));

        View fill = row.findViewById(R.id.barFill);
        View rest = row.findViewById(R.id.barRest);
        float share = totalCents == 0 ? 0 : (float) cents / totalCents;
        if (cents > 0) {
            // Keep very small amounts visible next to large ones.
            share = Math.max(share, 0.015f);
        }
        ((LinearLayout.LayoutParams) fill.getLayoutParams()).weight = share;
        ((LinearLayout.LayoutParams) rest.getLayoutParams()).weight = 1 - share;
        rows.addView(row);
    }

    private void addSummaryRow(LinearLayout rows, String label, String value, boolean bold) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_summary_row, rows, false);
        TextView labelView = row.findViewById(R.id.label);
        TextView valueView = row.findViewById(R.id.value);
        labelView.setText(label);
        valueView.setText(value);
        if (bold) {
            android.graphics.Typeface semibold = ResourcesCompat.getFont(this, R.font.barlow_semibold);
            labelView.setTypeface(semibold);
            valueView.setTypeface(semibold);
        }
        rows.addView(row);
    }

    // ----- Navigation -----

    private void openExpenseForm(long expenseId) {
        Intent intent = new Intent(this, ExpenseFormActivity.class);
        intent.putExtra(ExpenseFormActivity.EXTRA_GROUP_ID, groupId);
        intent.putExtra(ExpenseFormActivity.EXTRA_EXPENSE_ID, expenseId);
        startActivity(intent);
    }

    private void openBalances() {
        Intent intent = new Intent(this, BalancesActivity.class);
        intent.putExtra(BalancesActivity.EXTRA_GROUP_ID, groupId);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_history, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_record_settlement) {
            onRecordSettlementClicked();
        } else if (id == R.id.action_balances) {
            openBalances();
        } else if (id == R.id.action_monthly_summary) {
            onMonthlySummaryClicked();
        } else if (id == R.id.action_members) {
            Intent intent = new Intent(this, GroupActivity.class);
            intent.putExtra(GroupActivity.EXTRA_GROUP_ID, groupId);
            startActivity(intent);
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private static class PageData {
        Group group;
        List<User> members;
        List<TransactionItem> items;
        BalanceSummary balances;
    }
}
