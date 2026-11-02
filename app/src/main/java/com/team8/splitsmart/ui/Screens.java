package com.team8.splitsmart.ui;

import android.app.Activity;
import android.content.Intent;

import com.team8.splitsmart.SplitSmartApp;
import com.team8.splitsmart.data.User;

/** Shared helpers for screens. */
final class Screens {
    private Screens() {
    }

    /**
     * Returns the logged-in user, or sends the user back to the login screen and returns null,
     * e.g. after Android recreated the app process.
     */
    static User requireUser(Activity activity) {
        SplitSmartApp app = SplitSmartApp.get();
        User user = app.getConfigError() == null ? app.auth().getCurrentUser() : null;
        if (user == null) {
            Intent intent = new Intent(activity, AuthActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(intent);
            activity.finish();
        }
        return user;
    }
}
