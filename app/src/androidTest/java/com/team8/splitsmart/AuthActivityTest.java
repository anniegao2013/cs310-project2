package com.team8.splitsmart;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.espresso.matcher.ViewMatchers.Visibility;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.team8.splitsmart.ui.AuthActivity;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Espresso smoke tests for the login/register screen. These don't touch Supabase. */
@RunWith(AndroidJUnit4.class)
public class AuthActivityTest {

    @Rule
    public ActivityScenarioRule<AuthActivity> rule = new ActivityScenarioRule<>(AuthActivity.class);

    @Test
    public void loginModeHidesRegistrationFields() {
        onView(withId(R.id.loginTab)).perform(click());
        onView(withId(R.id.nameLayout)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        onView(withId(R.id.uscIdLayout)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        onView(withId(R.id.submitButton)).check(matches(withText(R.string.log_in)));
    }

    @Test
    public void registerTabShowsRegistrationFields() {
        onView(withId(R.id.registerTab)).perform(click());
        onView(withId(R.id.nameLayout)).check(matches(isDisplayed()));
        onView(withId(R.id.uscIdLayout)).check(matches(isDisplayed()));
        onView(withId(R.id.submitButton)).check(matches(withText(R.string.create_account)));
    }
}
