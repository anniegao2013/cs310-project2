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
