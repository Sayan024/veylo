import { z } from 'zod'
import { cloudEnabled } from './cloud'
import { db } from './db'
import { formatMoney } from './money'
import { profile } from './profile'
import type { SplitMethod } from './split'
import type { TripTheme } from './theme'
import type { Activity, ActivityKind, Expense, Group, GroupType, Member, OutboxKind, PayMethod, PayStatus, Settlement, ShareRow, SyncStatus } from './types'
import { nowIso, uid } from './utils'

/** Tells the sync engine something changed locally. */
export const notifyChange = () => window.dispatchEvent(new Event('veylo:changed'))

const initialStatus = (): SyncStatus => (cloudEnabled ? 'pending' : 'local')

async function enqueue(groupId: string, kind: OutboxKind, entityId: string, baseRevision: number) {
  if (!cloudEnabled) return
  const existing = await db.outbox.where('[kind+entityId]').equals([kind, entityId]).first()
  if (existing) return // the queued op reads the latest local copy when it runs
  await db.outbox.put({ id: uid(), groupId, kind, entityId, baseRevision, at: nowIso(), attempts: 0 })
}

export async function log(groupId: string, kind: ActivityKind, title: string, detail: string, amount?: number, entityId?: string, actor?: string) {
  const a: Activity = { id: uid(), groupId, kind, title, detail, amount, entityId, actor: actor ?? profile().name, at: nowIso() }
  await db.activity.put(a)
}

async function myName(groupId: string) {
  const g = await db.groups.get(groupId)
  const m = g ? await db.members.get(g.myMemberId) : undefined
  return m?.name ?? profile().name
}

// ------------------------------------------------------------------ Groups

export interface NewGroup {
  name: string
  type: GroupType
  destination: string
  currency: string
  cover?: string | null
  startDate?: string | null
  endDate?: string | null
  members: string[]
  theme: TripTheme
}

export async function createGroup(input: NewGroup): Promise<string> {
  const p = profile()
  const now = nowIso()
  const id = uid()
  const meId = uid()
  const group: Group = {
    id, name: input.name.trim(), type: input.type, destination: input.destination.trim(), currency: input.currency,
    cover: input.cover ?? null, startDate: input.startDate || null, endDate: input.endDate || null,
    archived: false, createdAt: now, updatedAt: now, shared: false, myMemberId: meId, syncStatus: initialStatus(),
    theme: input.theme,
  }
  const names = [...new Set(input.members.map((m) => m.trim()).filter(Boolean))].filter(
    (n) => n.toLowerCase() !== p.name.trim().toLowerCase(),
  )
  const members: Member[] = [
    { id: meId, groupId: id, name: p.name.trim() || 'You', deviceId: p.deviceId, upiId: p.upiId || null, joinedAt: now, updatedAt: now, revision: 0, deleted: false, syncStatus: initialStatus() },
    ...names.map((n, i): Member => ({
      id: uid(), groupId: id, name: n, deviceId: null, upiId: null,
      joinedAt: new Date(Date.now() + i + 1).toISOString(), updatedAt: now, revision: 0, deleted: false, syncStatus: initialStatus(),
    })),
  ]
  await db.transaction('rw', db.groups, db.members, db.activity, db.outbox, async () => {
    await db.groups.put(group)
    await db.members.bulkPut(members)
    await log(id, 'group_created', `Created ${group.name}`, `${members.length} members`)
    await enqueue(id, 'group', id, 0)
  })
  notifyChange()
  return id
}

/** Local-only fields (cover photo, archived, style). */
export async function updateGroupLocal(id: string, patch: Partial<Pick<Group, 'cover' | 'archived' | 'theme'>>) {
  await db.groups.update(id, patch)
}

