import {
  CheckCircle2, CircleAlert, Clock, Coffee, Car, Hotel, MonitorSmartphone, PartyPopper, Receipt, RefreshCw, ShoppingBag, UtensilsCrossed, X,
  type LucideIcon,
} from 'lucide-react'
import { forwardRef, useEffect, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { formatMoney } from '@/lib/money'
import type { Group, SyncStatus } from '@/lib/types'
import { avatarColor, cn, haptic, hash, initials } from '@/lib/utils'

// ------------------------------------------------------------------ Button

type Variant = 'lime' | 'forest' | 'soft' | 'ghost' | 'danger'
const variants: Record<Variant, string> = {
  lime: 'bg-lime text-forest-deep hover:brightness-95 shadow-soft',
  forest: 'bg-forest text-white hover:bg-forest-soft dark:bg-lime dark:text-forest-deep',
  soft: 'bg-cardalt text-ink hover:bg-line',
  ghost: 'text-ink hover:bg-cardalt',
  danger: 'bg-owe-bg text-owe hover:brightness-95',
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  icon?: LucideIcon
  size?: 'md' | 'sm' | 'lg'
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'lime', icon: Icon, size = 'md', className, children, onClick, ...rest }, ref,
) {
  const h = size === 'lg' ? 'h-14 text-[15px] px-6' : size === 'sm' ? 'h-10 text-sm px-4' : 'h-12 text-[15px] px-5'
  return (
    <button
      ref={ref}
      className={cn(
        'inline-flex select-none items-center justify-center gap-2 rounded-[18px] font-bold transition active:scale-[0.97] disabled:pointer-events-none disabled:opacity-40',
        h, variants[variant], className,
      )}
      onClick={(e) => {
        haptic()
        onClick?.(e)
      }}
      {...rest}
    >
      {Icon && <Icon size={size === 'sm' ? 16 : 19} strokeWidth={2.4} />}
      {children}
    </button>
  )
})

export function IconButton({ icon: Icon, label, className, onClick, dark }: { icon: LucideIcon; label: string; className?: string; onClick?: () => void; dark?: boolean }) {
  return (
    <button
      aria-label={label}
      title={label}
      onClick={onClick}
      className={cn(
        'grid h-11 w-11 shrink-0 place-items-center rounded-full transition active:scale-95',
        dark ? 'bg-black/35 text-white backdrop-blur hover:bg-black/45' : 'bg-card text-ink shadow-soft hover:bg-cardalt',
        className,
      )}
    >
      <Icon size={20} />
    </button>
  )
}

// ------------------------------------------------------------------ Layout bits

export function Card({ className, children, onClick }: { className?: string; children: ReactNode; onClick?: () => void }) {
  return (
    <div
      onClick={onClick}
      className={cn('rounded-[22px] bg-card p-5', onClick && 'cursor-pointer transition hover:-translate-y-0.5 hover:shadow-soft active:scale-[0.99]', className)}
    >
      {children}
    </div>
  )
}

export function SectionHeader({ title, action, onAction }: { title: string; action?: string; onAction?: () => void }) {
  return (
    <div className="flex items-center justify-between">
      <h2 className="text-lg font-bold tracking-tight">{title}</h2>
      {action && (
        <button onClick={onAction} className="rounded-lg px-2 py-1 text-xs font-semibold text-muted hover:bg-cardalt">
          {action} →
        </button>
      )}
    </div>
  )
}

export function EmptyState({ icon: Icon, title, body, children }: { icon: LucideIcon; title: string; body: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center px-6 py-10 text-center animate-rise">
      <div className="grid h-[72px] w-[72px] place-items-center rounded-full bg-lime-tint">
        <Icon className="text-forest dark:text-lime" size={30} />
      </div>
      <h3 className="mt-4 text-lg font-bold">{title}</h3>
      <p className="mt-1 max-w-sm text-sm text-muted">{body}</p>
      {children && <div className="mt-5 flex w-full max-w-xs flex-col gap-2.5">{children}</div>}
    </div>
  )
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn('skeleton', className)} />
}

export function PageSkeleton() {
  return (
    <div className="space-y-4 p-5">
      <Skeleton className="h-8 w-2/3" />
      <Skeleton className="h-40 w-full rounded-[26px]" />
      <div className="grid grid-cols-2 gap-3">
        <Skeleton className="h-20" />
        <Skeleton className="h-20" />
      </div>
      <Skeleton className="h-20 w-full" />
      <Skeleton className="h-20 w-full" />
    </div>
  )
}

