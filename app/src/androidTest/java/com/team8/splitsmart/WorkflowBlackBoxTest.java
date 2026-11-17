package com.team8.splitsmart;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;

import android.os.SystemClock;
import android.view.View;

import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.matcher.ViewMatchers.Visibility;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.team8.splitsmart.ui.AuthActivity;

import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Black-box system tests for the login, group-membership and expense workflows. Each test drives the
 * real screens and checks only what a user can see. The tests use a fresh account (and, for the
 * split test, a second account) so they never depend on data left by another test.
 */
@RunWith(AndroidJUnit4.class)
public class WorkflowBlackBoxTest {
    private static final String PASSWORD = "fighton123";
    private static final long NETWORK_TIMEOUT = 45_000;
    private static final long DIALOG_TIMEOUT = 10_000;

    /** Ten-digit USC IDs and matching emails that are unique for every account created. */
    private static final AtomicLong IDS =
            new AtomicLong(System.currentTimeMillis() % 1_000_000_000L);

    @Rule
    public ActivityScenarioRule<AuthActivity> rule = new ActivityScenarioRule<>(AuthActivity.class);

    private static final class Account {
        final String name;
        final String email;
        final String uscId;

        Account(String label) {
            long value = 1_000_000_000L + Math.floorMod(IDS.incrementAndGet(), 1_000_000_000L);
            this.uscId = String.valueOf(value);
            this.email = "bb" + uscId + "@usc.edu";
            this.name = "BB " + label + " " + uscId;
        }
    }

    // ----- Helpers -----

    private static void waitForView(Matcher<View> matcher) {
        waitForView(matcher, NETWORK_TIMEOUT);
    }

    private static void waitForView(Matcher<View> matcher, long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        AssertionError last = null;
        while (SystemClock.uptimeMillis() <= deadline) {
            try {
                onView(matcher).check(matches(isDisplayed()));
                return;
            } catch (AssertionError e) {
                last = e;
            } catch (NoMatchingViewException e) {
                last = new AssertionError(e);
            } catch (RuntimeException e) {
                last = new AssertionError(e);
            }
            SystemClock.sleep(200);
        }
        if (last != null) {
            throw last;
        }
        throw new AssertionError("View did not appear: " + matcher);
    }

    private static void waitForText(String text) {
        waitForView(allOf(withText(text), withEffectiveVisibility(Visibility.VISIBLE)));
    }

