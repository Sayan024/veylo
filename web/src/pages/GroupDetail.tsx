import { ArrowLeft, ArrowRight, Check, CheckCheck, Copy, ImagePlus, LogOut, MoreVertical, Palette, Plus, QrCode, ReceiptText, Share2, Undo2, UserPlus, Wallet } from 'lucide-react'
import QRCode from 'qrcode'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts'
import { toast } from 'sonner'
import { ExpenseRow } from '@/components/rows'
import { Amount, Avatar, Button, CATEGORIES, CategoryBadge, Chip, Cover, EmptyState, Field, IconButton, Input, PageSkeleton, Segmented, Sheet, SyncBadge, ThemeArt, ThemeChip } from '@/components/ui'
import { THEMES, themeInfo, themeOf } from '@/lib/theme'
import type { PayMethod, PayStatus } from '@/lib/types'
import { cloudEnabled } from '@/lib/cloud'
import { addMember, confirmSettlement, leaveGroup, recordSettlement, undoSettlement, updateGroupLocal } from '@/lib/data'
import { useAppData, useOnline, type GroupSummary } from '@/lib/hooks'
import { formatMoney, parseMoney, plainMoney } from '@/lib/money'
import type { Transfer } from '@/lib/settle'
import { cn, compressImage, fmtDate, upiLink } from '@/lib/utils'

type Tab = 'expenses' | 'balances' | 'settle'

