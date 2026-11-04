import { ArrowLeft, Copy, Pencil, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { Amount, Avatar, Button, CategoryBadge, EmptyState, IconButton, PageSkeleton, Sheet, SyncBadge, category } from '@/components/ui'
import { deleteExpense } from '@/lib/data'
import { useAppData } from '@/lib/hooks'
import { formatMoney } from '@/lib/money'
import { SPLIT_METHODS } from '@/lib/split'
import { fmtDateTime } from '@/lib/utils'

const STATUS_TEXT = {
  synced: 'Saved in the shared group.',
  syncing: 'Uploading…',
  pending: 'Saved on this device. Syncs automatically when online.',
  failed: 'The server rejected this change. Edit it to try again.',
  local: 'Only on this device — local mode.',
} as const

export function ExpenseDetailPage() {
  const { id = '' } = useParams()
  const d = useAppData()
  const nav = useNavigate()
  const [confirm, setConfirm] = useState(false)
  if (!d) return <PageSkeleton />
  const g = d.groups.find((s) => s.expenses.some((e) => e.id === id))
  const e = g?.expenses.find((x) => x.id === id)
  if (!g || !e) {
    return (
      <EmptyState icon={Trash2} title="Expense not found" body="It may have been deleted.">
        <Button onClick={() => nav(-1)}>Go back</Button>
      </EmptyState>
    )
  }

  return (
    <div className="pb-32">
      <header className="flex items-center gap-3 px-4 py-3 pt-safe lg:px-8 lg:pt-8">
        <IconButton icon={ArrowLeft} label="Back" onClick={() => nav(-1)} />
        <button onClick={() => nav(`/g/${g.group.id}`)} className="flex-1 truncate text-left text-sm font-semibold text-muted hover:text-ink">{g.group.name}</button>
        <IconButton icon={Copy} label="Duplicate" onClick={() => nav(`/add?duplicate=${e.id}`)} />
      </header>

      <div className="space-y-4 px-5 lg:px-8">
        <div className="flex flex-col items-center py-4 text-center">
          <CategoryBadge id={e.category} size={64} />
          <h1 className="mt-3 text-xl font-extrabold">{e.title}</h1>
          <Amount value={e.amount} currency={e.currency} className="text-5xl font-extrabold tracking-tight" />
          <p className="mt-1 text-xs text-muted">{category(e.category).label} · {new Date(e.date).toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric' })}</p>
          <div className="mt-3"><SyncBadge status={e.syncStatus} /></div>
          <p className="mt-1 text-xs text-muted">{e.syncError ?? STATUS_TEXT[e.syncStatus]}</p>
        </div>

        <div className="flex items-center gap-3 rounded-[22px] bg-card p-4">
          <Avatar name={g.member(e.payerId)?.name ?? '?'} size={42} />
          <div className="flex-1">
            <p className="text-[11px] text-muted">Paid by</p>
            <p className="font-bold">{g.name(e.payerId)}</p>
          </div>
          <Amount value={e.amount} currency={e.currency} className="font-bold" />
        </div>

        <div className="rounded-[22px] bg-card p-4">
          <div className="mb-2 flex items-center justify-between">
            <p className="font-bold">Split {SPLIT_METHODS.find((m) => m.id === e.splitMethod)?.label.toLowerCase()}</p>
            <span className="text-xs text-muted">{e.shares.length} people</span>
          </div>
          <div className="divide-y divide-line">
            {e.shares.map((s) => (
              <div key={s.memberId} className="flex items-center gap-3 py-2.5">
                <Avatar name={g.member(s.memberId)?.name ?? '?'} size={32} />
                <div className="flex-1">
                  <p className="text-sm font-semibold">{g.name(s.memberId)}</p>
                  <p className="text-xs text-muted">
                    {s.memberId === e.payerId ? 'paid for the group' : `owes ${g.name(e.payerId)}`}
                    {e.splitMethod === 'percent' && ` · ${s.input}%`}
                    {e.splitMethod === 'shares' && ` · ${s.input} share${s.input === 1 ? '' : 's'}`}
                  </p>
                </div>
                <span className="text-sm font-bold tabular-nums">{formatMoney(s.share, e.currency)}</span>
              </div>
            ))}
          </div>
        </div>

        {e.notes && (
          <div className="rounded-[22px] bg-card p-4">
            <p className="text-[11px] font-semibold text-muted">Notes</p>
            <p className="mt-1 whitespace-pre-wrap text-sm">{e.notes}</p>
          </div>
        )}

        {e.receipt && (
          <div>
            <p className="mb-2 font-bold">Receipt</p>
            <a href={e.receipt} target="_blank" rel="noreferrer">
              <img src={e.receipt} alt="Receipt" className="max-h-96 w-full rounded-[22px] object-cover" />
            </a>
          </div>
        )}

        <div className="rounded-[22px] bg-card p-4 text-xs text-muted">
          <p>Created {fmtDateTime(e.createdAt)} by {g.name(e.createdBy)}</p>
          <p>Last modified {fmtDateTime(e.updatedAt)}{e.revision > 1 ? ` · revision ${e.revision}` : ''}</p>
        </div>
      </div>

      <div className="fixed inset-x-0 bottom-0 z-20 bg-gradient-to-t from-bg via-bg to-transparent px-5 pb-safe pt-6 lg:left-64">
        <div className="mx-auto mb-4 grid max-w-3xl grid-cols-2 gap-3 lg:px-3">
          <Button variant="danger" icon={Trash2} onClick={() => setConfirm(true)}>Delete</Button>
          <Button variant="forest" icon={Pencil} onClick={() => nav(`/e/${e.id}/edit`)}>Edit</Button>
        </div>
      </div>

      <Sheet open={confirm} onClose={() => setConfirm(false)} title="Delete expense?">
        <p className="text-sm text-muted">“{e.title}” will be removed for everyone in the group once synced. It stays in the activity history.</p>
        <div className="mt-5 grid grid-cols-2 gap-2">
          <Button variant="soft" onClick={() => setConfirm(false)}>Cancel</Button>
          <Button variant="danger" onClick={async () => {
            await deleteExpense(e.id)
            toast.success('Expense deleted')
            nav(`/g/${g.group.id}`, { replace: true })
          }}>Delete</Button>
        </div>
      </Sheet>
    </div>
  )
}
