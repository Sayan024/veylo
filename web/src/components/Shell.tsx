import { Activity, CloudOff, Home, MountainSnow as Landscape, Plus, QrCode, ReceiptText, Split, User, Users, Coffee, type LucideIcon } from 'lucide-react'
import { useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { cloudEnabled } from '@/lib/cloud'
import { useAppData, useOnline } from '@/lib/hooks'
import { useSyncUi } from '@/lib/sync'
import { cn, haptic } from '@/lib/utils'
import { Logo, Sheet } from './ui'

const NAV: { to: string; label: string; icon: LucideIcon }[] = [
  { to: '/', label: 'Home', icon: Home },
  { to: '/groups', label: 'Groups', icon: Users },
  { to: '/activity', label: 'Activity', icon: Activity },
  { to: '/profile', label: 'Profile', icon: User },
]

const TAB_ROUTES = ['/', '/groups', '/activity', '/profile']

export function QuickActions({ open, onClose }: { open: boolean; onClose: () => void }) {
  const nav = useNavigate()
  const go = (to: string) => {
    onClose()
    nav(to)
  }
  const tiles: { label: string; icon: LucideIcon; to: string }[] = [
    { label: 'Add Expense', icon: ReceiptText, to: '/add' },
    { label: 'Split Bill', icon: Split, to: '/add?split=1' },
    { label: 'Create Trip', icon: Landscape, to: '/new/trip' },
    { label: 'Start Outing', icon: Coffee, to: '/new/outing' },
  ]
  return (
    <Sheet open={open} onClose={onClose} title="Quick actions">
      <div className="grid grid-cols-2 gap-3">
        {tiles.map((t) => (
          <button key={t.label} onClick={() => go(t.to)} className="flex items-center gap-3 rounded-[20px] bg-card p-4 text-left transition hover:shadow-soft active:scale-[0.98]">
            <span className="grid h-10 w-10 place-items-center rounded-xl bg-lime-tint text-forest dark:text-lime">
              <t.icon size={20} />
            </span>
            <span className="text-sm font-bold">{t.label}</span>
          </button>
        ))}
      </div>
      <button onClick={() => go('/join')} className="mt-3 flex w-full items-center gap-3 rounded-[20px] bg-forest p-4 text-left text-white transition active:scale-[0.98]">
        <span className="grid h-10 w-10 place-items-center rounded-xl bg-lime text-forest-deep">
          <QrCode size={20} />
        </span>
        <span className="text-sm font-bold">Join Group</span>
      </button>
    </Sheet>
  )
}

function SyncIndicator({ compact }: { compact?: boolean }) {
  const online = useOnline()
  const data = useAppData()
  const { syncing } = useSyncUi()
  const pending = data?.pendingCount ?? 0
  if (!cloudEnabled) return compact ? null : <p className="px-3 text-xs text-muted">Local mode · data stays on this device</p>
  const text = !online ? `Offline${pending ? ` · ${pending} pending` : ''}` : syncing ? 'Syncing…' : pending ? `${pending} pending` : 'All synced'
  return (
    <div className={cn('flex items-center gap-2 text-xs font-semibold', !online || pending ? 'text-warn' : 'text-muted', compact ? '' : 'px-3')}>
      {!online ? <CloudOff size={14} /> : <span className={cn('h-2 w-2 rounded-full', syncing ? 'animate-pulse bg-warn' : pending ? 'bg-warn' : 'bg-owed')} />}
      {text}
    </div>
  )
}

export function Shell() {
  const [actions, setActions] = useState(false)
  const loc = useLocation()
  const isTab = TAB_ROUTES.includes(loc.pathname)
  const online = useOnline()

  return (
    <div className="min-h-screen lg:flex">
      {/* Desktop sidebar */}
      <aside className="sticky top-0 hidden h-screen w-64 shrink-0 flex-col border-r border-line bg-card/60 p-5 lg:flex">
        <div className="mb-8 flex items-center gap-2 px-2">
          <div className="grid h-10 w-10 place-items-center rounded-xl bg-forest">
            <Logo size={26} />
          </div>
          <span className="text-xl font-extrabold tracking-[0.12em]">VEYLO</span>
        </div>
        <nav className="flex flex-col gap-1">
          {NAV.map((n) => (
            <NavLink
              key={n.to}
              to={n.to}
              end={n.to === '/'}
              className={({ isActive }) =>
                cn('flex items-center gap-3 rounded-2xl px-4 py-3 text-sm font-bold transition',
                  isActive ? 'bg-lime-tint text-forest dark:text-lime' : 'text-muted hover:bg-cardalt hover:text-ink')
              }
            >
              <n.icon size={20} /> {n.label}
            </NavLink>
          ))}
        </nav>
        <button
          onClick={() => setActions(true)}
          className="mt-6 flex h-12 items-center justify-center gap-2 rounded-[18px] bg-lime font-bold text-forest-deep shadow-soft transition hover:brightness-95 active:scale-[0.98]"
        >
          <Plus size={20} /> New
        </button>
        <div className="mt-auto">
          <SyncIndicator />
        </div>
      </aside>

      <main className={cn('mx-auto w-full max-w-3xl flex-1', isTab && 'pb-28 lg:pb-10')}>
        {!online && (
          <div className="sticky top-0 z-30 flex items-center justify-center gap-2 bg-forest px-4 py-2 pt-safe text-xs font-semibold text-white lg:hidden">
            <CloudOff size={14} /> You're offline — changes are saved and will sync later
          </div>
        )}
        <div key={loc.pathname} className="animate-fade">
          <Outlet />
        </div>
      </main>

      {/* Mobile bottom nav */}
      {isTab && (
        <>
          <button
            aria-label="New"
            onClick={() => {
              haptic()
              setActions(true)
            }}
            className="fixed bottom-24 right-5 z-40 grid h-[60px] w-[60px] place-items-center rounded-full bg-lime text-forest-deep shadow-lift transition active:scale-95 lg:hidden"
          >
            <Plus size={28} strokeWidth={2.5} />
          </button>
          <nav className="fixed inset-x-0 bottom-0 z-30 flex justify-around rounded-t-[28px] bg-card px-2 pb-safe pt-2 shadow-[0_-8px_30px_-12px_rgba(15,42,29,0.25)] lg:hidden">
            {NAV.map((n) => (
              <NavLink key={n.to} to={n.to} end={n.to === '/'} className="flex flex-col items-center gap-0.5 px-3 pb-2 pt-1">
                {({ isActive }) => (
                  <>
                    <span className={cn('rounded-full px-4 py-1 transition', isActive && 'bg-lime-tint')}>
                      <n.icon size={22} className={isActive ? 'text-forest dark:text-lime' : 'text-muted'} />
                    </span>
                    <span className={cn('text-[11px] font-semibold', isActive ? 'text-ink' : 'text-muted')}>{n.label}</span>
                  </>
                )}
              </NavLink>
            ))}
          </nav>
        </>
      )}
      <QuickActions open={actions} onClose={() => setActions(false)} />
    </div>
  )
}

export { SyncIndicator }
