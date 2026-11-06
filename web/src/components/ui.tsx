import {
  CheckCircle2, CircleAlert, Clock, Coffee, Car, Hotel, MonitorSmartphone, PartyPopper, Receipt, RefreshCw, ShoppingBag, UtensilsCrossed, X,
  type LucideIcon,
} from 'lucide-react'
import { forwardRef, useEffect, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { formatMoney } from '@/lib/money'
import type { Group, SyncStatus } from '@/lib/types'
import { themeInfo, themeOf, type TripTheme } from '@/lib/theme'
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

/** Cover illustration per trip style — same scenes as the Android app. */
export function ThemeArt({ theme, seed, className }: { theme: TripTheme; seed: string; className?: string }) {
  if (theme === 'hills') return <HillsArt seed={seed} className={className} />
  if (theme === 'cafe') return <HillsArt seed={seed} outing className={className} />
  const h = hash(seed)
  const W = 400, H = 240
  if (theme === 'beach') {
    const sunX = W * (0.28 + (h % 20) / 100)
    const wave = (y: number) => {
      let d = `M0 ${y}`
      for (let x = 0; x < W; x += W / 6) d += ` Q${x + W / 12} ${y - 6} ${x + W / 6} ${y}`
      return `${d} L${W} ${H} L0 ${H} Z`
    }
    return (
      <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="xMidYMid slice" className={className}>
        <defs><linearGradient id={`b${h}`} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#8ED8E8" /><stop offset="1" stopColor="#DDF4F2" /></linearGradient></defs>
        <rect width={W} height={H} fill={`url(#b${h})`} />
        <circle cx={sunX} cy={H * 0.3} r={H * 0.11} fill="#FFE08A" />
        <rect y={H * 0.52} width={W} height={H * 0.48} fill="#1BA8B5" />
        <path d={wave(H * 0.6)} fill="#34C3C9" />
        <path d={wave(H * 0.68)} fill="#7FDAD6" />
        <path d={`M0 ${H * 0.8} Q${W / 2} ${H * 0.7} ${W} ${H * 0.84} L${W} ${H} L0 ${H} Z`} fill="#F2D7A6" />
        <path d={`M${W * 0.8} ${H * 0.86} Q${W * 0.75} ${H * 0.55} ${W * 0.785} ${H * 0.3}`} stroke="#7A5230" strokeWidth={W * 0.022} fill="none" strokeLinecap="round" />
        {[-160, -120, -60, -20, 20].map((a) => {
          const r = (a * Math.PI) / 180, x0 = W * 0.785, y0 = H * 0.3, len = W * 0.13
          const x1 = x0 + len * Math.cos(r), y1 = y0 + len * Math.sin(r) + H * 0.06
          return <path key={a} d={`M${x0} ${y0} Q${(x0 + x1) / 2} ${y0 - H * 0.06} ${x1} ${y1}`} stroke="#2F8F4E" strokeWidth={W * 0.018} fill="none" strokeLinecap="round" />
        })}
      </svg>
    )
  }
  if (theme === 'business') {
    const ground = H * 0.92
    const blocks: JSX.Element[] = []
    let x = -W * 0.02
    for (let i = 0; x < W; i++) {
      const bw = W * (0.07 + ((h >> (i % 16)) & 7) / 100)
      const bh = H * (0.28 + ((h >> ((i * 3) % 18)) & 31) / 64)
      blocks.push(<rect key={`b${i}`} x={x} y={ground - bh} width={bw} height={bh} fill={i % 2 ? '#263763' : '#1E2B4D'} />)
      const rows = Math.max(1, Math.floor(bh / (H * 0.07)))
      for (let r = 0; r < rows; r++) for (let c = 0; c < 3; c++) {
        if (((h >> ((r + c + i) % 20)) & 3) === 0) continue
        blocks.push(<rect key={`w${i}-${r}-${c}`} x={x + bw * (0.15 + c * 0.27)} y={ground - bh + H * 0.03 + r * H * 0.07} width={bw * 0.16} height={H * 0.03} fill="#FFE9A8" opacity={0.85} />)
      }
      x += bw + W * 0.008
    }
    return (
      <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="xMidYMid slice" className={className}>
        <defs><linearGradient id={`c${h}`} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#2C3E6B" /><stop offset="0.55" stopColor="#6E7FB0" /><stop offset="1" stopColor="#C9CFE6" /></linearGradient></defs>
        <rect width={W} height={H} fill={`url(#c${h})`} />
        {blocks}
        <rect y={ground} width={W} height={H - ground} fill="#162040" />
      </svg>
    )
  }
  // vacation: sunset over water
  const horizon = H * 0.62, sunX = W * (0.45 + (h % 20) / 100)
  return (
    <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="xMidYMid slice" className={className}>
      <defs>
        <linearGradient id={`s${h}`} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#FF9A62" /><stop offset="0.5" stopColor="#FFC37A" /><stop offset="1" stopColor="#FFE3B0" /></linearGradient>
        <linearGradient id={`w${h}`} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#E5704A" /><stop offset="1" stopColor="#9C3F5C" /></linearGradient>
      </defs>
      <rect width={W} height={H} fill={`url(#s${h})`} />
      <circle cx={sunX} cy={horizon} r={H * 0.16} fill="#FFF1C9" />
      <rect y={horizon} width={W} height={H - horizon} fill={`url(#w${h})`} />
      {[0, 1, 2, 3, 4].map((k) => {
        const y = horizon + H * (0.05 + k * 0.06), half = W * (0.12 - k * 0.018)
        return <line key={k} x1={sunX - half} x2={sunX + half} y1={y} y2={y} stroke="#FFF1C9" strokeOpacity={0.6} strokeWidth={H * 0.012} strokeLinecap="round" />
      })}
      {[0, 1, 2].map((b) => {
        const bx = W * (0.18 + b * 0.09), by = H * (0.22 + (b % 2) * 0.05), s = W * 0.025
        return <path key={b} d={`M${bx - s} ${by} Q${bx - s / 2} ${by - s * 0.6} ${bx} ${by} Q${bx + s / 2} ${by - s * 0.6} ${bx + s} ${by}`} stroke="#5A2A3A" strokeOpacity={0.65} strokeWidth={W * 0.006} fill="none" strokeLinecap="round" />
      })}
    </svg>
  )
}

export function Cover({ group, className }: { group: Pick<Group, 'id' | 'cover' | 'type' | 'theme' | 'destination' | 'name'>; className?: string }) {
  return group.cover ? (
    <img src={group.cover} alt="" className={cn('object-cover', className)} />
  ) : (
    <ThemeArt theme={themeOf(group)} seed={group.id} className={className} />
  )
}

export function ThemeChip({ theme, onDark = true, onClick }: { theme: TripTheme; onDark?: boolean; onClick?: () => void }) {
  const t = themeInfo(theme)
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn('inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-bold', onDark ? 'bg-black/30 text-white backdrop-blur' : '')}
      style={onDark ? undefined : { background: `${t.accent}22`, color: t.accent }}
    >
      {t.emoji} {t.label}
    </button>
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
