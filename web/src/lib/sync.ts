import type { RealtimeChannel } from '@supabase/supabase-js'
import { create } from 'zustand'
import { CloudError, cloudEnabled, rpc, supabase } from './cloud'
import { log } from './data'
import { db } from './db'
import { formatMoney } from './money'
import { profile } from './profile'
import type { SplitMethod } from './split'
import type { Expense, Group, Member, OutboxOp, Settlement, ShareRow } from './types'
import { nowIso } from './utils'

/**
 * Offline-first sync.
 *
 * 1. Every local change is written to IndexedDB and an outbox op is queued (see data.ts).
 * 2. runSync() pushes the outbox in order through secure database functions. Each push
 *    carries the revision it was based on; the server rejects stale edits (conflict) and
 *    keeps an audit record, and we adopt the server's version locally.
 * 3. Then it pulls everything changed since the last pull for each shared group.
 * 4. A Supabase Realtime broadcast "ping" tells other members to pull immediately.
 *    The ping carries no data, so the channel never exposes group contents.
 */

interface SyncUi {
  syncing: boolean
  lastSyncAt?: string
  error?: string
}
export const useSyncUi = create<SyncUi>(() => ({ syncing: false }))

// ------------------------------------------------------------------ Wire format

interface ServerMember { id: string; group_id: string; name: string; device_id: string | null; upi_id: string | null; joined_at: string; updated_at: string; revision: number; deleted: boolean }
interface ServerExpense {
  id: string; group_id: string; title: string; amount: number; currency: string; payer_id: string; category: string
  date: string; notes: string; split_method: SplitMethod; shares: { member_id: string; share: number; input: number }[]
  created_by: string; updated_by: string | null; created_at: string; updated_at: string; revision: number; deleted: boolean
}
interface ServerSettlement { id: string; group_id: string; from_id: string; to_id: string; amount: number; created_by: string; updated_by: string | null; created_at: string; updated_at: string; revision: number; deleted: boolean }
interface ServerGroup { id: string; name: string; type: 'trip' | 'outing'; destination: string; currency: string; start_date: string | null; end_date: string | null; invite_code: string; created_at: string; updated_at: string }
type PushResult<T> = { status: 'ok'; revision: number; updated_at: string } | { status: 'conflict'; server: T }

const toServerMember = (m: Member) => ({ id: m.id, name: m.name, device_id: m.deviceId ?? null, upi_id: m.upiId ?? null, joined_at: m.joinedAt })
const toServerExpense = (e: Expense) => ({
  id: e.id, group_id: e.groupId, title: e.title, amount: e.amount, currency: e.currency, payer_id: e.payerId,
  category: e.category, date: e.date, notes: e.notes, split_method: e.splitMethod,
  shares: e.shares.map((s) => ({ member_id: s.memberId, share: s.share, input: s.input })),
  created_by: e.createdBy, created_at: e.createdAt, deleted: e.deleted,
})
const toServerSettlement = (s: Settlement) => ({
  id: s.id, group_id: s.groupId, from_id: s.fromId, to_id: s.toId, amount: s.amount, created_by: s.createdBy, created_at: s.createdAt, deleted: s.deleted,
})

const fromServerMember = (m: ServerMember): Member => ({
  id: m.id, groupId: m.group_id, name: m.name, deviceId: m.device_id, upiId: m.upi_id, joinedAt: m.joined_at,
  updatedAt: m.updated_at, revision: m.revision, deleted: m.deleted, syncStatus: 'synced',
})
const fromServerExpense = (e: ServerExpense, receipt?: string | null): Expense => ({
  id: e.id, groupId: e.group_id, title: e.title, amount: Number(e.amount), currency: e.currency, payerId: e.payer_id,
  category: e.category, date: e.date, notes: e.notes ?? '', receipt: receipt ?? null, splitMethod: e.split_method,
  shares: e.shares.map((s): ShareRow => ({ memberId: s.member_id, share: Number(s.share), input: Number(s.input) })),
  createdBy: e.created_by, createdAt: e.created_at, updatedAt: e.updated_at, revision: e.revision, deleted: e.deleted, syncStatus: 'synced',
})
const fromServerSettlement = (s: ServerSettlement): Settlement => ({
  id: s.id, groupId: s.group_id, fromId: s.from_id, toId: s.to_id, amount: Number(s.amount), createdBy: s.created_by,
  createdAt: s.created_at, updatedAt: s.updated_at, revision: s.revision, deleted: s.deleted, syncStatus: 'synced',
})