export function GroupDetailPage() {
  const { id = '' } = useParams()
  const d = useAppData()
  const nav = useNavigate()
  const [params, setParams] = useSearchParams()
  const tab = (params.get('tab') as Tab) || 'expenses'
  const [menu, setMenu] = useState(false)
  const [invite, setInvite] = useState(false)
  const [adding, setAdding] = useState(false)
  const [leaving, setLeaving] = useState(false)
  const [settle, setSettle] = useState<Transfer | null>(null)
  const [styling, setStyling] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)

  if (!d) return <PageSkeleton />
  const g = d.group(id)
  if (!g) {
    return (
      <EmptyState icon={Wallet} title="Group not found" body="It may have been removed from this device.">
        <Button onClick={() => nav('/groups')}>Back to groups</Button>
      </EmptyState>
    )
  }

  const subtitle = [
    `${g.members.length} members`,
    g.group.destination,
    g.group.startDate && (g.group.endDate ? `${fmtDate(g.group.startDate)} – ${fmtDate(g.group.endDate)}` : fmtDate(g.group.startDate)),
  ].filter(Boolean).join(' · ')

  return (
    <div className="pb-28">
      <div className="relative h-[300px] overflow-hidden lg:mx-8 lg:mt-8 lg:h-[260px] lg:rounded-[28px]">
        <Cover group={g.group} className="absolute inset-0 h-full w-full" />
        <div className="absolute inset-0 bg-gradient-to-b from-black/35 via-transparent to-forest-deep/85" />
        <div className="absolute inset-x-0 top-0 flex items-center gap-2 p-4 pt-safe">
          <IconButton icon={ArrowLeft} label="Back" dark onClick={() => nav(-1)} />
          <div className="flex-1" />
          <IconButton icon={QrCode} label="Invite" dark onClick={() => setInvite(true)} />
          <IconButton icon={MoreVertical} label="More" dark onClick={() => setMenu(true)} />
        </div>
        <div className="absolute inset-x-0 bottom-0 p-5 pb-9 text-white lg:pb-6">
          <div className="mb-2"><ThemeChip theme={themeOf(g.group)} onClick={() => setStyling(true)} /></div>
          <h1 className="text-3xl font-extrabold tracking-tight">{g.group.name}</h1>
          <p className="mt-1 text-xs text-white/85">{subtitle}</p>
          <div className="mt-3 flex items-center">
            <div className="flex -space-x-2">
              {g.members.slice(0, 7).map((m) => <Avatar key={m.id} name={m.name} size={36} className="ring-2 ring-white" />)}
            </div>
            <button onClick={() => setAdding(true)} aria-label="Add member" className="-ml-2 grid h-9 w-9 place-items-center rounded-full bg-white text-forest-deep ring-2 ring-white">
              <Plus size={18} />
            </button>
            <span className="ml-auto">{g.group.shared ? <SyncBadge status={g.unsynced ? 'pending' : 'synced'} /> : <SyncBadge status={cloudEnabled ? 'pending' : 'local'} />}</span>
          </div>
        </div>
        <div className="absolute inset-x-0 bottom-0 h-6 rounded-t-[28px] bg-bg lg:hidden" />
      </div>

      <div className="space-y-4 px-5 pt-2 lg:px-8 lg:pt-5">
        <Segmented<Tab>
          options={[{ id: 'expenses', label: 'Expenses' }, { id: 'balances', label: 'Balances' }, { id: 'settle', label: 'Settle Up' }]}
          value={tab}
          onChange={(t) => setParams({ tab: t }, { replace: true })}
        />
        {tab === 'expenses' && <ExpensesTab g={g} />}
        {tab === 'balances' && <BalancesTab g={g} />}
        {tab === 'settle' && <SettleTab g={g} onSettle={setSettle} />}
      </div>

      <div className="fixed inset-x-0 bottom-0 z-20 bg-gradient-to-t from-bg via-bg to-transparent px-5 pb-safe pt-6 lg:left-64">
        <div className="mx-auto max-w-3xl lg:px-3">
          <Button size="lg" icon={Plus} className="mb-4 w-full" onClick={() => nav(`/add?group=${g.group.id}`)}>Add Expense</Button>
        </div>
      </div>

      <input ref={fileRef} type="file" accept="image/*" hidden onChange={async (e) => {
        const f = e.target.files?.[0]
        if (f) await updateGroupLocal(g.group.id, { cover: await compressImage(f, 1400) })
      }} />

      <Sheet open={menu} onClose={() => setMenu(false)} title={g.group.name}>
        <div className="space-y-2">
          {[
            { icon: QrCode, label: 'Invite members', run: () => setInvite(true) },
            { icon: UserPlus, label: 'Add member', run: () => setAdding(true) },
            { icon: ImagePlus, label: 'Change cover photo', run: () => fileRef.current?.click() },
            { icon: Palette, label: 'Change trip style', run: () => setStyling(true) },
            { icon: CheckCheck, label: g.group.archived ? 'Mark as active' : 'Mark as completed', run: () => updateGroupLocal(g.group.id, { archived: !g.group.archived }) },
            { icon: LogOut, label: 'Leave group on this device', run: () => setLeaving(true), danger: true },
          ].map((a) => (
            <button key={a.label} onClick={() => { setMenu(false); a.run() }} className={cn('flex w-full items-center gap-3 rounded-2xl bg-card p-4 text-left font-semibold hover:bg-cardalt', a.danger && 'text-owe')}>
              <a.icon size={19} /> {a.label}
            </button>
          ))}
        </div>
      </Sheet>

      <InviteSheet g={g} open={invite} onClose={() => setInvite(false)} />
      <AddMemberSheet open={adding} onClose={() => setAdding(false)} g={g} />

      <Sheet open={leaving} onClose={() => setLeaving(false)} title={`Leave ${g.group.name}?`}>
        <p className="text-sm text-muted">This removes the group and its expenses from this device. Other members keep their copies{g.group.shared ? ', and you can rejoin with the invite code' : ''}.</p>
        <div className="mt-5 grid grid-cols-2 gap-2">
          <Button variant="soft" onClick={() => setLeaving(false)}>Cancel</Button>
          <Button variant="danger" onClick={async () => { await leaveGroup(g.group.id); nav('/groups', { replace: true }) }}>Leave</Button>
        </div>
      </Sheet>

      <SettleSheet g={g} t={settle} onClose={() => setSettle(null)} />

      <Sheet open={styling} onClose={() => setStyling(false)} title="Trip style">
        <div className="space-y-2">
          {THEMES.map((t) => (
            <button
              key={t.id}
              onClick={() => { void updateGroupLocal(g.group.id, { theme: t.id }); setStyling(false) }}
              className="flex w-full items-center gap-3 rounded-2xl bg-card p-3 text-left hover:bg-cardalt"
              style={t.id === themeOf(g.group) ? { outline: `2px solid ${t.accent}` } : undefined}
            >
              <ThemeArt theme={t.id} seed={g.group.id} className="h-10 w-16 shrink-0 overflow-hidden rounded-lg" />
              <span className="font-semibold">{t.emoji} {t.label}</span>
            </button>
          ))}
        </div>
      </Sheet>
    </div>
  )
}

