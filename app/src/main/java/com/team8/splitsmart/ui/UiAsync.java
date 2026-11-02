package com.team8.splitsmart.ui;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;
import com.team8.splitsmart.ValidationException;
import com.team8.splitsmart.data.DataException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Runs service calls off the main thread and delivers results (or a readable error) back on it. */
public final class UiAsync {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private UiAsync() {
    }

    public interface Work<T> {
        T run();
    }

    public interface Callback<T> {
        void accept(T value);
    }

    public static <T> void run(Activity activity, Work<T> work, Callback<T> onSuccess) {
        run(activity, work, onSuccess, message -> showError(activity, message));
    }

    public static <T> void run(Activity activity, Work<T> work, Callback<T> onSuccess, Callback<String> onError) {
        EXECUTOR.execute(() -> {
            try {
                T result = work.run();
                MAIN.post(() -> {
                    if (isAlive(activity)) {
                        onSuccess.accept(result);
                    }
                });
            } catch (RuntimeException e) {
                String message = messageFor(e);
                MAIN.post(() -> {
                    if (isAlive(activity)) {
                        onError.accept(message);
                    }
                });
            }
        });
    }

    public static void showError(Activity activity, String message) {
        View root = activity.findViewById(android.R.id.content);
        Snackbar.make(root, message, Snackbar.LENGTH_LONG).show();
    }

    private static String messageFor(RuntimeException e) {
        if (e instanceof ValidationException || e instanceof DataException) {
            return e.getMessage();
        }
        return "Something went wrong: " + e.getMessage();
    }

    private static boolean isAlive(Activity activity) {
        return !activity.isFinishing() && !activity.isDestroyed();
    }
}