// ------------------------------------------------------------------ Push

const unsynced = (s: { syncStatus: string }) => s.syncStatus !== 'synced' && s.syncStatus !== 'local'

async function setStatus(op: OutboxOp, syncStatus: Expense['syncStatus'], syncError: string | null = null) {
  const table = op.kind === 'expense' ? db.expenses : op.kind === 'settlement' ? db.settlements : op.kind === 'member' ? db.members : db.groups
  await (table as typeof db.expenses).update(op.entityId, { syncStatus, syncError } as Partial<Expense>)
}

async function nameOf(memberId: string | null | undefined) {
  return (memberId && (await db.members.get(memberId))?.name) || 'Someone'
}

async function pushOne(op: OutboxOp, g: Group): Promise<void> {
  const token = g.memberToken
  if (op.kind === 'group') {
    const members = await db.members.where('groupId').equals(g.id).toArray()
    const res = await rpc<{ invite_code: string; member_token: string; now: string }>('create_group', {
      p_group: { id: g.id, name: g.name, type: g.type, destination: g.destination, currency: g.currency, start_date: g.startDate, end_date: g.endDate },
      p_members: members.map(toServerMember),
      p_device: profile().deviceId,
    })
    await db.transaction('rw', db.groups, db.members, db.outbox, async () => {
      await db.groups.update(g.id, { shared: true, inviteCode: res.invite_code, memberToken: res.member_token, syncStatus: 'synced', syncError: null })
      for (const m of members) await db.members.update(m.id, { revision: 1, syncStatus: 'synced' })
      // Members were included in create_group.
      await db.outbox.where('groupId').equals(g.id).filter((o) => o.kind === 'member' && o.baseRevision === 0).delete()
    })
    return
  }

  if (op.kind === 'member') {
    const m = await db.members.get(op.entityId)
    if (!m) return
    const res = await rpc<PushResult<ServerMember>>('push_member', { p_token: token, p_member: toServerMember(m), p_base_revision: op.baseRevision })
    if (res.status === 'ok') await db.members.update(m.id, { revision: res.revision, syncStatus: 'synced' })
    else await db.members.put(fromServerMember(res.server))
    return
  }

  if (op.kind === 'expense') {
    const e = await db.expenses.get(op.entityId)
    if (!e) return
    await db.expenses.update(e.id, { syncStatus: 'syncing' })
    const res = await rpc<PushResult<ServerExpense>>('push_expense', { p_token: token, p_expense: toServerExpense(e), p_base_revision: op.baseRevision })
    const latest = await db.expenses.get(e.id)
    if (res.status === 'ok') {
      const editedMeanwhile = latest && latest.updatedAt !== e.updatedAt
      await db.expenses.update(e.id, { revision: res.revision, syncStatus: editedMeanwhile ? 'pending' : 'synced', syncError: null })
      if (editedMeanwhile) await db.outbox.put({ id: crypto.randomUUID(), groupId: g.id, kind: 'expense', entityId: e.id, baseRevision: res.revision, at: nowIso(), attempts: 0 })
    } else {
      const server = fromServerExpense(res.server, latest?.receipt)
      await db.expenses.put(server)
      await log(g.id, 'conflict', `Conflict on ${e.title}`,
        `${await nameOf(res.server.updated_by)} changed it first. Kept their version (${formatMoney(server.amount, server.currency)}); yours was ${formatMoney(e.amount, e.currency)}.`,
        server.amount, e.id, 'VEYLO')
    }
    return
  }

  const s = await db.settlements.get(op.entityId)
  if (!s) return
  await db.settlements.update(s.id, { syncStatus: 'syncing' })
  const res = await rpc<PushResult<ServerSettlement>>('push_settlement', { p_token: token, p_settlement: toServerSettlement(s), p_base_revision: op.baseRevision })
  const latest = await db.settlements.get(s.id)
  if (res.status === 'ok') {
    const editedMeanwhile = latest && latest.updatedAt !== s.updatedAt
    await db.settlements.update(s.id, { revision: res.revision, syncStatus: editedMeanwhile ? 'pending' : 'synced', syncError: null })
    if (editedMeanwhile) await db.outbox.put({ id: crypto.randomUUID(), groupId: g.id, kind: 'settlement', entityId: s.id, baseRevision: res.revision, at: nowIso(), attempts: 0 })
  } else {
    await db.settlements.put(fromServerSettlement(res.server))
    await log(g.id, 'conflict', 'Settlement conflict', 'Another member changed this settlement first; kept their version.', s.amount, s.id, 'VEYLO')
  }
}