// ------------------------------------------------------------------ Expenses

function ExpensesTab({ g }: { g: GroupSummary }) {
  const nav = useNavigate()
  const byCategory = useMemo(() => {
    const m = new Map<string, number>()
    g.expenses.forEach((e) => m.set(e.category, (m.get(e.category) ?? 0) + e.amount))
    return CATEGORIES.filter((c) => m.has(c.id)).map((c) => ({ ...c, value: m.get(c.id)! }))
  }, [g.expenses])

  return (
    <>
      <div className="flex items-center gap-4 rounded-[22px] p-5" style={{ background: `${themeInfo(themeOf(g.group)).accent}1F` }}>
        <div className="flex-1">
          <p className="text-xs font-semibold text-muted">Total Spent</p>
          <Amount value={g.totalSpent} currency={g.group.currency} className="text-3xl font-extrabold tracking-tight" />
          {g.me && <p className="text-xs text-muted">Your share {formatMoney(g.myShare, g.group.currency)}</p>}
        </div>
        {byCategory.length > 0 ? (
          <div className="h-[88px] w-[88px]">
            <ResponsiveContainer>
              <PieChart>
                <Pie data={byCategory} dataKey="value" nameKey="label" innerRadius={26} outerRadius={42} paddingAngle={3} stroke="none">
                  {byCategory.map((c) => <Cell key={c.id} fill={c.color} />)}
                </Pie>
                <Tooltip formatter={(v: number) => formatMoney(v, g.group.currency)} contentStyle={{ borderRadius: 12, border: 'none', fontFamily: 'Manrope' }} />
              </PieChart>
            </ResponsiveContainer>
          </div>
        ) : (
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-lime text-forest-deep"><ReceiptText size={22} /></span>
        )}
      </div>

      {byCategory.length > 0 && (
        <div className="no-scrollbar -mx-5 flex gap-2 overflow-x-auto px-5 lg:mx-0 lg:px-0">
          {byCategory.map((c) => (
            <span key={c.id} className="inline-flex shrink-0 items-center gap-1.5 rounded-full bg-card px-3 py-1.5 text-xs font-semibold">
              <span className="h-2.5 w-2.5 rounded-full" style={{ background: c.color }} /> {c.label} {formatMoney(c.value, g.group.currency, false)}
            </span>
          ))}
        </div>
      )}

      {g.expenses.length === 0 ? (
        <EmptyState icon={ReceiptText} title="No expenses yet" body="Add the first bill — hotel, cab, chai, anything. VEYLO does the math." />
      ) : (
        <div className="space-y-2.5">
          {g.expenses.map((e) => <ExpenseRow key={e.id} e={e} g={g} onClick={() => nav(`/e/${e.id}`)} />)}
        </div>
      )}
    </>
  )
}

// ------------------------------------------------------------------ Balances

