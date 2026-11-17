# SplitSmart Testing

This document covers 10 black-box tests (5 registration tests and 5 workflow tests) and 10 white-box tests. All 20 pass: the black-box tests on the Pixel_10 emulator (Android 17) and the white-box tests on the JVM.

**How to run them**

| Tests | Run all | Run one |
|---|---|---|
| Black-box (Espresso, needs an emulator or device) | `./gradlew connectedDebugAndroidTest` | `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.team8.splitsmart.RegistrationBlackBoxTest#<test name>`<br>`./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.team8.splitsmart.WorkflowBlackBoxTest#<test name>` |
| White-box (JUnit) | `./gradlew testDebugUnitTest` | `./gradlew testDebugUnitTest --tests "com.team8.splitsmart.WhiteBoxTest.<test name>"` |

In Android Studio you can also open the test file and click the green arrow next to a test or class.

## Black-box tests

**File:** `app/src/androidTest/java/com/team8/splitsmart/RegistrationBlackBoxTest.java`

**Requirement tested:** a user registers with a name, a USC email address, a 10-digit USC ID and a password.

These are system-level tests. Each one launches the real login screen, switches to **Register**, fills in the form the way a user would, taps **Create account**, and checks the error message shown on screen. They don't use any knowledge of the code beyond the view IDs.

**How the inputs were chosen:** we used equivalence partitioning and boundary value analysis on each field from the requirement. Every test starts from a known-valid form (`Tommy Trojan`, `ttrojan@usc.edu`, `1234567890`, `fighton`) and changes exactly one field to a value from an invalid partition. That way, if the test fails, the error points at a single rule. Because invalid input is rejected before the app contacts Supabase, these tests never create accounts in the database.

### BB-1 `blankNameIsRejected`

- **What it does:** registers with a name of three spaces and checks that "Enter your name" is shown.
- **Rationale:** an empty name is the obvious invalid case, but a name made only of spaces is the trickier one: it looks non-empty to a naive check. Spaces only tests that the app trims the name before validating it.
- **Bugs found:** none.

### BB-2 `nonUscEmailIsRejected`

- **What it does:** registers with `ttrojan@gmail.com` and checks that "Email must be a USC address ending in @usc.edu" is shown.
- **Rationale:** the requirement limits accounts to USC students. A well-formed email from another domain is the most common invalid partition, so it's the representative value for "valid email format, wrong domain."
- **Bugs found:** none.

### BB-3 `lookalikeUscDomainIsRejected`

- **What it does:** registers with `ttrojan@usc.edu.com` and checks for the same USC email error.
- **Rationale:** this address contains `@usc.edu` but doesn't end with it. A check written as "contains `usc.edu`" or "has `@usc.edu` somewhere" would wrongly accept it, so this input tests that the app checks the whole domain.
- **Bugs found:** none.

### BB-4 `nineDigitUscIdIsRejected`

- **What it does:** registers with the USC ID `123456789` and checks that "USC ID must be exactly 10 digits" is shown.
- **Rationale:** boundary value analysis on the length rule. 10 digits is valid, so 9 digits is the closest invalid value below the boundary. The field has `maxLength="10"`, so 11 digits can't be typed in the first place, which is why we test the lower side.
- **Bugs found:** none.

### BB-5 `fiveCharacterPasswordIsRejected`

- **What it does:** registers with the password `abc12` and checks that "Password must be at least 6 characters" is shown.
- **Rationale:** boundary value analysis on the minimum length. 6 is the smallest valid length, so 5 is the boundary from the invalid side. White-box test WB-1 checks the valid side (exactly 6).
- **Bugs found:** none.

**Workflow file:** `app/src/androidTest/java/com/team8/splitsmart/WorkflowBlackBoxTest.java`

**Author of BB-6 through BB-10:** Annie Gao

**Run one workflow test:** `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.team8.splitsmart.WorkflowBlackBoxTest#<test name>`