async function pushOutbox(touched: Set<string>) {
  const ops = await db.outbox.orderBy('at').toArray()
  const blocked = new Set<string>()
  for (const op of ops) {
    if (blocked.has(op.groupId)) continue
    const g = await db.groups.get(op.groupId)
    if (!g) {
      await db.outbox.delete(op.id)
      continue
    }
    if (op.kind !== 'group' && !g.shared) {
      blocked.add(op.groupId) // wait until the group itself is created in the cloud
      continue
    }
    try {
      await pushOne(op, g)
      await db.outbox.delete(op.id)
      touched.add(op.groupId)
    } catch (e) {
      const err = e as CloudError
      if (err.network) {
        await db.outbox.update(op.id, { attempts: op.attempts + 1, lastError: err.message })
        await setStatus(op, 'pending')
        throw err // stop; retry everything when back online
      }
      // Server refused (validation / permissions): keep the data, surface the reason.
      await setStatus(op, 'failed', err.message)
      await db.outbox.delete(op.id)
      if (op.kind === 'group') blocked.add(op.groupId)
    }
  }
}

// ------------------------------------------------------------------ Pull

interface PullResult { now: string; group: ServerGroup; members: ServerMember[]; expenses: ServerExpense[]; settlements: ServerSettlement[] }

async function pullGroup(g: Group, full = false) {
  if (!g.memberToken) return
  // Small overlap window: merging is idempotent, so re-reading a few rows is harmless.
  const since = !full && g.lastPulledAt ? new Date(new Date(g.lastPulledAt).getTime() - 10_000).toISOString() : null
  const res = await rpc<PullResult>('pull_group', { p_token: g.memberToken, p_since: since })

  await db.transaction('rw', [db.groups, db.members, db.expenses, db.settlements, db.activity], async () => {
    await db.groups.update(g.id, {
      name: res.group.name, destination: res.group.destination, currency: res.group.currency,
      startDate: res.group.start_date, endDate: res.group.end_date, inviteCode: res.group.invite_code,
      lastPulledAt: res.now, syncStatus: 'synced', syncError: null,
    })

    for (const sm of res.members) {
      const local = await db.members.get(sm.id)
      if (local && unsynced(local) && local.revision >= sm.revision) continue
      await db.members.put(fromServerMember(sm))
      if (!local && sm.device_id && sm.id !== g.myMemberId) await log(g.id, 'group_joined', `${sm.name} joined`, g.name, undefined, sm.id, sm.name)
    }

    for (const se of res.expenses) {
      const local = await db.expenses.get(se.id)
      if (local && unsynced(local) && local.revision >= se.revision) continue // our pending edit wins locally until pushed
      if (local && local.revision === se.revision && local.syncStatus === 'synced') continue
      await db.expenses.put(fromServerExpense(se, local?.receipt))
      const byOther = (se.updated_by ?? se.created_by) !== g.myMemberId
      if (byOther) {
        const who = await nameOf(se.updated_by ?? se.created_by)
        const amt = formatMoney(Number(se.amount), se.currency)
        if (se.deleted && local && !local.deleted) await log(g.id, 'expense_deleted', `Deleted ${se.title}`, amt, Number(se.amount), se.id, who)
        else if (!local && !se.deleted) await log(g.id, 'expense_added', se.title, `${amt} expense`, Number(se.amount), se.id, who)
        else if (local && !se.deleted) await log(g.id, 'expense_edited', `Edited ${se.title}`, `Now ${amt}`, Number(se.amount), se.id, who)
      }
    }

    for (const ss of res.settlements) {
      const local = await db.settlements.get(ss.id)
      if (local && unsynced(local) && local.revision >= ss.revision) continue
      if (local && local.revision === ss.revision && local.syncStatus === 'synced') continue
      await db.settlements.put(fromServerSettlement(ss))
      if ((ss.updated_by ?? ss.created_by) !== g.myMemberId) {
        const who = await nameOf(ss.updated_by ?? ss.created_by)
        const [from, to] = [await nameOf(ss.from_id), await nameOf(ss.to_id)]
        if (!local && !ss.deleted) await log(g.id, 'settlement', `${from} paid ${to}`, formatMoney(Number(ss.amount), g.currency), Number(ss.amount), ss.id, who)
        else if (ss.deleted && local && !local.deleted) await log(g.id, 'settlement_undone', 'Settlement undone', formatMoney(Number(ss.amount), g.currency), Number(ss.amount), ss.id, who)
      }
    }
  })
}

