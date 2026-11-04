import { ChevronRight, Cloud, CloudOff } from 'lucide-react'
import type { GroupSummary } from '@/lib/hooks'
import { formatMoney } from '@/lib/money'
import type { Activity, Expense } from '@/lib/types'
import { cn, fmtDate, fmtTime } from '@/lib/utils'
import { Amount, CategoryBadge, Cover, SyncBadge } from './ui'

export function balanceText(bal: number, currency: string) {
  if (bal > 0) return { text: `you get ${formatMoney(bal, currency)}`, cls: 'text-owed' }
  if (bal < 0) return { text: `you owe ${formatMoney(-bal, currency)}`, cls: 'text-owe' }
  return { text: 'settled up', cls: 'text-muted' }
}

export function GroupRow({ g, onClick }: { g: GroupSummary; onClick: () => void }) {
  const b = balanceText(g.myBalance, g.group.currency)
  return (
    <button onClick={onClick} className="flex w-full items-center gap-4 rounded-[22px] bg-card p-3 text-left transition hover:shadow-soft active:scale-[0.99]">
      <Cover group={g.group} className="h-[58px] w-[58px] shrink-0 overflow-hidden rounded-2xl" />
      <div className="min-w-0 flex-1">
        <p className="truncate font-bold">{g.group.name}</p>
        <p className={cn('truncate text-xs font-semibold', b.cls)}>
          <span className="text-muted">{g.members.length} members · </span>
          {b.text}
        </p>
      </div>
      <span className="grid h-9 w-9 place-items-center rounded-full bg-lime-tint text-forest dark:text-lime" title={g.group.shared ? 'Shared' : 'This device only'}>
        {g.group.shared ? <Cloud size={16} /> : <CloudOff size={16} className="text-muted" />}
      </span>
    </button>
  )
}

export function ExpenseRow({ e, g, onClick }: { e: Expense; g: GroupSummary; onClick: () => void }) {
  return (
    <button onClick={onClick} className="flex w-full items-center gap-3 rounded-[18px] bg-card p-3 text-left transition hover:shadow-soft active:scale-[0.99]">
      <CategoryBadge id={e.category} />
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-bold">{e.title}</p>
        <p className="truncate text-xs text-muted">
          {g.name(e.payerId)} paid · {fmtDate(e.date)}
        </p>
      </div>
      <div className="flex flex-col items-end gap-1">
        <Amount value={e.amount} currency={e.currency} decimals={false} className="font-bold" />
        <SyncBadge status={e.syncStatus} compact />
      </div>
    </button>
  )
}

const KIND_LABEL: Record<Activity['kind'], string> = {
  group_created: 'Group', group_joined: 'Joined', member_added: 'Member', expense_added: 'Expense',
  expense_edited: 'Edited', expense_deleted: 'Deleted', settlement: 'Settled', settlement_undone: 'Undone', conflict: 'Conflict',
}

export function ActivityRow({ a, groupName, onClick, category, currency }: { a: Activity; groupName?: string; onClick?: () => void; category?: string; currency?: string }) {
  const settle = a.kind === 'settlement' || a.kind === 'settlement_undone'
  return (
    <button onClick={onClick} disabled={!onClick} className="flex w-full items-center gap-3 rounded-[18px] bg-card p-3 text-left transition enabled:hover:shadow-soft">
      <CategoryBadge id={category} settlement={settle} />
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <p className={cn('truncate text-sm font-bold', a.kind === 'expense_deleted' && 'line-through decoration-muted')}>{a.title}</p>
          <span className={cn('shrink-0 rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide',
            a.kind === 'conflict' ? 'bg-owe-bg text-owe' : 'bg-cardalt text-muted')}>{KIND_LABEL[a.kind]}</span>
        </div>
        <p className="truncate text-xs text-muted">
          {a.actor} · {groupName ? `${groupName} · ` : ''}{fmtTime(a.at)}
        </p>
        {a.kind === 'conflict' && <p className="mt-1 text-xs text-owe">{a.detail}</p>}
      </div>
      {a.amount != null && a.kind !== 'conflict' && (
        <span className={cn('text-sm font-bold tabular-nums', settle && 'text-owed', a.kind === 'expense_deleted' && 'text-muted line-through')}>
          {formatMoney(a.amount, currency ?? 'INR', false)}
        </span>
      )}
      {onClick && <ChevronRight size={16} className="text-muted" />}
    </button>
  )
}