**Requirements tested:** logging in, creating a group, adding a member, rejecting an invalid expense amount, and splitting an expense between two members.

These tests drive the real screens after registration. Unlike the registration tests, they create real accounts and data through the UI, so each test generates a unique 10-digit USC ID and matching `@usc.edu` address. Test data never depends on data left by another test.

### BB-6 `wrongPasswordThenCorrectLoginSucceeds`

- **What it does:** registers an account, logs out, tries to log in with the wrong password, checks for "Incorrect email or password", then retries with the correct password and checks that the group list opens.
- **Rationale:** login has two outcomes that matter to the user: rejected credentials and a successful session. Testing both in one sequence also checks that a rejected attempt leaves the screen usable for a retry.
- **Bugs found:** none.

### BB-7 `blankGroupNameIsRejected`

- **What it does:** opens **New group**, leaves the name blank, taps **Save**, and checks for "Group name cannot be empty" on the name field.
- **Rationale:** a group needs a name. The blank case is the invalid partition, and checking the field error verifies the app keeps the dialog open instead of creating a nameless group.
- **Bugs found:** none.

### BB-8 `unregisteredMemberEmailIsRejected`

- **What it does:** creates a group, opens **Members**, tries to add a `@usc.edu` address with no account, and checks for "No registered user has the email ...".
- **Rationale:** a well-formed but unknown email is different from a malformed address. The message must identify the address so the owner can correct it.
- **Bugs found:** none.

### BB-9 `nonPositiveExpenseAmountIsRejected`

- **What it does:** opens the add-expense form, enters a title and the amount `0`, and checks for "Amount must be greater than $0.00" and that **Save expense** stays disabled.
- **Rationale:** zero is the boundary between valid and invalid amounts. Checking both the message and the disabled button verifies the user cannot submit the invalid value.
- **Bugs found:** none.

### BB-10 `equalSplitExpenseAppearsInHistoryAndBalances`

- **What it does:** registers two accounts, creates a group, adds the second account as a member, saves a `$10.00` expense split equally, and checks that the expense appears in history and that the partner owes `$5.00` on the balance page.
- **Rationale:** this is the core end-to-end workflow. The expected `$5.00` is computed from the equal-split rule, so the test checks both the saved expense and the derived balance.
- **Bugs found:** none.

## White-box tests

**File:** `app/src/test/java/com/team8/splitsmart/WhiteBoxTest.java`

**Author of WB-6 through WB-10:** Annie Gao

These are unit tests written by reading the source code. We first ran the existing unit tests with coverage on, looked for branches they never took, and wrote each test to take those branches. The database is replaced by small fake `UserRepository`, `GroupRepository` and `ExpenseRepository` classes, so the tests run in milliseconds and don't need a network.

### WB-1 `registerPasswordLengthBoundary`

- **Code under test:** `AuthService.register` in `app/src/main/java/com/team8/splitsmart/usergroup/AuthService.java`
- **What it does:** registers with a 5-character password and expects a rejection, with nothing saved. Then it registers with exactly 6 characters, padded name, mixed-case email and padded USC ID, and checks that the account is saved trimmed and lowercased and becomes the current user.
- **Branches covered:** both outcomes of `password.length() < MIN_PASSWORD_LENGTH`, the success path through `userRepo.save`, and the assignment of `currentUser`.
- **Result:** pass.
- **Bugs found:** none.

### WB-2 `registerDuplicateAccountBranches`

- **Code under test:** `AuthService.register`
- **What it does:** makes the fake repository report an existing email, then an existing USC ID, then makes `save` throw `DataException.UniqueViolation`. It checks the error message for each case and that nobody ends up logged in.
- **Branches covered:** the `existsByEmail` branch, the `existsByUscId` branch, and the `catch (DataException.UniqueViolation e)` block. The catch block only runs when two people register with the same email at the same moment, which can't be reproduced reliably by hand.
- **Result:** pass.
- **Bugs found:** none.

### WB-3 `percentageSplitTieGoesToFirstParticipant`

