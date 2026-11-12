# SpendPlan

A native Android app for tracking your budget and planning next month's spending.
Built with Kotlin, Jetpack Compose and Material 3. All data stays on your phone: the app
has **no internet permission** and no analytics.

You don't need Android Studio. GitHub Actions builds the APK for you.

---

## Features

| Area | What it does |
|---|---|
| **Transactions** | Add, edit and delete income and expenses (amount, category, date, note). The list is grouped by day, with filters for month, type and category, plus search. Swipe left to delete, with an **Undo** snackbar. |
| **Categories** | Comes with Food, Transport, Fuel, Bills & Utilities, Rent, Groceries, Health, Education, Entertainment, Shopping, Loan/Lease and Other (expense), plus Salary, Freelance, Hire Driving and Other (income). Add, rename and recolor them, and pick an icon. A category that is in use can only be deleted after its transactions are moved to another category. |
| **Home dashboard** | Month selector; cards for income, expenses, net balance and % of budget used; a spending-by-category donut chart; a daily spending bar chart; the top 5 categories; and budget-vs-actual progress bars. |
| **Plan** | Plan any month (defaults to next month). Set your expected income and a budget per category. **Auto-fill** from last month's actuals or from the average of the last 3 months, then edit the values. Shows the **unallocated** amount and warns when it's negative. Changes save automatically. |
| **Planned payments** | Fixed items such as "Lease payment Rs. 45,000 on the 5th". Tick one to mark it **paid**, which records the real expense. Auto-fill can copy last month's items. |
| **Plan vs. actual** | Progress bars per category: green under 80%, amber at 80–100%, red over 100%. Optional notifications at 80% and 100%. |
| **Month summary** | Planned vs. actual per category, total over/under budget, and your savings. |
| **Recurring** | Set any transaction to repeat monthly (on day N) or weekly. A daily background job creates due entries. If the app wasn't opened for a while, it catches up without creating duplicates. |
| **Data** | Export transactions to CSV, and back up or restore everything as JSON. You choose the file location via Android's file picker, so no storage permission is needed. |
| **Settings** | Currency (default **LKR, Rs.**), month start day 1–28 (for salary-cycle months), budget alerts, and theme (system/light/dark, dynamic color). |

### Tech stack

Kotlin 2.4 · Jetpack Compose (Material 3) · MVVM with ViewModels + StateFlow · Compose
Navigation (type-safe routes) · Room (KSP) · DataStore · WorkManager · kotlinx.serialization ·
charts drawn with Compose Canvas · manual DI (`AppContainer`) · AGP 9.5 / Gradle 9.9 / JDK 17 ·
minSdk 26, target/compile SDK 37.

---

## 1. Get the APK from GitHub Actions

1. Create an **empty** repository on GitHub, e.g. `spendplan`. Don't add a README or a .gitignore.
2. Push this folder to it:

   ```bash
   cd "path/to/this/folder"
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<your-user>/spendplan.git
   git push -u origin main
   ```

   Check that `gradle/wrapper/gradle-wrapper.jar` is included (`git ls-files gradle/wrapper`).
   CI fails without it.

3. On GitHub, open the **Actions** tab. The workflow **Android CI** starts automatically and
   takes about 5–10 minutes. You can also start it by hand: **Actions → Android CI → Run workflow**.
4. When the run shows a green check, click it. At the bottom of the page under
   **Artifacts**, download **SpendPlan-debug-apk**.
5. The download is a `.zip` file. Unzip it to get `app-debug.apk`.

## 2. Install the APK on your Android phone

1. Copy `app-debug.apk` to your phone (USB, Google Drive, email to yourself, etc.).
2. Open the file on the phone, for example in the **Files** app.
3. Android asks for permission to install apps from that source. Tap **Settings** and turn
   on **Allow from this source** (*Install unknown apps*) for the app you're opening it with,
   such as Files or Chrome. Then go back.
4. Tap **Install**. If Play Protect warns about an unknown developer, choose **Install anyway**.
   This is expected for an app you built yourself.

Updating: install a newer APK over the old one and your data is kept. The debug app
(`com.spendplan.app.debug`) and the release app (`com.spendplan.app`) are separate apps,
so their data isn't shared. Use **Settings → Back up / Restore** to move data between them.

## 3. Signed release builds (optional)

When you push a tag such as `v1.0.0`, the workflow also builds a **signed release APK** and
attaches it to a GitHub Release. That requires a signing key stored in repository secrets.

### Create a keystore (one time)

`keytool` comes with any JDK (Java 17+):

```bash
keytool -genkeypair -v \
  -keystore spendplan-release.jks \
  -alias spendplan \
  -keyalg RSA -keysize 4096 -validity 10000
```

It asks for a keystore password, your name and so on, and optionally a key password
(press Enter to reuse the keystore password).

> **Keep `spendplan-release.jks` and its passwords safe and private, and never commit them.**
> If you lose the key, you can't publish updates that install over the old version.
> `.gitignore` already excludes `*.jks` and `*.keystore`.

### Base64-encode it

```bash
# Linux
base64 -w 0 spendplan-release.jks > keystore.b64
# macOS
base64 -i spendplan-release.jks -o keystore.b64
```

```powershell
# Windows (PowerShell)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("spendplan-release.jks")) | Set-Content keystore.b64
```

### Add the 4 secrets

On GitHub, go to **Settings → Secrets and variables → Actions → New repository secret** and add:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | the entire contents of `keystore.b64` |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | `spendplan` (the `-alias` you used) |
| `KEY_PASSWORD` | the key password (the same as the keystore password if you pressed Enter) |

Delete `keystore.b64` afterwards.

### Publish a release

```bash
git tag v1.0.0
git push origin v1.0.0
```

The run then builds `SpendPlan-v1.0.0.apk`, signs it, and attaches it to a new release under
**Releases**. The version name comes from the tag and the version code from the run number.
If the secrets are missing, the build still succeeds but the APK is **unsigned**
(`…-unsigned.apk`, which can't be installed). The workflow logs a warning in that case.

---

## Building locally (optional)

Requirements: JDK 17 and the Android SDK (platform 37). Point `ANDROID_HOME`, or a
`local.properties` file containing `sdk.dir=...`, at the SDK, then run:

```bash
./gradlew testDebugUnitTest assembleDebug   # APK in app/build/outputs/apk/debug/
./gradlew lintDebug                          # lint report (never fails the build)
```

## Project layout

```
app/src/main/java/com/spendplan/app/
├── domain/        Pure Kotlin logic (unit tested): money format, budget periods,
│                  budget calculations, auto-fill, recurrence scheduling, CSV
├── data/          Room entities/DAOs/database, repositories, DataStore settings, backup
├── work/          WorkManager job for recurring transactions
├── notify/        Budget alert notifications
└── ui/            Compose screens (home, transactions, plan, settings), theme, charts
app/schemas/       Exported Room schemas (commit these, they're used for migrations)
app/src/test/      JVM unit tests
```

### Database migrations

The database is created with `exportSchema = true`, and the schema JSON is written to
`app/schemas/`. To change the schema: bump `AppDatabase.VERSION`, build once so the new
schema is exported, then add an `AutoMigration` or a `Migration` in `Migrations.ALL`.
The app never uses destructive migration, so user data is never silently wiped.