    private static void openOverflowMenu() {
        openActionBarOverflowOrOptionsMenu(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    private static void register(Account account) {
        onView(withId(R.id.registerTab)).perform(click());
        onView(withId(R.id.nameInput)).perform(scrollTo(), replaceText(account.name));
        onView(withId(R.id.emailInput)).perform(scrollTo(), replaceText(account.email));
        onView(withId(R.id.uscIdInput)).perform(scrollTo(), replaceText(account.uscId));
        onView(withId(R.id.passwordInput))
                .perform(scrollTo(), replaceText(PASSWORD), closeSoftKeyboard());
        onView(withId(R.id.submitButton)).perform(scrollTo(), click());
        waitForView(allOf(withId(R.id.fab), withText("New group")));
    }

    private static void logIn(Account account, String password) {
        onView(withId(R.id.loginTab)).perform(click());
        onView(withId(R.id.emailInput)).perform(scrollTo(), replaceText(account.email));
        onView(withId(R.id.passwordInput))
                .perform(scrollTo(), replaceText(password), closeSoftKeyboard());
        onView(withId(R.id.submitButton)).perform(scrollTo(), click());
    }

    private static void logOut() {
        openOverflowMenu();
        onView(withText("Log out")).perform(click());
        waitForView(allOf(withId(R.id.loginTab), isDisplayed()));
    }

    private static void createGroup(String name) {
        waitForView(allOf(withId(R.id.fab), withText("New group")));
        onView(withId(R.id.fab)).perform(click());
        waitForView(allOf(withId(R.id.firstInput), isDisplayed()), DIALOG_TIMEOUT);
        onView(withId(R.id.firstInput)).perform(replaceText(name), closeSoftKeyboard());
        onView(withId(android.R.id.button1)).perform(click());
        waitForView(allOf(withId(R.id.fab), withText("Add expense")));
    }

    private static void openMembers() {
        openOverflowMenu();
        onView(withText("Members")).perform(click());
        waitForView(allOf(withId(R.id.fab), withText("Add member")));
    }

    private static void addMember(String email) {
        onView(withId(R.id.fab)).perform(click());
        waitForView(allOf(withId(R.id.firstInput), isDisplayed()), DIALOG_TIMEOUT);
        onView(withId(R.id.firstInput)).perform(replaceText(email), closeSoftKeyboard());
        onView(withId(android.R.id.button1)).perform(click());
    }

    private static void openExpenseForm() {
        waitForView(allOf(withId(R.id.fab), withText("Add expense")));
        onView(withId(R.id.fab)).perform(click());
        waitForView(allOf(withId(R.id.titleInput), isDisplayed()));
    }

    private static void saveExpense(String title, String amount, Account partner) {
        onView(withId(R.id.titleInput)).perform(scrollTo(), replaceText(title), closeSoftKeyboard());
        onView(withId(R.id.amountInput))
                .perform(scrollTo(), replaceText(amount), closeSoftKeyboard());
        onView(allOf(withId(R.id.checkbox), withText(partner.name))).perform(scrollTo(), click());
        onView(withId(R.id.saveButton)).perform(scrollTo(), click());
        waitForView(allOf(withId(R.id.title), withText(title)));
    }

    // ----- BB-6 through BB-10 -----

    /** BB-6: a wrong password is rejected and the correct password logs the same user in. */
    @Test
    public void wrongPasswordThenCorrectLoginSucceeds() {
        Account account = new Account("Login");
        register(account);
        logOut();

        logIn(account, "definitely-wrong");
        waitForText("Incorrect email or password");

        onView(withId(R.id.passwordInput))
                .perform(scrollTo(), replaceText(PASSWORD), closeSoftKeyboard());
        onView(withId(R.id.submitButton)).perform(scrollTo(), click());
        waitForView(allOf(withId(R.id.fab), withText("New group")));
    }

    /** BB-7: creating a group with a blank name shows an error on the name field. */
    @Test
    public void blankGroupNameIsRejected() {
        register(new Account("Group"));

        waitForView(allOf(withId(R.id.fab), withText("New group")));
        onView(withId(R.id.fab)).perform(click());
        waitForView(allOf(withId(R.id.firstInput), isDisplayed()), DIALOG_TIMEOUT);

        onView(withId(android.R.id.button1)).perform(click());
        waitForText("Group name cannot be empty");
        onView(withId(R.id.firstInput)).check(matches(isDisplayed()));
    }

    /** BB-8: adding an email that has no account shows the unregistered-user error. */
    @Test
    public void unregisteredMemberEmailIsRejected() {
        Account account = new Account("Owner");
        register(account);
        createGroup("BB group " + account.uscId);
        openMembers();

        String unknownEmail = "ghost" + account.uscId + "@usc.edu";
        addMember(unknownEmail);
        waitForText("No registered user has the email " + unknownEmail);
    }

    /** BB-9: a zero amount is rejected on the form and the expense cannot be saved. */
    @Test
    public void nonPositiveExpenseAmountIsRejected() {
        Account account = new Account("Amount");
        register(account);
        createGroup("BB amount group " + account.uscId);
        openExpenseForm();

        onView(withId(R.id.titleInput))
                .perform(scrollTo(), replaceText("Zero amount"), closeSoftKeyboard());
        onView(withId(R.id.amountInput))
                .perform(scrollTo(), replaceText("0"), closeSoftKeyboard());

        waitForText("Amount must be greater than $0.00");
        onView(withId(R.id.saveButton)).check(matches(not(isEnabled())));
    }

    /** BB-10: an equal split is saved, listed in history, and produces a $5.00 balance. */
    @Test
    public void equalSplitExpenseAppearsInHistoryAndBalances() {
        Account owner = new Account("Payer");
        Account partner = new Account("Partner");

        // Create both accounts through the real registration screen.
        register(partner);
        logOut();
        register(owner);

        String groupName = "BB split " + owner.uscId;
        createGroup(groupName);

        openMembers();
        addMember(partner.email);
        // Wait for the new row in the member list (not the still-open dialog's email field).
        waitForView(allOf(withId(R.id.secondary), withText(partner.email), isDisplayed()));
        pressBack();
        waitForView(allOf(withId(R.id.fab), withText("Add expense")));

        openExpenseForm();
        saveExpense("Dinner", "10.00", partner);

        onView(allOf(withId(R.id.amount), withText("$10.00"))).check(matches(isDisplayed()));

        openOverflowMenu();
        onView(withText("Balances")).perform(click());
        waitForView(allOf(withId(R.id.secondary), withText(partner.name + " owes you")));
        onView(allOf(withId(R.id.trailing), withText("$5.00"))).check(matches(isDisplayed()));
    }
}
