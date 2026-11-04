import { ArrowDownLeft, ArrowUpRight, Coffee, MountainSnow, QrCode, ReceiptText, Split } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { ActivityRow, GroupRow } from '@/components/rows'
import { SyncIndicator } from '@/components/Shell'
import { Avatar, Button, Card, EmptyState, PageSkeleton, SectionHeader } from '@/components/ui'
import { useAppData } from '@/lib/hooks'
import { formatMoney } from '@/lib/money'
import { useProfile } from '@/lib/profile'
import { greeting } from '@/lib/utils'

export function HomePage() {
  const d = useAppData()
  const name = useProfile((s) => s.name)
  const nav = useNavigate()
  if (!d) return <PageSkeleton />

  const active = d.groups.filter((g) => !g.group.archived)
  const settlements = d.activity.filter((a) => a.kind === 'settlement').slice(0, 3)
  const recent = d.activity.filter((a) => a.kind === 'expense_added' || a.kind === 'expense_edited').slice(0, 5)
  const netLabel = d.net > 0 ? 'You are owed' : d.net < 0 ? 'You owe overall' : 'All settled up'

  return (
    <div className="space-y-5 px-5 pb-6 pt-6 pt-safe lg:px-8 lg:pt-10">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-[22px] font-extrabold tracking-tight">{greeting()}, {name.split(' ')[0]} 👋</h1>
          <p className="text-sm text-muted">Ready for your next adventure?</p>
        </div>
        <button onClick={() => nav('/profile')} aria-label="Profile">
          <Avatar name={name || '?'} size={46} />
        </button>
      </header>

      <section className="relative overflow-hidden rounded-[26px] bg-gradient-to-br from-forest to-forest-deep p-6 text-white shadow-lift">
        <p className="text-sm text-white/75">Your Balance</p>
        <p className="mt-1 text-[40px] font-extrabold leading-tight tracking-tight tabular-nums">{formatMoney(Math.abs(d.net), d.mainCurrency)}</p>
        <p className={d.net >= 0 ? 'font-bold text-lime' : 'font-bold text-[#FFB4A6]'}>{netLabel}</p>
        <div className="mt-4 flex items-center gap-2 text-white/80 [&_*]:!text-white/80">
          <SyncIndicator compact />
        </div>
        <button onClick={() => nav('/add')} aria-label="Add expense" className="absolute right-5 top-5 grid h-12 w-12 place-items-center rounded-2xl bg-lime text-forest-deep transition hover:brightness-95 active:scale-95">
          <ReceiptText size={22} />
        </button>
      </section>

      <div className="grid grid-cols-2 gap-3">
        <div className="flex items-center gap-3 rounded-[20px] bg-owe-bg p-4">
          <span className="grid h-9 w-9 place-items-center rounded-xl bg-owe/15 text-owe"><ArrowUpRight size={18} /></span>
          <div className="min-w-0">
            <p className="text-[11px] font-semibold text-owe/80">You owe</p>
            <p className="truncate font-bold text-owe tabular-nums">{formatMoney(d.youOwe, d.mainCurrency)}</p>
          </div>
        </div>
        <div className="flex items-center gap-3 rounded-[20px] bg-owed-bg p-4">
          <span className="grid h-9 w-9 place-items-center rounded-xl bg-owed/15 text-owed"><ArrowDownLeft size={18} /></span>
          <div className="min-w-0">
            <p className="text-[11px] font-semibold text-owed/80">Owed to you</p>
            <p className="truncate font-bold text-owed tabular-nums">{formatMoney(d.owedToYou, d.mainCurrency)}</p>
          </div>
        </div>
      </div>

      <div className="no-scrollbar -mx-5 flex gap-2 overflow-x-auto px-5 lg:mx-0 lg:px-0">
        {[
          { label: 'Add Expense', icon: ReceiptText, to: '/add' },
          { label: 'Split Bill', icon: Split, to: '/add?split=1' },
          { label: 'Create Trip', icon: MountainSnow, to: '/new/trip' },
          { label: 'Start Outing', icon: Coffee, to: '/new/outing' },
          { label: 'Join Group', icon: QrCode, to: '/join' },
        ].map((q) => (
          <button key={q.label} onClick={() => nav(q.to)} className="flex shrink-0 items-center gap-2 rounded-full bg-card px-4 py-2.5 text-sm font-bold transition hover:shadow-soft active:scale-95">
            <q.icon size={16} className="text-forest dark:text-lime" /> {q.label}
          </button>
        ))}
      </div>

      {d.groups.length === 0 ? (
        <Card>
          <EmptyState icon={MountainSnow} title="Plan your first trip" body="Create a trip or a quick outing, add friends, and VEYLO keeps everyone's balance straight.">
            <Button icon={MountainSnow} onClick={() => nav('/new/trip')}>Create Trip</Button>
            <Button icon={Coffee} variant="soft" onClick={() => nav('/new/outing')}>Start Outing</Button>
            <Button icon={QrCode} variant="soft" onClick={() => nav('/join')}>Join with a code</Button>
          </EmptyState>
        </Card>
      ) : (
        <>
          <SectionHeader title="Active groups" action="See all" onAction={() => nav('/groups')} />
          <div className="space-y-3">
            {active.slice(0, 4).map((g) => <GroupRow key={g.group.id} g={g} onClick={() => nav(`/g/${g.group.id}`)} />)}
            {active.length === 0 && <p className="text-sm text-muted">All groups are completed.</p>}
          </div>

          {recent.length > 0 && (
            <>
              <SectionHeader title="Recent expenses" action="View all" onAction={() => nav('/activity')} />
              <div className="space-y-2.5">
                {recent.map((a) => {
                  const g = d.group(a.groupId)
                  const e = g?.expenses.find((x) => x.id === a.entityId)
                  return <ActivityRow key={a.id} a={a} groupName={g?.group.name} category={e?.category} currency={g?.group.currency} onClick={e ? () => nav(`/e/${e.id}`) : undefined} />
                })}
              </div>
            </>
          )}

          {settlements.length > 0 && (
            <>
              <SectionHeader title="Recent settlements" />
              <div className="space-y-2.5">
                {settlements.map((a) => <ActivityRow key={a.id} a={a} groupName={d.group(a.groupId)?.group.name} currency={d.group(a.groupId)?.group.currency} onClick={() => nav(`/g/${a.groupId}?tab=settle`)} />)}
              </div>
            </>
          )}
        </>
      )}
    </div>
  )
}
