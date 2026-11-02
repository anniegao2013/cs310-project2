package com.team8.splitsmart.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.team8.splitsmart.R;
import com.team8.splitsmart.SplitSmartApp;
import com.team8.splitsmart.data.User;
import com.team8.splitsmart.usergroup.AuthService;

/** Register and log-in screens. */
public class AuthActivity extends AppCompatActivity {
    private AuthService authService;
    private boolean registering;

    private TextInputLayout nameLayout;
    private TextInputLayout uscIdLayout;
    private TextInputEditText nameInput;
    private TextInputEditText emailInput;
    private TextInputEditText uscIdInput;
    private TextInputEditText passwordInput;
    private TextView errorText;
    private Button submitButton;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        nameLayout = findViewById(R.id.nameLayout);
        uscIdLayout = findViewById(R.id.uscIdLayout);
        nameInput = findViewById(R.id.nameInput);
        emailInput = findViewById(R.id.emailInput);
        uscIdInput = findViewById(R.id.uscIdInput);
        passwordInput = findViewById(R.id.passwordInput);
        errorText = findViewById(R.id.errorText);
        submitButton = findViewById(R.id.submitButton);
        progress = findViewById(R.id.progress);

        MaterialButtonToggleGroup toggle = findViewById(R.id.modeToggle);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                setRegistering(checkedId == R.id.registerTab);
            }
        });
        submitButton.setOnClickListener(v -> {
            if (registering) {
                onRegisterClicked();
            } else {
                onLoginClicked();
            }
        });

        SplitSmartApp app = SplitSmartApp.get();
        if (app.getConfigError() != null) {
            showError("App is not configured: " + app.getConfigError());
            submitButton.setEnabled(false);
            return;
        }
        authService = app.auth();
    }

    private void setRegistering(boolean registering) {
        this.registering = registering;
        nameLayout.setVisibility(registering ? View.VISIBLE : View.GONE);
        uscIdLayout.setVisibility(registering ? View.VISIBLE : View.GONE);
        submitButton.setText(registering ? R.string.create_account : R.string.log_in);
        errorText.setVisibility(View.GONE);
    }

    public void onRegisterClicked() {
        String name = text(nameInput);
        String email = text(emailInput);
        String uscId = text(uscIdInput);
        String password = text(passwordInput);
        submit(() -> authService.register(name, email, uscId, password));
    }

    public void onLoginClicked() {
        String email = text(emailInput);
        String password = text(passwordInput);
        submit(() -> authService.login(email, password));
    }

    private void submit(UiAsync.Work<User> work) {
        errorText.setVisibility(View.GONE);
        setBusy(true);
        UiAsync.run(this, work, user -> {
            setBusy(false);
            openGroups();
        }, message -> {
            setBusy(false);
            showError(message);
        });
    }

    private void openGroups() {
        Intent intent = new Intent(this, GroupActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    public void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        submitButton.setEnabled(!busy);
    }

    static String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}