function BalancesTab({ g }: { g: GroupSummary }) {
  const c = g.group.currency
  return (
    <>
      <div className="space-y-2.5">
        {g.members.map((m) => {
          const t = g.totals[m.id] ?? { paid: 0, share: 0, net: 0 }
          return (
            <div key={m.id} className="rounded-[20px] bg-card p-4">
              <div className="flex items-center gap-3">
                <Avatar name={m.name} size={42} />
                <div className="min-w-0 flex-1">
                  <p className="truncate font-bold">{m.name}{m.id === g.me?.id && <span className="text-muted"> (you)</span>}</p>
                  <p className="text-xs text-muted">{m.deviceId ? 'Joined' : 'Not joined yet'}</p>
                </div>
                <div className="text-right">
                  <p className="text-[11px] text-muted">{t.net > 0 ? 'gets back' : t.net < 0 ? 'owes' : 'settled'}</p>
                  <p className={cn('font-extrabold tabular-nums', t.net > 0 ? 'text-owed' : t.net < 0 ? 'text-owe' : 'text-muted')}>{formatMoney(Math.abs(t.net), c)}</p>
                </div>
              </div>
              <div className="mt-3 grid grid-cols-2 gap-2 text-xs">
                <div className="rounded-xl bg-cardalt px-3 py-2"><span className="text-muted">Paid </span><b className="tabular-nums">{formatMoney(t.paid, c)}</b></div>
                <div className="rounded-xl bg-cardalt px-3 py-2"><span className="text-muted">Share </span><b className="tabular-nums">{formatMoney(t.share, c)}</b></div>
              </div>
            </div>
          )
        })}
      </div>
      {g.transfers.length > 0 && (
        <div className="rounded-[20px] bg-card p-4">
          <p className="mb-2 font-bold">Who owes whom</p>
          {g.transfers.map((t) => (
            <div key={`${t.fromId}-${t.toId}`} className="flex items-center gap-2 py-1.5 text-sm">
              <b>{g.name(t.fromId)}</b> <ArrowRight size={14} className="text-muted" /> <b>{g.name(t.toId)}</b>
              <span className="ml-auto font-bold tabular-nums">{formatMoney(t.amount, c)}</span>
            </div>
          ))}
        </div>
      )}
    </>
  )
}

// ------------------------------------------------------------------ Settle up

