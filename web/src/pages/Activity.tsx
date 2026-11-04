import { History } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ActivityRow } from '@/components/rows'
import { Chip, EmptyState, PageSkeleton } from '@/components/ui'
import { useAppData } from '@/lib/hooks'
import type { ActivityKind } from '@/lib/types'
import { dayLabel } from '@/lib/utils'

const TYPES: { id: string; label: string; kinds: ActivityKind[] | null }[] = [
  { id: 'all', label: 'All', kinds: null },
  { id: 'expenses', label: 'Expenses', kinds: ['expense_added'] },
  { id: 'edits', label: 'Edits', kinds: ['expense_edited', 'conflict'] },
  { id: 'deleted', label: 'Deleted', kinds: ['expense_deleted'] },
  { id: 'settle', label: 'Settlements', kinds: ['settlement', 'settlement_undone'] },
  { id: 'group', label: 'Group changes', kinds: ['group_created', 'group_joined', 'member_added'] },
]

export function ActivityPage() {
  const d = useAppData()
  const nav = useNavigate()
  const [type, setType] = useState('all')
  const [groupId, setGroupId] = useState<string>('all')

  const grouped = useMemo(() => {
    if (!d) return []
    const kinds = TYPES.find((t) => t.id === type)?.kinds
    const list = d.activity.filter((a) => (!kinds || kinds.includes(a.kind)) && (groupId === 'all' || a.groupId === groupId))
    const map = new Map<string, typeof list>()
    list.forEach((a) => {
      const k = dayLabel(a.at)
      map.set(k, [...(map.get(k) ?? []), a])
    })
    return [...map.entries()]
  }, [d, type, groupId])

  if (!d) return <PageSkeleton />

  return (
    <div className="space-y-4 px-5 pb-6 pt-6 pt-safe lg:px-8 lg:pt-10">
      <h1 className="text-3xl font-extrabold tracking-tight">Activity</h1>
      <div className="no-scrollbar -mx-5 flex gap-2 overflow-x-auto px-5 lg:mx-0 lg:px-0">
        {TYPES.map((t) => <Chip key={t.id} active={type === t.id} onClick={() => setType(t.id)}>{t.label}</Chip>)}
      </div>
      {d.groups.length > 1 && (
        <select
          value={groupId}
          onChange={(e) => setGroupId(e.target.value)}
          className="h-11 w-full rounded-2xl border border-line bg-card px-4 text-sm font-semibold outline-none"
          aria-label="Filter by group"
        >
          <option value="all">All groups</option>
          {d.groups.map((g) => <option key={g.group.id} value={g.group.id}>{g.group.name}</option>)}
        </select>
      )}

      {grouped.length === 0 && <EmptyState icon={History} title="Nothing here yet" body="Expenses, edits, settlements and group changes show up here." />}
      {grouped.map(([day, items]) => (
        <section key={day} className="space-y-2.5">
          <h2 className="pt-2 text-xs font-bold uppercase tracking-wider text-muted">{day}</h2>
          {items.map((a) => {
            const g = d.group(a.groupId)
            const e = g?.expenses.find((x) => x.id === a.entityId)
            const go = e ? () => nav(`/e/${e.id}`) : a.kind.startsWith('settlement') ? () => nav(`/g/${a.groupId}?tab=settle`) : g ? () => nav(`/g/${a.groupId}`) : undefined
            return <ActivityRow key={a.id} a={a} groupName={g?.group.name} category={e?.category} currency={g?.group.currency} onClick={go} />
          })}
        </section>
      ))}
    </div>
  )
}
