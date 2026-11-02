package com.team8.splitsmart;

import android.app.Application;

import com.team8.splitsmart.balance.BalanceCalculator;
import com.team8.splitsmart.balance.BalanceService;
import com.team8.splitsmart.dashboard.HistoryService;
import com.team8.splitsmart.dashboard.MonthlySummaryService;
import com.team8.splitsmart.data.ExpenseRepository;
import com.team8.splitsmart.data.GroupRepository;
import com.team8.splitsmart.data.SettlementRepository;
import com.team8.splitsmart.data.SplitSmartDatabase;
import com.team8.splitsmart.data.UserRepository;
import com.team8.splitsmart.expense.AmountValidator;
import com.team8.splitsmart.expense.ExpenseService;
import com.team8.splitsmart.settlement.SettlementService;
import com.team8.splitsmart.split.SplitCalculator;
import com.team8.splitsmart.usergroup.AuthService;
import com.team8.splitsmart.usergroup.EmailValidator;
import com.team8.splitsmart.usergroup.GroupService;
import com.team8.splitsmart.usergroup.PasswordHasher;

/** Builds every repository and service once and shares them with the screens. */
public class SplitSmartApp extends Application {
    private static SplitSmartApp instance;

    private String configError;

    private AuthService authService;
    private GroupService groupService;
    private ExpenseService expenseService;
    private SplitCalculator splitCalculator;
    private BalanceService balanceService;
    private SettlementService settlementService;
    private HistoryService historyService;
    private MonthlySummaryService monthlySummaryService;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        try {
            SplitSmartDatabase.init(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY);
        } catch (RuntimeException e) {
            configError = e.getMessage();
            return;
        }
        SplitSmartDatabase db = SplitSmartDatabase.getInstance();

        UserRepository userRepo = new UserRepository(db);
        GroupRepository groupRepo = new GroupRepository(db);
        ExpenseRepository expenseRepo = new ExpenseRepository(db);
        SettlementRepository settlementRepo = new SettlementRepository(db);

        AmountValidator amountValidator = new AmountValidator();
        splitCalculator = new SplitCalculator();
        authService = new AuthService(userRepo, new EmailValidator(), new PasswordHasher());
        groupService = new GroupService(groupRepo, userRepo);
        expenseService = new ExpenseService(expenseRepo, groupRepo, amountValidator, splitCalculator);
        balanceService = new BalanceService(expenseRepo, settlementRepo, userRepo, new BalanceCalculator());
        settlementService = new SettlementService(settlementRepo, groupRepo, amountValidator, balanceService);
        historyService = new HistoryService(expenseRepo, settlementRepo, userRepo);
        monthlySummaryService = new MonthlySummaryService(expenseRepo);
    }

    public static SplitSmartApp get() {
        return instance;
    }

    /** Null when the app is configured; otherwise why the database could not be set up. */
    public String getConfigError() {
        return configError;
    }

    public AuthService auth() {
        return authService;
    }

    public GroupService groups() {
        return groupService;
    }

    public ExpenseService expenses() {
        return expenseService;
    }

    public SplitCalculator splitCalculator() {
        return splitCalculator;
    }

    public BalanceService balances() {
        return balanceService;
    }

    public SettlementService settlements() {
        return settlementService;
    }

    public HistoryService history() {
        return historyService;
    }

    public MonthlySummaryService monthlySummaries() {
        return monthlySummaryService;
    }
}