// ------------------------------------------------------------------ Orchestration

let running = false
let again = false

export async function runSync(): Promise<void> {
  if (!cloudEnabled) return
  if (running) {
    again = true
    return
  }
  running = true
  useSyncUi.setState({ syncing: true })
  try {
    do {
      again = false
      const touched = new Set<string>()
      await pushOutbox(touched)
      const groups = (await db.groups.toArray()).filter((g) => g.shared && g.memberToken)
      for (const g of groups) {
        try {
          await pullGroup(g)
        } catch (e) {
          const err = e as CloudError
          if (err.network) throw err
          await db.groups.update(g.id, { syncError: err.message })
        }
      }
      touched.forEach(ping)
      await subscribe(groups)
    } while (again)
    useSyncUi.setState({ lastSyncAt: nowIso(), error: undefined })
  } catch (e) {
    useSyncUi.setState({ error: (e as Error).message })
  } finally {
    running = false
    useSyncUi.setState({ syncing: false })
  }
}

// ------------------------------------------------------------------ Realtime pings

const channels = new Map<string, RealtimeChannel>()

async function subscribe(groups: Group[]) {
  if (!supabase) return
  const wanted = new Set(groups.map((g) => g.id))
  for (const [id, ch] of channels) {
    if (!wanted.has(id)) {
      await supabase.removeChannel(ch)
      channels.delete(id)
    }
  }
  for (const g of groups) {
    if (channels.has(g.id)) continue
    const ch = supabase.channel(`veylo-${g.id}`, { config: { broadcast: { self: false } } })
    ch.on('broadcast', { event: 'changed' }, () => schedule(200)).subscribe()
    channels.set(g.id, ch)
  }
}

function ping(groupId: string) {
  channels.get(groupId)?.send({ type: 'broadcast', event: 'changed', payload: {} }).catch(() => undefined)
}

let timer: ReturnType<typeof setTimeout> | undefined
export function schedule(ms = 600) {
  clearTimeout(timer)
  timer = setTimeout(() => void runSync(), ms)
}

/** Call once at startup. */
export function startSync() {
  if (!cloudEnabled) return
  window.addEventListener('veylo:changed', () => schedule())
  window.addEventListener('online', () => schedule(100))
  document.addEventListener('visibilitychange', () => document.visibilityState === 'visible' && schedule(100))
  setInterval(() => document.visibilityState === 'visible' && void runSync(), 30_000)
  // Anything left "syncing" from a closed tab goes back to pending.
  void Promise.all([
    db.expenses.filter((e) => e.syncStatus === 'syncing').modify({ syncStatus: 'pending' }),
    db.settlements.filter((s) => s.syncStatus === 'syncing').modify({ syncStatus: 'pending' }),
  ]).then(() => schedule(300))
}

// ------------------------------------------------------------------ Joining

export interface InvitePreview {
  group_id: string
  name: string
  type: 'trip' | 'outing'
  destination: string
  currency: string
  start_date: string | null
  members: { id: string; name: string; claimed: boolean }[]
}

export const previewInvite = (code: string) => rpc<InvitePreview>('preview_invite', { p_code: code.toUpperCase().trim() })

/** Joins a shared group, optionally claiming an existing (name-only) member. Returns the group id. */
export async function joinGroup(code: string, claimMemberId: string | null): Promise<string> {
  const p = profile()
  const res = await rpc<{ group_id: string; member_id: string; member_token: string }>('join_group', {
    p_code: code.toUpperCase().trim(), p_name: p.name, p_device: p.deviceId, p_upi: p.upiId || null, p_member_id: claimMemberId,
  })
  const existing = await db.groups.get(res.group_id)
  const now = nowIso()
  const g: Group = existing
    ? { ...existing, memberToken: res.member_token, myMemberId: res.member_id, shared: true }
    : {
        id: res.group_id, name: '…', type: 'trip', destination: '', currency: 'INR', archived: false, createdAt: now, updatedAt: now,
        shared: true, inviteCode: code.toUpperCase(), memberToken: res.member_token, myMemberId: res.member_id, syncStatus: 'syncing',
      }
  await db.groups.put(g)
  await pullGroup(g, true)
  const fresh = await db.groups.get(g.id)
  await log(g.id, 'group_joined', `You joined ${fresh?.name ?? 'the group'}`, 'Via invite', undefined, res.member_id)
  schedule(100)
  return g.id
}
