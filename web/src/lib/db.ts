import Dexie, { type Table } from 'dexie'
import type { Activity, Expense, Group, Member, OutboxOp, Settlement } from './types'

/** Local IndexedDB store. Every change is written here first, then synced. */
class VeyloDB extends Dexie {
  groups!: Table<Group, string>
  members!: Table<Member, string>
  expenses!: Table<Expense, string>
  settlements!: Table<Settlement, string>
  activity!: Table<Activity, string>
  outbox!: Table<OutboxOp, string>

  constructor() {
    super('veylo')
    this.version(1).stores({
      groups: 'id, updatedAt',
      members: 'id, groupId',
      expenses: 'id, groupId, updatedAt',
      settlements: 'id, groupId',
      activity: 'id, groupId, at',
      outbox: 'id, groupId, at, [kind+entityId]',
    })
  }
}

export const db = new VeyloDB()