export function Segmented<T extends string>({ options, value, onChange, className }: { options: { id: T; label: string }[]; value: T; onChange: (v: T) => void; className?: string }) {
  return (
    <div className={cn('flex gap-1 rounded-full bg-cardalt p-1', className)}>
      {options.map((o) => (
        <button
          key={o.id}
          onClick={() => onChange(o.id)}
          className={cn(
            'flex-1 rounded-full px-3 py-2.5 text-xs font-bold transition',
            o.id === value ? 'bg-forest text-white shadow-soft dark:bg-lime dark:text-forest-deep' : 'text-muted hover:text-ink',
          )}
        >
          {o.label}
        </button>
      ))}
    </div>
  )
}

// ------------------------------------------------------------------ Inputs

export function Field({ label, error, children, hint }: { label: string; error?: string; hint?: string; children: ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-xs font-semibold text-muted">{label}</span>
      {children}
      {error ? <span className="mt-1 block text-xs font-semibold text-owe">{error}</span> : hint && <span className="mt-1 block text-xs text-muted">{hint}</span>}
    </label>
  )
}

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { prefix?: string }>(function Input({ className, prefix, ...rest }, ref) {
  return (
    <div className={cn('flex h-[52px] items-center rounded-2xl border border-line bg-card px-4 transition focus-within:border-forest dark:focus-within:border-lime', className)}>
      {prefix && <span className="mr-2 font-semibold text-muted">{prefix}</span>}
      <input ref={ref} className="h-full w-full bg-transparent text-[15px] font-medium outline-none placeholder:text-muted/70" {...rest} />
    </div>
  )
})

export function Chip({ active, onClick, children, className }: { active?: boolean; onClick?: () => void; children: ReactNode; className?: string }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'inline-flex shrink-0 items-center gap-1.5 rounded-full px-3.5 py-2 text-sm font-semibold transition',
        active ? 'bg-lime text-forest-deep' : 'bg-card text-ink hover:bg-cardalt',
        className,
      )}
    >
      {children}
    </button>
  )
}

// ------------------------------------------------------------------ Avatars

export function Avatar({ name, size = 40, ring, className }: { name: string; size?: number; ring?: boolean; className?: string }) {
  return (
    <div
      className={cn('grid shrink-0 place-items-center rounded-full font-bold text-white', ring && 'ring-2 ring-card', className)}
      style={{ width: size, height: size, background: avatarColor(name), fontSize: size * 0.38 }}
      title={name}
    >
      {initials(name)}
    </div>
  )
}

export function AvatarStack({ names, size = 30, max = 4 }: { names: string[]; size?: number; max?: number }) {
  return (
    <div className="flex -space-x-2.5">
      {names.slice(0, max).map((n, i) => (
        <Avatar key={i} name={n} size={size} ring />
      ))}
      {names.length > max && (
        <div className="grid place-items-center rounded-full bg-cardalt text-[11px] font-bold ring-2 ring-card" style={{ width: size, height: size }}>
          +{names.length - max}
        </div>
      )}
    </div>
  )
}

// ------------------------------------------------------------------ Brand art

export function Logo({ size = 40, className }: { size?: number; className?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 100 100" className={className} aria-label="VEYLO">
      <path d="M20 24 L46 76" stroke="#8FD13F" strokeWidth="19" strokeLinecap="round" />
      <path d="M46 76 L82 18" stroke="#C8FF65" strokeWidth="19" strokeLinecap="round" />
    </svg>
  )
}

/** Layered hills illustration, deterministic per seed. */
export function HillsArt({ seed, outing, className }: { seed: string; outing?: boolean; className?: string }) {
  const h = hash(seed)
  const sky = outing ? ['#F4E6CC', '#DCE8C4'] : ['#B9D3BE', '#E6EFDD']
  const layers = outing ? ['#9DBB7A', '#4F7A52', '#163D2A'] : ['#6E9A7B', '#2B5A40', '#0F2A1D']
  const W = 400, H = 240
  const paths = layers.map((_, i) => {
    const base = H * (0.5 + i * 0.17)
    const peaks = 3 + i
    let d = `M0 ${H} L0 ${base}`
    for (let k = 1; k <= peaks; k++) {
      const x = (W * k) / peaks
      const jitter = ((h >> (k + i * 3)) & 31) / 31
      const peakY = base - (H * (0.12 + 0.16 * jitter)) / (i * 0.4 + 1)
      d += ` Q${x - W / peaks / 2} ${peakY - H * 0.05} ${x} ${base - H * 0.03 * jitter}`
    }
    return `${d} L${W} ${H} Z`
  })
  const gid = `sky${h}${outing ? 'o' : 't'}`
  return (
    <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="xMidYMid slice" className={className}>
      <defs>
        <linearGradient id={gid} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor={sky[0]} />
          <stop offset="1" stopColor={sky[1]} />
        </linearGradient>
      </defs>
      <rect width={W} height={H} fill={`url(#${gid})`} />
      <circle cx={W * (0.3 + (h % 25) / 100)} cy={H * 0.42} r={H * 0.11} fill={outing ? '#FFD27A' : '#FBFDF3'} opacity={0.95} />
      {paths.map((d, i) => (
        <path key={i} d={d} fill={layers[i]} />
      ))}
    </svg>
  )
}