- **Code under test:** `PercentageSplitStrategy.calculate` in `app/src/main/java/com/team8/splitsmart/split/PercentageSplitStrategy.java`
- **What it does:** splits 1 cent 50/50 between two people. Both are owed half a cent, so their rounding remainders are equal. It checks that the first participant gets the cent, the second gets 0, and the shares add up to the total.
- **Branches covered:** the tie-breaking side of the remainder comparator (`b[1] == a[1]`, so participant index decides). None of the existing tests produced two equal remainders.
- **Result:** pass.
- **Bugs found:** none.

### WB-4 `percentageInputOverOneHundredIsInvalidInput`

- **Code under test:** `PercentageSplitStrategy.toBasisPoints`
- **What it does:** tries four inputs: `100.01` for one person (over 100%), `33.333` (three decimals), a missing value for one person, and `100%` / `0%` with percent signs. The first three must be invalid. The last must be valid, with the first person paying everything.
- **Branches covered:** `points > FULL`, the regex mismatch, `text == null`, and the `%`-stripping valid path. That takes `toBasisPoints` from partial to 100% branch coverage.
- **Result:** pass.
- **Bugs found:** none.

### WB-5 `pairwiseIgnoresOtherPairsAndHandlesUserAsPayee`

- **Code under test:** `BalanceCalculator.computePairwise` in `app/src/main/java/com/team8/splitsmart/balance/BalanceCalculator.java`
- **What it does:** computes Alice's balances from an expense between Bob and Carol, an expense Alice paid for Bob ($15.00 share), a $5.00 settlement from Bob to Alice, and a settlement from Carol to Bob. It checks that Bob owes Alice exactly $10.00 and that Carol doesn't appear at all.
- **Branches covered:** the expense split where the user is neither payer nor participant, the settlement where the user is the payee, and the settlement where the user is neither side.
- **Result:** pass.
- **Bugs found:** none.

### WB-6 `loginRejectsBadCredentialsAndNormalizesEmail`

- **Code under test:** `AuthService.login` in `app/src/main/java/com/team8/splitsmart/usergroup/AuthService.java`
- **What it does:** tries an unknown email, a wrong password, a null email and a null password, and checks the same "Incorrect email or password" message with nobody logged in. Then logs in with `  Tommy@USC.edu ` and checks that the email is lowercased and trimmed, the stored user is returned and becomes the current user; finally logs out and checks the session is cleared.
- **Branches covered:** both sides of `user == null || !hasher.verify(...)`, the null sides of `normalizeEmail` and the password check, and `logout()` clearing `currentUser`.
- **Result:** pass.
- **Bugs found:** none.

### WB-7 `updateNameRequiresLoginAndTrimsName`

- **Code under test:** `AuthService.updateName`
- **What it does:** calls `updateName` while logged out and expects "Please log in again". Registers a user, tries a spaces-only name and `null`, then updates to `"  Tommy Trojan  "` and checks the trimmed name, the repository call, the user ID and the cached current user.
- **Branches covered:** the empty-name check before the session check, both null/blank name paths, and the successful repository update and cached-user replacement.
- **Result:** pass.
- **Bugs found:** none.

### WB-8 `groupCreationAndMembershipValidationBranches`

- **Code under test:** `GroupService.createGroup`, `addMember`, `editGroup`, `getGroup` and `deleteGroup`
- **What it does:** checks blank/null group names and descriptions, adds a member by mixed-case email, then checks the blank-email, unknown-email, not-owner, already-member and missing-group errors. It also edits the group and checks the updated name/description, then checks that only the owner can delete it.
- **Branches covered:** both sides of the name/description cleaning ternaries, every `addMember` rejection, both sides of `requireOwner`, the missing-group branch, and the owner-only delete path.
- **Result:** pass.
- **Bugs found:** none.

### WB-9 `expenseDraftValidationRejectsEachRule`

