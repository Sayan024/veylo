import type { SplitMethod } from './split'

/** local = only on this device · pending = queued · syncing = sent · synced = accepted by server · failed = rejected */
export type SyncStatus = 'local' | 'pending' | 'syncing' | 'synced' | 'failed'
export type GroupType = 'trip' | 'outing'

export interface Group {
  id: string
  name: string
  type: GroupType
  destination: string
  currency: string
  /** Local-only compressed image (data URL). */
  cover?: string | null
  startDate?: string | null
  endDate?: string | null
  archived: boolean
  createdAt: string
  updatedAt: string
  /** Set once the group exists in the cloud. */
  shared: boolean
  inviteCode?: string | null
  /** Secret proving this device's membership. Never shown to users. */
  memberToken?: string | null
  myMemberId: string
  lastPulledAt?: string | null
  syncStatus: SyncStatus
  syncError?: string | null
}

export interface Member {
  id: string
  groupId: string
  name: string
  deviceId?: string | null
  upiId?: string | null
  joinedAt: string
  updatedAt: string
  revision: number
  deleted: boolean
  syncStatus: SyncStatus
}

export interface ShareRow {
  memberId: string
  share: number
  /** Raw editor value (percent, weight or exact minor units) for re-editing. */
  input: number
}

export interface Expense {
  id: string
  groupId: string
  title: string
  amount: number
  currency: string
  payerId: string
  category: string
  /** yyyy-mm-dd */
  date: string
  notes: string
  /** Local-only compressed receipt photo (data URL). */
  receipt?: string | null
  splitMethod: SplitMethod
  shares: ShareRow[]
  createdBy: string
  createdAt: string
  updatedAt: string
  /** Last revision accepted by the server (0 = never synced). */
  revision: number
  deleted: boolean
  syncStatus: SyncStatus
  syncError?: string | null
}

export interface Settlement {
  id: string
  groupId: string
  fromId: string
  toId: string
  amount: number
  createdBy: string
  createdAt: string
  updatedAt: string
  revision: number
  deleted: boolean
  syncStatus: SyncStatus
  syncError?: string | null
}

export type ActivityKind =
  | 'group_created'
  | 'group_joined'
  | 'member_added'
  | 'expense_added'
  | 'expense_edited'
  | 'expense_deleted'
  | 'settlement'
  | 'settlement_undone'
  | 'conflict'

export interface Activity {
  id: string
  groupId: string
  kind: ActivityKind
  title: string
  detail: string
  amount?: number
  entityId?: string
  actor: string
  at: string
}

export type OutboxKind = 'group' | 'member' | 'expense' | 'settlement'

export interface OutboxOp {
  id: string
  groupId: string
  kind: OutboxKind
  entityId: string
  /** Revision the local edit was based on — used for conflict detection. */
  baseRevision: number
  at: string
  attempts: number
  lastError?: string | null
}
