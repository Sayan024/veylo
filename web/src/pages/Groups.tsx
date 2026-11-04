import { Coffee, MountainSnow, QrCode, Search, Users } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { balanceText } from '@/components/rows'
import { AvatarStack, Button, Cover, EmptyState, PageSkeleton, Segmented } from '@/components/ui'
import { useAppData } from '@/lib/hooks'
import { formatMoney } from '@/lib/money'
import { cn } from '@/lib/utils'

type Filter = 'active' | 'completed' | 'all'

export function GroupsPage() {
  const d = useAppData()
  const nav = useNavigate()
  const [filter, setFilter] = useState<Filter>('active')
  const [q, setQ] = useState('')
  if (!d) return <PageSkeleton />

  const shown = d.groups
    .filter((g) => (filter === 'all' ? true : filter === 'completed' ? g.group.archived : !g.group.archived))
    .filter((g) => `${g.group.name} ${g.group.destination}`.toLowerCase().includes(q.trim().toLowerCase()))

  return (
    <div className="space-y-4 px-5 pb-6 pt-6 pt-safe lg:px-8 lg:pt-10">
      <header className="flex items-center justify-between gap-3">
        <h1 className="text-3xl font-extrabold tracking-tight">Groups</h1>
        <div className="flex gap-2">
          <Button size="sm" variant="soft" icon={QrCode} onClick={() => nav('/join')}>Join</Button>
          <Button size="sm" icon={MountainSnow} onClick={() => nav('/new/trip')} className="hidden sm:inline-flex">New trip</Button>
        </div>
      </header>

      <div className="flex h-12 items-center gap-2 rounded-2xl bg-card px-4">
        <Search size={18} className="text-muted" />
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search groups" className="h-full flex-1 bg-transparent text-sm font-medium outline-none placeholder:text-muted" />
      </div>
      <Segmented options={[{ id: 'active', label: 'Active' }, { id: 'completed', label: 'Completed' }, { id: 'all', label: 'All' }]} value={filter} onChange={setFilter} />

      {shown.length === 0 ? (
        <EmptyState icon={Users} title={q ? 'No matches' : 'No groups here'} body={q ? 'Try a different name.' : 'Create a trip, start an outing, or join friends with a code.'}>
          {!q && (
            <>
              <Button icon={MountainSnow} onClick={() => nav('/new/trip')}>Create Trip</Button>
              <Button icon={Coffee} variant="soft" onClick={() => nav('/new/outing')}>Start Outing</Button>
            </>
          )}
        </EmptyState>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          {shown.map((g) => {
            const b = balanceText(g.myBalance, g.group.currency)
            return (
              <button key={g.group.id} onClick={() => nav(`/g/${g.group.id}`)} className="overflow-hidden rounded-[24px] bg-card text-left transition hover:-translate-y-0.5 hover:shadow-soft active:scale-[0.99]">
                <div className="relative h-36">
                  <Cover group={g.group} className="absolute inset-0 h-full w-full" />
                  <div className="absolute inset-0 bg-gradient-to-b from-transparent to-forest-deep/75" />
                  <div className="absolute left-3 top-3 flex gap-1.5">
                    <span className="rounded-full bg-lime px-2.5 py-1 text-[10px] font-extrabold tracking-wider text-forest-deep">{g.group.type.toUpperCase()}</span>
                    {g.group.archived && <span className="rounded-full bg-black/40 px-2.5 py-1 text-[10px] font-bold text-white">COMPLETED</span>}
                  </div>
                  <div className="absolute bottom-3 left-4 right-4 text-white">
                    <p className="truncate text-xl font-extrabold">{g.group.name}</p>
                    {g.group.destination && <p className="truncate text-xs text-white/80">{g.group.destination}</p>}
                  </div>
                </div>
                <div className="flex items-center justify-between p-4">
                  <div>
                    <AvatarStack names={g.members.map((m) => m.name)} />
                    <p className={cn('mt-1.5 text-xs font-semibold', b.cls)}>{b.text}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-[11px] text-muted">Total spent</p>
                    <p className="font-bold tabular-nums">{formatMoney(g.totalSpent, g.group.currency)}</p>
                  </div>
                </div>
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}