- **Code under test:** `ExpenseService.validateDraft`
- **What it does:** changes one rule at a time: null/blank title, zero amount, missing category, payer not participating, fewer than two participants, and a participant who is not a group member. It then checks that a valid `$12.34` draft returns `1234` cents.
- **Branches covered:** every validation branch in `validateDraft`, including the `memberIds.containsAll(participants)` success and failure paths.
- **Result:** pass.
- **Bugs found:** none.

### WB-10 `expenseCreationVerifiesSplitAndEnforcesCreator`

- **Code under test:** `ExpenseService.createExpense`, `updateExpense` and `deleteExpense`
- **What it does:** checks that a non-member cannot create an expense, that a null or invalid split is rejected, that the split must contain exactly the selected participants and add up to the total, and that notes are trimmed. It then verifies the saved expense fields and two saved splits, checks that only the creator may update or delete, and checks the fields after an update.
- **Branches covered:** both sides of the creator-membership check, the null/invalid-split branches, both split-mismatch branches, the blank-note normalization, and both creator-permission checks.
- **Result:** pass.
- **Bugs found:** none.

## Coverage

**Criterion:** branch coverage (every `if`, loop condition, ternary and `catch` taken both ways). We picked branch coverage over statement coverage because the core logic is mostly validation rules and rounding decisions, where a statement can run while one side of its condition is never tested. Coverage is measured with JaCoCo, which is turned on for debug unit tests in `app/build.gradle`.

**To generate the report:** `./gradlew createDebugUnitTestCoverageReport`, then open `app/build/reports/coverage/test/debug/html/index.html`.

**Results with all unit tests** (the 10 white-box tests plus the other unit tests):

| Method | Branch coverage |
|---|---|
| `BalanceCalculator.computePairwise` | 16 / 16 (100%) |
| `PercentageSplitStrategy.calculate` | 10 / 10 (100%) |
| `PercentageSplitStrategy.toBasisPoints` | 6 / 6 (100%) |
| `AuthService.register` | 12 / 18 (67%) |
| `AuthService.login` | 3 / 3 (100%) |
| `AuthService.updateName` | 4 / 4 (100%) |
| `AuthService.normalizeEmail` | 2 / 2 (100%) |
| `GroupService` (all methods) | 20 / 20 (100%) |
| `ExpenseService` (all methods) | 35 / 36 (97%) |

The 6 branches still missed in `AuthService.register` are the `null` sides of the input-cleaning ternaries and the invalid-email, invalid-USC-ID and null-password rejections. The UI never passes `null` (it passes an empty string instead). The invalid-email and invalid-USC-ID rejections are covered at system level by BB-2, BB-3 and BB-4, but those run on the device, and JaCoCo here only measures the JVM unit tests. The one missed branch in `ExpenseService` is the `null` side of the note-cleaning ternary in `applyDraft`; the form always passes a string.

## Bugs found and changes made

None of the 20 tests (10 black-box and 10 white-box) found a defect in the app, so no app code was changed for them. These setup problems came up while getting the tests to run:

| Problem | Change | Reason |
|---|---|---|
| Espresso 3.6.1 crashed on the Android 17 emulator with `NoSuchMethodException: InputManager.getInstance` | Upgraded to Espresso 3.7.0 and AndroidX Test 1.7.0 in `app/build.gradle` | The older Espresso calls a hidden Android method that was removed in newer Android versions |
| Gradle failed in the terminal with `Unsupported class file major version 69` | Run Gradle with JDK 21 (`JAVA_HOME`) | Gradle 8.7 doesn't support Java 25, which was the default on this machine |
| No coverage numbers for white-box testing | Added `enableUnitTestCoverage true` to the debug build type | Needed to measure branch coverage |
| The Pixel_4 (Android 14) emulator showed "Process system isn't responding" and Espresso could not get window focus | Ran the black-box suite on the Pixel_10 (Android 17) emulator | The Android 14 emulator was unstable under memory pressure; the same tests pass on Android 17 |