export function Cover({ group, className }: { group: Pick<Group, 'id' | 'cover' | 'type'>; className?: string }) {
  return group.cover ? (
    <img src={group.cover} alt="" className={cn('object-cover', className)} />
  ) : (
    <HillsArt seed={group.id} outing={group.type === 'outing'} className={className} />
  )
}

// ------------------------------------------------------------------ Status & categories

const STATUS: Record<SyncStatus, { label: string; icon: LucideIcon; cls: string }> = {
  synced: { label: 'Synced', icon: CheckCircle2, cls: 'text-owed bg-owed-bg' },
  syncing: { label: 'Syncing', icon: RefreshCw, cls: 'text-warn bg-warn/10' },
  pending: { label: 'Pending', icon: Clock, cls: 'text-warn bg-warn/10' },
  failed: { label: 'Failed', icon: CircleAlert, cls: 'text-owe bg-owe-bg' },
  local: { label: 'This device', icon: MonitorSmartphone, cls: 'text-muted bg-cardalt' },
}

export function SyncBadge({ status, compact }: { status: SyncStatus; compact?: boolean }) {
  const s = STATUS[status]
  if (compact) return <s.icon size={14} className={cn(s.cls.split(' ')[0], status === 'syncing' && 'animate-spin')} aria-label={s.label} />
  return (
    <span className={cn('inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-[11px] font-bold', s.cls)}>
      <s.icon size={12} className={cn(status === 'syncing' && 'animate-spin')} /> {s.label}
    </span>
  )
}

export interface Category {
  id: string
  label: string
  icon: LucideIcon
  color: string
}
export const CATEGORIES: Category[] = [
  { id: 'food', label: 'Food', icon: UtensilsCrossed, color: '#E07A3F' },
  { id: 'cafe', label: 'Café', icon: Coffee, color: '#8B5E3C' },
  { id: 'stay', label: 'Stay', icon: Hotel, color: '#D45C7A' },
  { id: 'travel', label: 'Travel', icon: Car, color: '#2F8F5B' },
  { id: 'shopping', label: 'Shopping', icon: ShoppingBag, color: '#5B6FD4' },
  { id: 'fun', label: 'Fun', icon: PartyPopper, color: '#B0659E' },
  { id: 'other', label: 'Other', icon: Receipt, color: '#6E766F' },
]
export const category = (id?: string) => CATEGORIES.find((c) => c.id === id) ?? CATEGORIES[CATEGORIES.length - 1]

export function CategoryBadge({ id, size = 44, settlement }: { id?: string; size?: number; settlement?: boolean }) {
  const c = settlement ? { icon: CheckCircle2, color: '#2F7A1F', label: 'Settlement' } : category(id)
  return (
    <div className="grid shrink-0 place-items-center rounded-[14px]" style={{ width: size, height: size, background: `${c.color}22` }} title={c.label}>
      <c.icon size={size * 0.48} color={c.color} />
    </div>
  )
}

export function Amount({ value, currency, className, decimals = true }: { value: number; currency: string; className?: string; decimals?: boolean }) {
  return <span className={cn('tabular-nums', className)}>{formatMoney(value, currency, decimals)}</span>
}

// ------------------------------------------------------------------ Sheet / modal

export function Sheet({ open, onClose, title, children }: { open: boolean; onClose: () => void; title?: string; children: ReactNode }) {
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    document.addEventListener('keydown', onKey)
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = ''
    }
  }, [open, onClose])
  if (!open) return null
  return createPortal(
    <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center" role="dialog" aria-modal="true">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-[2px]" onClick={onClose} />
      <div className="relative max-h-[92vh] w-full overflow-y-auto rounded-t-[28px] bg-bg p-5 pb-8 shadow-lift animate-rise sm:max-w-md sm:rounded-[28px]">
        <div className="mx-auto mb-3 h-1.5 w-10 rounded-full bg-line sm:hidden" />
        {title && (
          <div className="mb-4 flex items-center justify-between">
            <h3 className="text-xl font-extrabold tracking-tight">{title}</h3>
            <button onClick={onClose} aria-label="Close" className="grid h-9 w-9 place-items-center rounded-full hover:bg-cardalt">
              <X size={18} />
            </button>
          </div>
        )}
        {children}
      </div>
    </div>,
    document.body,
  )
}
