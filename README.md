# SplitSmart

Android app for splitting group expenses, built by Team 8 for CSCI 310. It's written in Java and uses Supabase as its backend.

## Setup

You need [Android Studio](https://developer.android.com/studio) and a Supabase project.

### 1. Open the project

Clone the repo and open the folder in Android Studio. It creates a `local.properties` file in the project root and sets `sdk.dir` to your Android SDK location. Leave that line as it is. The file is git-ignored, so everyone has their own copy.

### 2. Set up the database

If your Supabase project is new, paste the contents of `supabase/schema.sql` into the SQL Editor in the Supabase dashboard and run it. Skip this step if you're using a project that's already set up.

### 3. Add your Supabase keys

Add these two lines to `local.properties`. In Android Studio, the file is listed under **Gradle Scripts**.

```properties
SUPABASE_URL=https://your-project-ref.supabase.co
SUPABASE_ANON_KEY=your-publishable-key
```

Don't put quotes around the values, and don't add `/rest/v1` to the URL.

| Value | Where to find it in the Supabase dashboard |
|---|---|
| `SUPABASE_URL` | **Project URL**, from the **Connect** button or **Project Settings → Data API** |
| `SUPABASE_ANON_KEY` | **Publishable key** (`sb_publishable_...`) or the legacy **`anon` `public`** key (`eyJ...`), from **Project Settings → API Keys** |

Never use the **secret** (`sb_secret_...`) or **`service_role`** key. They bypass row-level security, and anything in this file gets built into the APK.

### 4. Run the app

Select an emulator or device and click **Run**. The keys are read at build time, so rebuild the app after changing them.

If the keys are missing, the app still builds but can't connect to Supabase.

## Building from the terminal

```sh
./gradlew assembleDebug
```

If you haven't opened the project in Android Studio, `local.properties` won't exist yet. Create it with your keys, then either add a `sdk.dir` line or set `ANDROID_HOME` to your SDK location. On a Mac the default location is `~/Library/Android/sdk`.

## Running tests

The project has two kinds of tests:

| Kind | Folder | Runs on | Used for |
|---|---|---|---|
| JUnit unit tests | `app/src/test/` | Your computer's JVM | Logic classes like `SplitCalculator` and `AmountValidator` |
| Espresso UI tests | `app/src/androidTest/` | An emulator or device | Screens and user interaction |

The dependencies are already in `app/build.gradle`, so there's nothing to install. Gradle downloads them the first time you sync or build.

### JUnit unit tests

In Android Studio, right-click `app/src/test/java` and choose **Run 'Tests in ...'**. You can also click the green arrow next to any test class or method.

From the terminal:

```sh
./gradlew testDebugUnitTest
```

The HTML report is written to `app/build/reports/tests/testDebugUnitTest/index.html`.

If the terminal build fails with `Unsupported class file major version 69`, Gradle is running on Java 25, which this Gradle version doesn't support. Point `JAVA_HOME` at a JDK 17 or 21 install before running `./gradlew`. Android Studio doesn't have this problem because it uses its own Gradle JDK setting.

### Espresso UI tests

1. Start an emulator or connect a device with USB debugging on.
2. Turn off animations on it, or Espresso tests can be flaky. In **Settings → Developer options**, set **Window animation scale**, **Transition animation scale** and **Animator duration scale** to **Off**.
3. Right-click `app/src/androidTest/java` and choose **Run 'Tests in ...'**.

From the terminal:

```sh
./gradlew connectedDebugAndroidTest
```

The HTML report is written to `app/build/reports/androidTests/connected/debug/index.html`.

### Writing a new test

- Logic that doesn't need Android classes goes in `app/src/test/java/com/team8/splitsmart/`. Annotate methods with `@Test` and use `org.junit.Assert`.
- UI tests go in `app/src/androidTest/java/com/team8/splitsmart/`. Use `AuthActivityTest` as a template: annotate the class with `@RunWith(AndroidJUnit4.class)`, launch the screen with `ActivityScenarioRule`, then use `onView(withId(...)).perform(...)` and `.check(matches(...))`.
- UI tests that log in or load data talk to the Supabase project in your `local.properties`, so use a test account rather than real data.