function SettleTab({ g, onSettle }: { g: GroupSummary; onSettle: (t: Transfer) => void }) {
  const c = g.group.currency
  const n = g.transfers.length
  return (
    <>
      <div className="flex flex-col items-center rounded-[22px] bg-lime-tint p-6 text-center">
        <span className="grid h-11 w-11 place-items-center rounded-full bg-lime text-forest-deep"><Check size={22} strokeWidth={3} /></span>
        <h3 className="mt-3 text-xl font-extrabold">Minimum Settlements</h3>
        <p className="text-sm text-muted">{n === 0 ? 'Everyone is settled up 🎉' : `Clear all balances with just ${n} payment${n === 1 ? '' : 's'}`}</p>
      </div>

      {g.transfers.map((t) => {
        const payee = g.member(t.toId)
        const canUpi = t.fromId === g.me?.id && !!payee?.upiId && c === 'INR'
        return (
          <div key={`${t.fromId}-${t.toId}`} className="rounded-[22px] bg-card p-4">
            <div className="flex items-center gap-3">
              <Avatar name={g.member(t.fromId)?.name ?? '?'} size={42} />
              <div className="flex-1">
                <p className="flex items-center gap-1.5 text-sm font-bold">{g.name(t.fromId)} <ArrowRight size={14} className="text-muted" /> {g.name(t.toId)}</p>
                <Amount value={t.amount} currency={c} className="text-2xl font-extrabold" />
              </div>
            </div>
            <div className="mt-3 flex gap-2">
              {canUpi && (
                <a
                  href={upiLink({ upiId: payee!.upiId!, name: payee!.name, amount: t.amount, note: `VEYLO ${g.group.name}`, ref: `VEYLO${Date.now()}` })}
                  className="inline-flex h-11 flex-1 items-center justify-center gap-2 rounded-[16px] bg-cardalt text-sm font-bold hover:bg-line"
                >
                  <Wallet size={16} /> Pay via UPI
                </a>
              )}
              <Button size="sm" variant="forest" icon={Check} className="h-11 flex-1" onClick={() => onSettle(t)}>Mark as Settled</Button>
            </div>
            {t.fromId === g.me?.id && !payee?.upiId && c === 'INR' && <p className="mt-2 text-xs text-muted">{payee?.name} hasn't added a UPI ID yet.</p>}
          </div>
        )
      })}

      {g.settlements.length > 0 && (
        <>
          <h3 className="pt-2 font-bold">Settlement history</h3>
          {g.settlements.map((s) => (
            <div key={s.id} className="flex items-center gap-3 rounded-[18px] bg-card p-3">
              <CategoryBadge settlement size={38} />
              <div className="flex-1">
                <p className="text-sm font-bold">{g.name(s.fromId)} paid {g.name(s.toId)}</p>
                <p className="text-xs text-muted">
                  {s.method === 'upi' ? 'UPI' : s.method === 'other' ? 'Other' : 'Cash'}
                  {s.txnId && <> · Txn …{s.txnId.slice(-6)}</>}
                </p>
                {s.payStatus === 'pending' && (
                  <p className="mt-1 flex items-center gap-2">
                    <span className="rounded-full bg-warn/15 px-2 py-0.5 text-[11px] font-bold text-warn">Pending</span>
                    <button onClick={() => confirmSettlement(s.id).then(() => toast.success('Marked as received'))} className="text-xs font-bold text-forest underline-offset-2 hover:underline dark:text-lime">Mark received</button>
                  </p>
                )}
                <p className="flex items-center gap-1.5 text-xs text-muted">{fmtDate(s.createdAt)} <SyncBadge status={s.syncStatus} compact /></p>
              </div>
              <Amount value={s.amount} currency={c} className="text-sm font-bold text-owed" />
              <button aria-label="Undo settlement" title="Undo" onClick={() => undoSettlement(s.id).then(() => toast('Settlement undone'))} className="grid h-8 w-8 place-items-center rounded-full text-muted hover:bg-cardalt">
                <Undo2 size={16} />
              </button>
            </div>
          ))}
        </>
      )}
    </>
  )
}

function SettleSheet({ g, t, onClose }: { g: GroupSummary; t: Transfer | null; onClose: () => void }) {
  const [text, setText] = useState('')
  const [method, setMethod] = useState<PayMethod>('cash')
  const [txnId, setTxnId] = useState('')
  const [status, setStatus] = useState<PayStatus>('success')
  useEffect(() => {
    setText(t ? plainMoney(t.amount) : '')
    setMethod('cash'); setTxnId(''); setStatus('success')
  }, [t])
  const amount = parseMoney(text)
  const tooMuch = !!t && amount != null && amount > t.amount
  return (
    <Sheet open={!!t} onClose={onClose} title="Record payment">
      {t && (
        <div className="space-y-4">
          <p className="font-semibold">{g.name(t.fromId)} → {g.name(t.toId)}</p>
          <Field label="Paid with">
            <div className="flex gap-2">
              {(['upi', 'cash', 'other'] as PayMethod[]).map((m) => (
                <Chip key={m} active={method === m} onClick={() => setMethod(m)}>{m === 'upi' ? 'UPI' : m === 'cash' ? 'Cash' : 'Other'}</Chip>
              ))}
            </div>
          </Field>
          <Field label="Amount" error={tooMuch ? `More than the ${formatMoney(t.amount, g.group.currency)} owed` : undefined}>
            <Input inputMode="decimal" prefix={formatMoney(0, g.group.currency).replace(/[\d.,]/g, '')} value={text} onChange={(e) => setText(e.target.value)} />
          </Field>
          {method === 'upi' && (
            <>
              <Field label="UPI transaction ID / UTR (optional)" hint="Find it in your UPI app's payment details (usually 12 digits).">
                <Input value={txnId} onChange={(e) => setTxnId(e.target.value.replace(/[^A-Za-z0-9]/g, '').slice(0, 35))} placeholder="431298765432" />
              </Field>
              <Field label="Status" hint={status === 'pending' ? "Pending payments don't change balances until someone taps Mark received." : undefined}>
                <div className="flex gap-2">
                  <Chip active={status === 'success'} onClick={() => setStatus('success')}>✓ Paid</Chip>
                  <Chip active={status === 'pending'} onClick={() => setStatus('pending')}>⏳ Pending</Chip>
                </div>
              </Field>
            </>
          )}
          <Button className="w-full" icon={Check} disabled={!amount || amount <= 0 || tooMuch} onClick={async () => {
            await recordSettlement(g.group.id, t.fromId, t.toId, amount!, method === 'upi' ? { method, txnId, payStatus: status } : { method })
            toast.success(status === 'pending' && method === 'upi' ? 'Saved as pending' : 'Marked as settled')
            onClose()
          }}>{status === 'pending' && method === 'upi' ? 'Save as pending' : 'Mark as settled'}</Button>
        </div>
      )}
    </Sheet>
  )
}

// ------------------------------------------------------------------ Invite & members

function InviteSheet({ g, open, onClose }: { g: GroupSummary; open: boolean; onClose: () => void }) {
  const online = useOnline()
  const [qr, setQr] = useState('')
  const link = g.group.inviteCode ? `${location.origin}/join?code=${g.group.inviteCode}` : ''
  useEffect(() => {
    if (link) QRCode.toDataURL(link, { margin: 1, width: 480, color: { dark: '#0F2A1D', light: '#FFFFFF' } }).then(setQr)
  }, [link])

  return (
    <Sheet open={open} onClose={onClose} title="Invite members">
      {!cloudEnabled ? (
        <p className="text-sm text-muted">This copy of VEYLO runs in local mode (no cloud keys configured), so groups can't be shared yet. See the README to connect a free Supabase project.</p>
      ) : !g.group.inviteCode ? (
        <p className="text-sm text-muted">{online ? 'Uploading the group… the invite code appears in a moment.' : "You're offline. The group uploads and gets an invite code as soon as you're back online."}</p>
      ) : (
        <div className="flex flex-col items-center text-center">
          {qr && <img src={qr} alt="Invite QR code" className="h-52 w-52 rounded-[22px] bg-white p-3" />}
          <p className="mt-4 text-xs font-semibold text-muted">Invite code</p>
          <p className="text-4xl font-extrabold tracking-[0.3em]">{g.group.inviteCode}</p>
          <p className="mt-2 text-xs text-muted">Friends scan the QR, open the link, or enter the code under Join Group.</p>
          <div className="mt-5 grid w-full grid-cols-2 gap-2">
            <Button variant="soft" icon={Copy} onClick={() => navigator.clipboard.writeText(link).then(() => toast.success('Link copied'))}>Copy link</Button>
            <Button icon={Share2} onClick={() => {
              if (navigator.share) navigator.share({ title: `Join ${g.group.name} on VEYLO`, text: `Join "${g.group.name}" on VEYLO — code ${g.group.inviteCode}`, url: link }).catch(() => undefined)
              else navigator.clipboard.writeText(link).then(() => toast.success('Link copied'))
            }}>Share</Button>
          </div>
        </div>
      )}
    </Sheet>
  )
}

function AddMemberSheet({ g, open, onClose }: { g: GroupSummary; open: boolean; onClose: () => void }) {
  const [name, setName] = useState('')
  const dup = g.members.some((m) => m.name.toLowerCase() === name.trim().toLowerCase())
  return (
    <Sheet open={open} onClose={onClose} title="Add member">
      <form className="space-y-4" onSubmit={async (e) => {
        e.preventDefault()
        if (!name.trim() || dup) return
        await addMember(g.group.id, name)
        toast.success(`${name.trim()} added`)
        setName('')
        onClose()
      }}>
        <Field label="Name" error={dup ? 'Someone with that name is already here' : undefined} hint="They can claim this name when they join with the invite code.">
          <Input value={name} onChange={(e) => setName(e.target.value.slice(0, 40))} placeholder="Priya" autoFocus />
        </Field>
        <Button type="submit" className="w-full" disabled={!name.trim() || dup}>Add</Button>
      </form>
    </Sheet>
  )
}
