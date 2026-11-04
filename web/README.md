# VEYLO Web

*Together, expenses made effortless.* A mobile-first web app for splitting expenses on trips and outings. You can install it on your phone like an app, and it keeps working without internet. No download, account or login.

- **Local mode** (no setup): everything works in one browser, including offline.
- **Shared mode** (free Supabase project): invite friends with a QR code, link or 6-character code. Everyone's view updates within a second or two.

## Quick start

```bash
cd web
npm install
npm run dev        # http://localhost:5173
npm test           # money, split and settle-up tests
npm run build      # production build in dist/ (includes the offline service worker)
```

## Turn on shared groups (Supabase Free)

1. Create a free project at [supabase.com](https://supabase.com).
2. Open **SQL Editor**, paste [`supabase/schema.sql`](supabase/schema.sql) and click **Run**.
3. Go to **Project Settings → API** and copy the **Project URL** and the **anon / publishable** key.
4. Create `web/.env.local`:
   ```
   VITE_SUPABASE_URL=https://xxxx.supabase.co
   VITE_SUPABASE_ANON_KEY=eyJ...
   ```
5. Restart `npm run dev`.

> Only use the **anon / publishable** key. Never put the service-role key in the frontend or in Vercel environment variables for this app.

## Deploy to Vercel (free)

1. Push the repo to GitHub and import it in Vercel.
2. Set **Root Directory** to `web`. Vercel detects Vite automatically (`npm run build` → `dist`).
3. Add `VITE_SUPABASE_URL` and `VITE_SUPABASE_ANON_KEY` under **Environment Variables**.
4. Deploy. `vercel.json` already makes links like `/join?code=…` and `/g/…` open the app.

## Features

- **Home:** overall balance, You owe / Owed to you, active groups, recent expenses and settlements, sync status, quick actions.
- **Groups:** cover cards, search, and Active / Completed / All filters.
- **Create Trip:** name, destination, dates, currency, cover photo and members. **Create Outing** is the short version.
- **Trip details:**
  - **Expenses:** total spent, your share, a spending-by-category chart, and the list.
  - **Balances:** for each member, paid, share and net, plus who owes whom.
  - **Settle Up:** the fewest payments needed, Pay via UPI, Mark as Settled, and a history you can undo.
- **Add / Edit / Duplicate expense:**
  - split equally, by exact amounts, by percentage, or by shares;
  - leave members out;
  - shares always add up exactly to the total;
  - optional receipt photo and notes.
- **Join:** QR scanner, invite link or code, a preview of the group, then pick which listed member you are, or join as new.
- **Activity:** a timeline of expenses, edits, deletions, settlements, group changes and conflicts, filterable by type and group.
- **Profile:** name, avatar, UPI ID, default currency, light / dark / system theme, export and import a JSON backup, delete local data.
- **Installable app:** install to the home screen, opens offline, and changes made offline are queued and sent automatically.

## How the money math works (`src/lib`)

- All amounts are stored as whole **paise or cents**, never as decimals, so nothing gets lost to rounding.
- Indian amounts use Indian grouping, for example ₹12,34,567.00.
- Equal, percentage and shares splits use whole-number math. Leftover paise go to the members who are owed the biggest fractions (ties go to the earlier member), so shares always add up exactly to the total.
- Each member's balance = what they paid − their share, adjusted for settlements already recorded.
- Settle Up first pairs people whose amounts match exactly, then repeatedly matches the person who owes the most with the person who is owed the most. That's at most one payment fewer than the number of members, and it never routes money in a circle or through people who are already settled.
- All of this is covered by `calc.test.ts`.

## How sync works

```
UI → save to the browser's database (IndexedDB) → add to the send queue → server function in Supabase
        ↑                                                                          │
        └─ fetch changes since last check ← "something changed" signal ←───────────┘
```

1. **Saved locally first.** Every change is written to IndexedDB before anything is sent, so a network failure can't lose an expense.
2. **Queue.** Changes are sent in order when you're online, and again automatically when you come back online, return to the tab, or every 30 seconds. Each item shows **Pending → Syncing → Synced**, or **Failed** if the server rejected it.
3. **Conflicts.** Every expense has a revision number, and each edit says which revision it started from. If someone else saved first:
   - the server rejects the late edit and records both versions in its audit log;
   - your device switches to the accepted version;
   - a **Conflict** entry appears in Activity explaining what happened.

   Nothing is overwritten blindly.
4. **Live updates.** After a change is saved, the server sends a small "changed" signal to the group over Supabase Realtime. It contains no data. Other devices then fetch the new records through the secure functions.

**Offline:** previously opened groups, adding, editing and deleting all work offline. Seeing *other people's* new changes needs an internet connection.

## Security model and limitations

**What's in place:**
- Row Level Security is on for every table, with **no policies**. The public key can't read or write any table directly.
- Every read and write goes through server functions that run with elevated rights:

  | Function | Purpose |
  |---|---|
  | `create_group` | upload a new group |
  | `preview_invite` | show a group's name and members for a code |
  | `join_group` | join with a code |
  | `push_member` | save a member's name or UPI ID |
  | `push_expense` | save an expense |
  | `push_settlement` | save a settlement |
  | `pull_group` | fetch changes |
- Joining requires the invite code. A successful join gives the device a random 192-bit **member token**. The server stores only a fingerprint (hash) of the token, and every later call must include the token.
- The server re-checks every expense:
  - the payer and everyone in the split belong to the group;
  - no share is negative;
  - **the shares add up exactly to the total.**
- Members can only edit their own profile, or members who haven't joined yet. Settlements can only be undone, not edited.
- The "changed" signal carries no data. Device IDs are never returned to other members.

**Limits of anonymous invite-code access (accept these, or add Supabase Auth later):**
- **Anyone with the invite code can join** and see that group. Share codes only with people you trust.
- **Guessing codes is theoretically possible.** A code has about 1.07 billion combinations, and there's no built-in rate limit. For a public deployment, add rate limiting (Supabase Edge Function or API gateway) or longer codes.
- **The member token works like a password** for that group on that device. Clearing browser data loses it; rejoin with the code and pick your name again.
- **No server-side bans.** Leaving a group only removes it from your device. There's no way to kick a member out yet.
- **Anyone can create groups.** For production, add a cap per device or IP.
- **Receipt and cover photos stay on the device** that added them. They are not uploaded.

## UPI

On **Settle Up**, if you're the one paying in an INR group and the person you're paying has a UPI ID in their profile, a **Pay via UPI** button appears. It opens:

`upi://pay?pa=<their UPI ID>&pn=<their name>&am=<amount>&cu=INR&tn=<note>&tr=<reference>`

This launches your UPI app (GPay, PhonePe, Paytm…) on phones. VEYLO never sees or stores payment details. After paying, tap **Mark as Settled**.

## Project structure

```
web/
├── src/lib/          money, split, settle (+ tests), db (Dexie), data (local changes), sync, cloud, hooks, profile
├── src/components/   Shell (sidebar / bottom nav / + menu), ui kit, list rows
├── src/pages/        Welcome, Home, Groups, CreateGroup, GroupDetail, AddExpense, ExpenseDetail, Join, Activity, Profile
├── supabase/         schema.sql (tables, security, server functions)
└── public/           icons
```

**Differences from the original brief:**
- shadcn/ui wasn't used. A small built-in component kit (`components/ui.tsx`) keeps the VEYLO look consistent and the bundle small.
- Each group has one currency, so balances can be added together. There's no currency conversion.
