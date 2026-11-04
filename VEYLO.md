# VEYLO

**Together, expenses made effortless.**

VEYLO is an Android app for sharing expenses on group trips and outings. Friends record what they spend, VEYLO splits the bills, and everyone's phone stays up to date over Bluetooth. It needs no internet, no account and no server.

---

## Why VEYLO

Splitting money on a trip usually means one of these:
- one person keeps a messy note,
- everyone re-enters the same bill,
- an app that stops working when you're in the mountains with no signal.

VEYLO fixes this:

- **One entry, every phone.** One person adds the bill and it appears on everyone's phone.
- **Works offline.** Phones sync directly with each other over Bluetooth and Wi-Fi Direct.
- **Fair to the paisa.** Splits always add up to the exact total.
- **Fewest payments.** VEYLO works out the smallest number of payments that clears everyone's balance.

---

## Features

| Area | What you can do |
|---|---|
| **Trips** | Name, destination, dates, currency, cover photo, members |
| **Outings** | Quick groups for cafés, dinners, shopping and weekends |
| **Expenses** | Title, amount, who paid, category, receipt photo |
| **Splitting** | Equally, exact amounts, percentage, or shares; leave anyone out |
| **Balances** | Who paid what, who gets back, who owes |
| **Settle Up** | Fewest payments needed, **Pay via UPI**, **Mark as Settled**, history with undo |
| **Activity** | A timeline of every expense and settlement across all groups |
| **Nearby sync** | Host a group, invite with QR or a 6-character code, live updates |
| **Profile** | Display name, UPI ID, light/dark theme, delete all data |

**Expense categories:** Food, Café, Stay, Travel, Shopping, Fun, Other.

**Currencies:** INR (₹), USD, EUR, GBP, THB, AED. Rupee amounts use Indian grouping, for example ₹12,34,567.00.

---

## Screens

1. **Welcome:** forest background, lime "V" logo, "Split. Sync. Travel Together.", enter your name.
2. **Home:**
   - greeting and your overall balance,
   - *You owe* and *Owed to you* boxes,
   - your groups, recent activity and sync status.
3. **Groups:** trips and outings shown as cover cards, with filters.
4. **Trip details:** cover photo header, members, and three tabs:
   - **Expenses:** total spent, your share, and the list of bills.
   - **Balances:** for each member, what they paid and what they get back or owe.
   - **Settle Up:** "Clear all balances with just N payments".
5. **Add Expense:** a large amount field, who paid, category, how to split, and who's included.
6. **Expense details:** each person's share, the sync status, the receipt, and edit or delete.
7. **Join nearby group:** a radar animation, nearby groups, QR scan or code entry.
8. **Activity** and **Profile**.

The **+** button opens: Add Expense · Split Bill · Create Trip · Start Outing · Join a nearby group.

---

## Design

| Element | Value |
|---|---|
| Primary | Forest Green `#163D2A` |
| Accent | Electric Lime `#C8FF65` |
| Background | Warm Cream `#F7F7F2` |
| Surface | White `#FFFFFF` |
| Text | Deep Charcoal `#1C211D` |
| Font | Manrope |
| Corners | 20–28 dp |
| Theme | Light by default, Dark optional |

Large balance numbers, rounded cards with few borders, illustrated mountain covers when a trip has no photo, haptic feedback on buttons, and smooth screen transitions.

---

## How sync works

```
Host phone (master ledger)
      ▲  ▼        ▲  ▼        ▲  ▼
   Phone B      Phone C      Phone D
```

1. The person who creates a group is its **host**. They tap the QR icon, then **Start hosting**.
2. Friends tap **+ → Join a nearby group**, then scan the QR or type the code.
3. The host checks the code, then sends the full group. A friend who joins with a name that's already in the group is matched to that member.
4. New expenses go to the host. The host checks them, saves them, and passes them on to every other phone.
5. Each phone confirms what it receives. An expense shows **Synced** only after that confirmation arrives.

**Sync status of each expense:**

| Status | Meaning |
|---|---|
| On this phone | No one else has joined the group yet |
| Pending sync | Saved here, waiting for the host to be nearby |
| Syncing | Sent, waiting for confirmation |
| Synced | Confirmed |
| Failed | Rejected or timed out; retried next time |

Everything is saved on the phone before it's sent, so nothing is lost when someone walks out of range. Phones reconnect to the host automatically when they're back in range. If two people edit the same expense, the latest edit wins.

---

## Privacy

- No account, no login, no server.
- All data stays on the phones in the group.
- Only group members who entered the right code can sync.
- The host rejects bad data, such as amounts that don't add up or members who aren't in the group.
- No payment details are stored. UPI only opens your own UPI app.
- You can leave a group or delete all data at any time.

---

## Technology

| Part | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Storage | Room (on the phone) |
| Architecture | MVVM, StateFlow, Coroutines |
| Nearby sync | Google Nearby Connections (Bluetooth / BLE / Wi-Fi) |
| Messages | Kotlin Serialization (JSON) |
| QR | ZXing (generate), Google Code Scanner (scan) |
| Images | Coil |
| Minimum Android | 8.0 (API 26), target API 35 |

```
app/src/main/java/com/veylo/app/
├── domain/       Money, SplitCalculator, SettlementCalculator
├── data/local/   Room tables and database queries
├── data/sync/    SyncEngine + message format
├── data/         Repository, local profile
└── ui/           Theme, components, screens, navigation
```

---

## Status (v0.1.0)

**Done**
- All screens listed above, working with real data
- Expense splitting and settle-up calculations, with unit tests
- Nearby sync with confirmations, retries and automatic reconnection
- Installable APK: `release/VEYLO-0.1.0-debug.apk`

**Known limits**
- Receipt and cover photos stay on the phone that added them.
- Sync goes through the host. Without the host nearby, members work offline and sync later.
- Home totals use your most common currency; there's no currency conversion.
- Edit history only shows the last update time.

**Next ideas**
- Share receipt photos between phones
- Let any member act as host
- Spending charts per category
- Export a trip summary as PDF
- Signed Play Store release

---

## Quick start

1. Install `release/VEYLO-0.1.0-debug.apk`.
2. Enter your name, then tap **+ → Create Trip** and add your friends.
3. Add expenses, then open **Settle Up** to see who pays whom.
4. To sync: tap the QR icon in your trip, then **Start hosting**. Your friends tap **Join a nearby group**.

For build instructions, see [README.md](README.md).
