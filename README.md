# Pocket Ledger

Pocket Ledger is an offline-first Android bookkeeping app built with Kotlin, Jetpack Compose, Room, and MVVM.

## Run

1. Open this folder in Android Studio Panda 3 (2025.3.3) or newer.
2. Let Android Studio install JDK 17, Android SDK 36, and sync Gradle 9.4.1 through the included wrapper.
3. Run the `app` configuration on an Android 8.0+ emulator or device.

## Included

- Home ledger with monthly totals, search/filter, transaction detail, edit, and delete
- One-sentence parsing in multiple languages plus Android speech input
- Manual income/expense entry with editable confirmation before saving
- Week/month/year charts and category breakdowns
- Analytics, monthly budget tracking, account creation/editing/archiving
- Atomic account balance updates when transactions are added, edited, or removed
- Room database persistence for transactions, accounts, categories, and budgets, with one-time import from the previous SharedPreferences JSON format
- Custom account and income/expense category CRUD, safe archive/delete flows, and historical snapshots
- Profile authentication screens that remain disabled until real provider/backend credentials are configured
- System/light/dark themes, four text sizes, ten currencies, and ten UI languages

## Data migration

Room database version 1 is the first Room schema in this project. Existing releases used a `pocket_ledger` SharedPreferences JSON document. On first launch, that document is imported into Room without deleting the original backup. No destructive migration or `fallbackToDestructiveMigration` is used.

## Authentication scope

Google, WeChat, phone, and email show real integration requirements but do not create fake sessions or tokens. Production authentication and cross-device sync require provider credentials and a backend.