export async function addMember(groupId: string, name: string) {
  const now = nowIso()
  const m: Member = { id: uid(), groupId, name: name.trim(), deviceId: null, upiId: null, joinedAt: now, updatedAt: now, revision: 0, deleted: false, syncStatus: initialStatus() }
  await db.transaction('rw', db.members, db.activity, db.outbox, async () => {
    await db.members.put(m)
    await log(groupId, 'member_added', `${m.name} added`, 'New member')
    await enqueue(groupId, 'member', m.id, 0)
  })
  notifyChange()
}

/** Pushes my latest name / UPI ID to every group I'm in. */
export async function syncMyProfileToGroups() {
  const p = profile()
  const groups = await db.groups.toArray()
  for (const g of groups) {
    const me = await db.members.get(g.myMemberId)
    if (!me || (me.name === p.name && (me.upiId ?? '') === p.upiId)) continue
    await db.members.update(me.id, { name: p.name, upiId: p.upiId || null, updatedAt: nowIso(), syncStatus: initialStatus() })
    await enqueue(g.id, 'member', me.id, me.revision)
  }
  notifyChange()
}

export async function leaveGroup(groupId: string) {
  await db.transaction('rw', [db.groups, db.members, db.expenses, db.settlements, db.activity, db.outbox], async () => {
    await db.members.where('groupId').equals(groupId).delete()
    await db.expenses.where('groupId').equals(groupId).delete()
    await db.settlements.where('groupId').equals(groupId).delete()
    await db.activity.where('groupId').equals(groupId).delete()
    await db.outbox.where('groupId').equals(groupId).delete()
    await db.groups.delete(groupId)
  })
}

// ------------------------------------------------------------------ Expenses

export interface ExpenseInput {
  id?: string
  groupId: string
  title: string
  amount: number
  currency: string
  payerId: string
  category: string
  date: string
  notes: string
  receipt?: string | null
  splitMethod: SplitMethod
  shares: ShareRow[]
}

export async function saveExpense(input: ExpenseInput): Promise<string> {
  const now = nowIso()
  const old = input.id ? await db.expenses.get(input.id) : undefined
  const id = old?.id ?? uid()
  const g = await db.groups.get(input.groupId)
  if (!g) throw new Error('Group not found')
  const e: Expense = {
    id, groupId: input.groupId, title: input.title.trim(), amount: input.amount, currency: input.currency,
    payerId: input.payerId, category: input.category, date: input.date, notes: input.notes.trim(),
    receipt: input.receipt ?? null, splitMethod: input.splitMethod, shares: input.shares.filter((s) => s.share >= 0),
    createdBy: old?.createdBy ?? g.myMemberId, createdAt: old?.createdAt ?? now, updatedAt: now,
    revision: old?.revision ?? 0, deleted: false, syncStatus: initialStatus(), syncError: null,
  }
  const actor = await myName(e.groupId)
  await db.transaction('rw', db.expenses, db.activity, db.outbox, async () => {
    await db.expenses.put(e)
    await log(e.groupId, old ? 'expense_edited' : 'expense_added', old ? `Edited ${e.title}` : e.title,
      old ? `Now ${formatMoney(e.amount, e.currency)}` : `${formatMoney(e.amount, e.currency)} expense`, e.amount, e.id, actor)
    await enqueue(e.groupId, 'expense', e.id, e.revision)
  })
  notifyChange()
  return id
}

export async function deleteExpense(id: string) {
  const e = await db.expenses.get(id)
  if (!e) return
  const actor = await myName(e.groupId)
  await db.transaction('rw', db.expenses, db.activity, db.outbox, async () => {
    await db.expenses.update(id, { deleted: true, updatedAt: nowIso(), syncStatus: initialStatus() })
    await log(e.groupId, 'expense_deleted', `Deleted ${e.title}`, formatMoney(e.amount, e.currency), e.amount, e.id, actor)
    await enqueue(e.groupId, 'expense', id, e.revision)
  })
  notifyChange()
}

// ------------------------------------------------------------------ Settlements

