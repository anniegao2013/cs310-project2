package com.team8.splitsmart;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static org.hamcrest.CoreMatchers.allOf;

import android.os.SystemClock;

import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.matcher.ViewMatchers.Visibility;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.team8.splitsmart.ui.AuthActivity;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Black-box system tests for the "register an account" requirement. They type into the real
 * registration screen and check only what the user sees. Each test changes one field to an
 * invalid value and keeps the others valid, so the error message identifies the rule under test.
 * Invalid input is rejected before the app contacts Supabase, so no accounts are created.
 */
@RunWith(AndroidJUnit4.class)
public class RegistrationBlackBoxTest {
    private static final String NAME = "Tommy Trojan";
    private static final String EMAIL = "ttrojan@usc.edu";
    private static final String USC_ID = "1234567890";
    private static final String PASSWORD = "fighton";

    @Rule
    public ActivityScenarioRule<AuthActivity> rule = new ActivityScenarioRule<>(AuthActivity.class);

    private static void register(String name, String email, String uscId, String password) {
        onView(withId(R.id.registerTab)).perform(click());
        onView(withId(R.id.nameInput)).perform(scrollTo(), replaceText(name));
        onView(withId(R.id.emailInput)).perform(scrollTo(), replaceText(email));
        onView(withId(R.id.uscIdInput)).perform(scrollTo(), replaceText(uscId));
        onView(withId(R.id.passwordInput)).perform(scrollTo(), replaceText(password), closeSoftKeyboard());
        onView(withId(R.id.submitButton)).perform(scrollTo(), click());
    }

    /** Registration runs on a background thread, so wait up to 5 seconds for the error to appear. */
    private static void assertErrorShown(String message) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (true) {
            try {
                onView(withId(R.id.errorText)).check(matches(allOf(
                        withEffectiveVisibility(Visibility.VISIBLE), withText(message))));
                return;
            } catch (AssertionError | NoMatchingViewException e) {
                if (SystemClock.uptimeMillis() > deadline) {
                    throw e;
                }
                SystemClock.sleep(100);
            }
        }
    }

    @Test
    public void blankNameIsRejected() {
        register("   ", EMAIL, USC_ID, PASSWORD);
        assertErrorShown("Enter your name");
    }

    @Test
    public void nonUscEmailIsRejected() {
        register(NAME, "ttrojan@gmail.com", USC_ID, PASSWORD);
        assertErrorShown("Email must be a USC address ending in @usc.edu");
    }

    @Test
    public void lookalikeUscDomainIsRejected() {
        register(NAME, "ttrojan@usc.edu.com", USC_ID, PASSWORD);
        assertErrorShown("Email must be a USC address ending in @usc.edu");
    }

    @Test
    public void nineDigitUscIdIsRejected() {
        register(NAME, EMAIL, "123456789", PASSWORD);
        assertErrorShown("USC ID must be exactly 10 digits");
    }

    @Test
    public void fiveCharacterPasswordIsRejected() {
        register(NAME, EMAIL, USC_ID, "abc12");
        assertErrorShown("Password must be at least 6 characters");
    }
}
