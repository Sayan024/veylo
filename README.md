# VEYLO

*Together, expenses made effortless.* An offline Android app for splitting group expenses on trips and outings. Phones sync over Bluetooth and Wi-Fi using Google Nearby Connections, with no internet or login needed.

This repository has two apps:

| Folder | What it is |
|---|---|
| `app/` | Android app (Kotlin, Jetpack Compose). Steps below. |
| [`web/`](web/) | Web app you can install from the browser (React, TypeScript). See [web/README.md](web/README.md). |

For an overview of features and design, see [VEYLO.md](VEYLO.md).

## Install

`release/VEYLO-0.1.0-debug.apk` is ready to install. Copy it to a phone and open it (allow "install unknown apps"), or install over USB:

```
A:\AndroidDev\sdk\platform-tools\adb.exe install -r release\VEYLO-0.1.0-debug.apk
```

The app needs Android 8.0 or newer, and Google Play services for nearby sync.

## Build

```powershell
$env:JAVA_HOME = "A:\AndroidDev\jdk-17.0.20.1+1"
.\gradlew.bat assembleDebug        # → app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat testDebugUnitTest    # split / settlement / money tests
```

You can also open the folder in Android Studio. `local.properties` points to `A:/AndroidDev/sdk`.

**Signed release APK:**
1. Create a key: `keytool -genkey -v -keystore veylo.jks -alias veylo -keyalg RSA -keysize 2048 -validity 10000`
2. Add a `signingConfigs { create("release") { ... } }` block to `app/build.gradle.kts` and point `buildTypes.release.signingConfig` at it.
3. Run `.\gradlew.bat assembleRelease`.

## Testing sync on 2 phones

1. **Phone A:** Create a trip, add friends by name (e.g. "Rahul"), open the trip, tap the **QR icon**, then **Start hosting**.
2. **Phone B:** Onboard as "Rahul", tap **+**, then **Join a nearby group**. Scan the QR, or tap the group and type the 6-character code.
3. Phone B receives the whole ledger. It is linked to the existing "Rahul" member because the names match.
4. Add an expense on either phone. It appears on the other phone within about a second. Each item's status changes from **Syncing** to **Synced** only after the other phone confirms it received it.
5. Move Phone B out of range and add expenses. They show as **Pending sync**. When B comes back in range it reconnects automatically and sends them.

## Architecture

```
app/src/main/java/com/veylo/app/
├── domain/        Money (paise as Long), SplitCalculator, SettlementCalculator (pure Kotlin, unit-tested)
├── data/local/    Room entities + DAO (groups, members, expenses, splits, settlements)
├── data/sync/     SyncEngine (Nearby Connections) + SyncMessage protocol
├── data/          VeyloRepository, ProfileStore (no-login local identity)
└── ui/            Compose screens, theme (Forest #163D2A / Lime #C8FF65 / Cream #F7F7F2, Manrope)
```

**How sync works:**
- One phone, the host, keeps the master ledger. The other phones connect to it.
- Everything is saved to the phone first, then sent.
- Every record has a unique ID, so receiving the same record twice is harmless.
- The host checks each incoming expense: the amounts must add up and every member must belong to the group. If a check fails, the host rejects the expense.
- The host passes each accepted record on to the other phones and replies to the sender with a confirmation.
- When two phones edit the same record, the most recent edit wins.
- Deletions are kept as markers, so they sync to the other phones like any other change.
- If no confirmation arrives within 15 seconds, the item is marked **Failed** and is retried on the next connection.
- **Statuses:**
  - **On this phone**: no one else has joined the group yet.
  - **Pending**: waiting for the host to be in range.
  - **Syncing**: sent, waiting for confirmation.
  - **Synced**: confirmed by the other phone.
  - **Failed**: rejected or timed out; retried next time.

## Known limits (v0.1)

- Receipt photos and cover photos stay on the phone that added them. They are not sent to other phones.
- Sync only works through the group's host. If the host's phone is not around, members keep working offline and sync later.
- Totals on the home screen use the most common currency across your groups. There is no currency conversion.
- Dependency injection is done by hand (`AppContainer`) instead of with Hilt, to keep the build small.
- Expense edit history only shows when the expense was last updated. Older versions are not kept.
