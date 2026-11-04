import { useLiveQuery } from 'dexie-react-hooks'
import { useMemo, useSyncExternalStore } from 'react'
import { db } from './db'
import { memberTotals, minimizeTransfers, type MemberTotals, type Transfer } from './settle'
import type { Activity, Expense, Group, Member, Settlement } from './types'

export interface GroupSummary {
  group: Group
  members: Member[]
  expenses: Expense[]
  settlements: Settlement[]
  totals: Record<string, MemberTotals>
  transfers: Transfer[]
  me?: Member
  totalSpent: number
  myBalance: number
  myShare: number
  unsynced: number
  name: (id: string) => string
  member: (id: string) => Member | undefined
}

export interface AppData {
  groups: GroupSummary[]
  activity: Activity[]
  group: (id?: string) => GroupSummary | undefined
  mainCurrency: string
  youOwe: number
  owedToYou: number
  net: number
  pendingCount: number
}

function summarize(group: Group, members: Member[], expenses: Expense[], settlements: Settlement[]): GroupSummary {
  const liveMembers = members
    .filter((m) => !m.deleted)
    // You first, then everyone in the order they were added.
    .sort((a, b) => (a.id === group.myMemberId ? -1 : b.id === group.myMemberId ? 1 : a.joinedAt.localeCompare(b.joinedAt)))
  const liveExpenses = expenses.filter((e) => !e.deleted).sort((a, b) => (b.date + b.createdAt).localeCompare(a.date + a.createdAt))
  const liveSettlements = settlements.filter((s) => !s.deleted).sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  const totals = memberTotals(
    liveMembers.map((m) => m.id),
    liveExpenses.map((e) => ({ payerId: e.payerId, amount: e.amount, shares: Object.fromEntries(e.shares.map((s) => [s.memberId, s.share])) })),
    liveSettlements.map((s) => ({ fromId: s.fromId, toId: s.toId, amount: s.amount })),
  )
  const me = members.find((m) => m.id === group.myMemberId)
  const byId = new Map(members.map((m) => [m.id, m]))
  const pending = (x: { syncStatus: string }) => x.syncStatus !== 'synced' && x.syncStatus !== 'local'
  return {
    group,
    members: liveMembers,
    expenses: liveExpenses,
    settlements: liveSettlements,
    totals,
    transfers: minimizeTransfers(Object.fromEntries(Object.entries(totals).map(([k, v]) => [k, v.net]))),
    me,
    totalSpent: liveExpenses.reduce((s, e) => s + e.amount, 0),
    myBalance: me ? totals[me.id]?.net ?? 0 : 0,
    myShare: me ? totals[me.id]?.share ?? 0 : 0,
    unsynced: expenses.filter(pending).length + settlements.filter(pending).length,
    name: (id) => (id === me?.id ? 'You' : byId.get(id)?.name ?? 'Someone'),
    member: (id) => byId.get(id),
  }
}

/** Live view of everything, recomputed whenever IndexedDB changes. undefined while loading. */
export function useAppData(): AppData | undefined {
  const raw = useLiveQuery(async () => {
    const [groups, members, expenses, settlements, activity] = await Promise.all([
      db.groups.orderBy('updatedAt').reverse().toArray(),
      db.members.toArray(),
      db.expenses.toArray(),
      db.settlements.toArray(),
      db.activity.orderBy('at').reverse().limit(300).toArray(),
    ])
    return { groups, members, expenses, settlements, activity }
  })

  return useMemo(() => {
    if (!raw) return undefined
    const summaries = raw.groups
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .map((g) =>
        summarize(
          g,
          raw.members.filter((m) => m.groupId === g.id),
          raw.expenses.filter((e) => e.groupId === g.id),
          raw.settlements.filter((s) => s.groupId === g.id),
        ),
      )
    const counts: Record<string, number> = {}
    summaries.forEach((s) => (counts[s.group.currency] = (counts[s.group.currency] ?? 0) + 1))
    const mainCurrency = Object.entries(counts).sort((a, b) => b[1] - a[1])[0]?.[0] ?? 'INR'
    const same = summaries.filter((s) => s.group.currency === mainCurrency)
    const youOwe = same.reduce((s, g) => s + Math.max(0, -g.myBalance), 0)
    const owedToYou = same.reduce((s, g) => s + Math.max(0, g.myBalance), 0)
    return {
      groups: summaries,
      activity: raw.activity,
      group: (id?: string) => summaries.find((s) => s.group.id === id),
      mainCurrency,
      youOwe,
      owedToYou,
      net: owedToYou - youOwe,
      pendingCount: summaries.reduce((s, g) => s + g.unsynced, 0),
    }
  }, [raw])
}

export function useOnline(): boolean {
  return useSyncExternalStore(
    (cb) => {
      window.addEventListener('online', cb)
      window.addEventListener('offline', cb)
      return () => {
        window.removeEventListener('online', cb)
        window.removeEventListener('offline', cb)
      }
    },
    () => navigator.onLine,
  )
}