export async function recordSettlement(
  groupId: string, fromId: string, toId: string, amount: number,
  pay: { method?: PayMethod; txnId?: string | null; payStatus?: PayStatus } = {},
) {
  const g = await db.groups.get(groupId)
  if (!g || amount <= 0 || fromId === toId) return
  const now = nowIso()
  const s: Settlement = {
    id: uid(), groupId, fromId, toId, amount, createdBy: g.myMemberId, createdAt: now, updatedAt: now,
    revision: 0, deleted: false, syncStatus: initialStatus(),
    method: pay.method ?? 'cash', txnId: pay.txnId?.trim() || null, payStatus: pay.payStatus ?? 'success',
  }
  const [from, to] = await Promise.all([db.members.get(fromId), db.members.get(toId)])
  const actor = await myName(groupId)
  await db.transaction('rw', db.settlements, db.activity, db.outbox, async () => {
    await db.settlements.put(s)
    await log(groupId, 'settlement', `${from?.name} paid ${to?.name}`, formatMoney(amount, g.currency), amount, s.id, actor)
    await enqueue(groupId, 'settlement', s.id, 0)
  })
  notifyChange()
}

/** A pending payment was received. */
export async function confirmSettlement(id: string) {
  const s = await db.settlements.get(id)
  if (!s) return
  await db.settlements.update(id, { payStatus: 'success', updatedAt: nowIso(), syncStatus: initialStatus() })
  await enqueue(s.groupId, 'settlement', id, s.revision)
  notifyChange()
}

export async function undoSettlement(id: string) {
  const s = await db.settlements.get(id)
  if (!s) return
  const g = await db.groups.get(s.groupId)
  const actor = await myName(s.groupId)
  await db.transaction('rw', db.settlements, db.activity, db.outbox, async () => {
    await db.settlements.update(id, { deleted: true, updatedAt: nowIso(), syncStatus: initialStatus() })
    await log(s.groupId, 'settlement_undone', 'Settlement undone', formatMoney(s.amount, g?.currency), s.amount, s.id, actor)
    await enqueue(s.groupId, 'settlement', id, s.revision)
  })
  notifyChange()
}

// ------------------------------------------------------------------ Backup

const backupSchema = z.object({
  app: z.literal('veylo'),
  version: z.literal(1),
  exportedAt: z.string(),
  groups: z.array(z.record(z.unknown())),
  members: z.array(z.record(z.unknown())),
  expenses: z.array(z.record(z.unknown())),
  settlements: z.array(z.record(z.unknown())),
  activity: z.array(z.record(z.unknown())),
})

export async function exportBackup(): Promise<string> {
  const [groups, members, expenses, settlements, activity] = await Promise.all([
    db.groups.toArray(), db.members.toArray(), db.expenses.toArray(), db.settlements.toArray(), db.activity.toArray(),
  ])
  // Membership secrets stay on this device.
  const safeGroups = groups.map((g) => ({ ...g, memberToken: null }))
  return JSON.stringify({ app: 'veylo', version: 1, exportedAt: nowIso(), groups: safeGroups, members, expenses, settlements, activity }, null, 2)
}

/** Imports a backup as local-only groups (it can't restore cloud membership). Returns the group count. */
export async function importBackup(json: string): Promise<number> {
  const data = backupSchema.parse(JSON.parse(json))
  const groups = data.groups as unknown as Group[]
  await db.transaction('rw', [db.groups, db.members, db.expenses, db.settlements, db.activity], async () => {
    await db.groups.bulkPut(groups.map((g) => (g.memberToken ? g : { ...g, shared: false, syncStatus: 'local' as const })))
    await db.members.bulkPut(data.members as unknown as Member[])
    await db.expenses.bulkPut(data.expenses as unknown as Expense[])
    await db.settlements.bulkPut(data.settlements as unknown as Settlement[])
    await db.activity.bulkPut(data.activity as unknown as Activity[])
  })
  return groups.length
}

export async function deleteAllData() {
  await Promise.all([db.groups.clear(), db.members.clear(), db.expenses.clear(), db.settlements.clear(), db.activity.clear(), db.outbox.clear()])
}
