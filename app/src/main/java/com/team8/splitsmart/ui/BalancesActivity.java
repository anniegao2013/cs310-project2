package com.team8.splitsmart.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.team8.splitsmart.Money;
import com.team8.splitsmart.R;
import com.team8.splitsmart.SplitSmartApp;
import com.team8.splitsmart.balance.BalanceService;
import com.team8.splitsmart.balance.BalanceSummary;
import com.team8.splitsmart.balance.PairBalance;
import com.team8.splitsmart.data.User;

/** The group's balance page for the logged-in user. Recalculated every time it is shown or refreshed. */
public class BalancesActivity extends AppCompatActivity {
    public static final String EXTRA_GROUP_ID = "groupId";

    private BalanceService balanceService;
    private User user;
    private long groupId;

    private SwipeRefreshLayout swipe;
    private BindingAdapter<PairBalance> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        user = Screens.requireUser(this);
        if (user == null) {
            return;
        }
        setContentView(R.layout.activity_balances);
        setTitle(R.string.balances);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        balanceService = SplitSmartApp.get().balances();
        groupId = getIntent().getLongExtra(EXTRA_GROUP_ID, 0);

        swipe = findViewById(R.id.swipe);
        swipe.setOnRefreshListener(this::load);
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BindingAdapter<>(R.layout.item_two_line, this::bindPair, null);
        list.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (user != null) {
            load();
        }
    }

    private void load() {
        swipe.setRefreshing(true);
        UiAsync.run(this, () -> balanceService.getBalances(groupId, user.userId), summary -> {
            swipe.setRefreshing(false);
            render(summary);
        }, message -> {
            swipe.setRefreshing(false);
            UiAsync.showError(this, message);
        });
    }

    public void render(BalanceSummary summary) {
        ((TextView) findViewById(R.id.toPayText)).setText(Money.format(summary.totalToPayCents));
        ((TextView) findViewById(R.id.toReceiveText)).setText(Money.format(summary.totalToReceiveCents));
        adapter.setItems(summary.pairs);
        findViewById(R.id.emptyText).setVisibility(summary.pairs.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void bindPair(View row, PairBalance pair) {
        ((TextView) row.findViewById(R.id.avatar)).setText(GroupActivity.initial(pair.otherUserName));
        ((TextView) row.findViewById(R.id.primary)).setText(pair.otherUserName);
        TextView secondary = row.findViewById(R.id.secondary);
        TextView amount = row.findViewById(R.id.trailing);
        if (pair.netCents > 0) {
            secondary.setText(pair.otherUserName + " owes you");
            amount.setText(Money.format(pair.netCents));
            amount.setTextColor(ContextCompat.getColor(this, R.color.owed_to_you));
        } else if (pair.netCents < 0) {
            secondary.setText("You owe " + pair.otherUserName);
            amount.setText(Money.format(-pair.netCents));
            amount.setTextColor(ContextCompat.getColor(this, R.color.you_owe));
        } else {
            secondary.setText("Settled up");
            amount.setText(Money.format(0));
            amount.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
