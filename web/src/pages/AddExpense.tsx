import { ArrowLeft, Camera, Check, Coffee, X } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { z } from 'zod'
import { Avatar, Button, CATEGORIES, Chip, EmptyState, Field, IconButton, Input, PageSkeleton, Segmented } from '@/components/ui'
import { saveExpense } from '@/lib/data'
import { useAppData } from '@/lib/hooks'
import { formatMoney, parseMoney, plainMoney, symbol } from '@/lib/money'
import { calculateSplit, SPLIT_METHODS, type SplitMethod } from '@/lib/split'
import { cn, compressImage, today } from '@/lib/utils'

const schema = z.object({
  title: z.string().trim().min(1, 'Add a title').max(80),
  amount: z.number().int().positive('Enter an amount greater than zero'),
  payerId: z.string().min(1, 'Choose who paid'),
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Pick a date'),
  notes: z.string().max(500),
})

export function AddExpensePage() {
  const { id: editId } = useParams()
  const [params] = useSearchParams()
  const dupId = params.get('duplicate')
  const sourceId = editId ?? dupId ?? undefined
  const d = useAppData()
  const nav = useNavigate()
  const fileRef = useRef<HTMLInputElement>(null)

  const [groupId, setGroupId] = useState<string | undefined>(params.get('group') ?? undefined)
  const [title, setTitle] = useState('')
  const [amountText, setAmountText] = useState('')
  const [payerId, setPayerId] = useState('')
  const [category, setCategory] = useState('food')
  const [date, setDate] = useState(today())
  const [notes, setNotes] = useState('')
  const [receipt, setReceipt] = useState<string | null>(null)
  const [method, setMethod] = useState<SplitMethod>(params.get('split') ? 'exact' : 'equal')
  const [excluded, setExcluded] = useState<Record<string, boolean>>({})
  const [values, setValues] = useState<Record<string, string>>({})
  const [touched, setTouched] = useState(false)
  const [saving, setSaving] = useState(false)
  const loaded = useRef(false)

  // Default group: the most recently created active one.
  useEffect(() => {
    if (d && !groupId && !sourceId) setGroupId(d.groups.find((g) => !g.group.archived)?.group.id ?? d.groups[0]?.group.id)
  }, [d, groupId, sourceId])

  // Prefill when editing or duplicating.
  useEffect(() => {
    if (!d || !sourceId || loaded.current) return
    const g = d.groups.find((s) => s.expenses.some((e) => e.id === sourceId))
    const e = g?.expenses.find((x) => x.id === sourceId)
    if (!g || !e) return
    loaded.current = true
    setGroupId(g.group.id)
    setTitle(dupId ? `${e.title} (copy)` : e.title)
    setAmountText(plainMoney(e.amount))
    setPayerId(e.payerId)
    setCategory(e.category)
    setDate(dupId ? today() : e.date)
    setNotes(e.notes)
    setReceipt(dupId ? null : e.receipt ?? null)
    setMethod(e.splitMethod)
    const inc = new Set(e.shares.map((s) => s.memberId))
    setExcluded(Object.fromEntries(g.members.filter((m) => !inc.has(m.id)).map((m) => [m.id, true])))
    setValues(Object.fromEntries(e.shares.map((s) => [s.memberId, e.splitMethod === 'exact' ? plainMoney(s.share) : e.splitMethod === 'equal' ? '' : String(s.input)])))
  }, [d, sourceId, dupId])

  const g = d?.group(groupId)
  useEffect(() => {
    if (g && !payerId) setPayerId(g.me?.id ?? g.members[0]?.id ?? '')
  }, [g, payerId])

  const amount = parseMoney(amountText) ?? 0
  const inputs = useMemo(() => (g?.members ?? []).map((m) => {
    const raw = values[m.id] ?? ''
    let v = 0
    if (method === 'exact') v = parseMoney(raw) ?? 0
    else if (method === 'shares') v = raw === '' ? 1 : Number(raw)
    else if (method === 'percent') v = raw === '' ? 0 : Number(raw)
    return { memberId: m.id, included: !excluded[m.id], value: Number.isFinite(v) ? v : -1 }
  }), [g, values, excluded, method])
  const split = calculateSplit(amount, method, inputs, g?.group.currency)

  if (!d) return <PageSkeleton />
  if (d.groups.length === 0) {
    return (
      <EmptyState icon={Coffee} title="Create a group first" body="Expenses live inside a trip or an outing.">
        <Button onClick={() => nav('/new/outing', { replace: true })}>Start Outing</Button>
        <Button variant="soft" onClick={() => nav(-1)}>Back</Button>
      </EmptyState>
    )
  }
  if (!g) return <PageSkeleton />

  const currency = g.group.currency
  const check = schema.safeParse({ title, amount, payerId, date, notes })
  const errors = touched && !check.success ? Object.fromEntries(check.error.issues.map((i) => [i.path[0], i.message])) : {}

  const submit = async () => {
    setTouched(true)
    if (!check.success || !split.ok) {
      toast.error(!check.success ? check.error.issues[0].message : split.ok ? '' : split.error)
      return
    }
    setSaving(true)
    const shares = inputs.filter((i) => i.included && split.shares[i.memberId] != null).map((i) => ({ memberId: i.memberId, share: split.shares[i.memberId], input: i.value }))
    try {
      const id = await saveExpense({
        id: editId, groupId: g.group.id, title, amount, currency, payerId, category, date, notes, receipt, splitMethod: method, shares,
      })
      toast.success(editId ? 'Expense updated' : 'Expense added')
      nav(editId ? `/e/${id}` : `/g/${g.group.id}`, { replace: true })
    } catch (err) {
      toast.error(`Couldn't save: ${(err as Error).message}`)
      setSaving(false)
    }
  }

  const placeholder = method === 'shares' ? '1' : method === 'percent' ? '%' : '0'

  return (
    <div className="pb-32">
      <header className="sticky top-0 z-20 flex items-center gap-3 bg-bg/90 px-4 py-3 pt-safe backdrop-blur lg:px-8 lg:pt-8">
        <IconButton icon={ArrowLeft} label="Back" onClick={() => nav(-1)} />
        <h1 className="flex-1 text-lg font-extrabold">{editId ? 'Edit Expense' : dupId ? 'Duplicate Expense' : params.get('split') ? 'Split Bill' : 'Add Expense'}</h1>
        <IconButton icon={Camera} label="Attach receipt" onClick={() => fileRef.current?.click()} />
        <input ref={fileRef} type="file" accept="image/*" capture="environment" hidden onChange={async (e) => {
          const f = e.target.files?.[0]
          if (f) setReceipt(await compressImage(f, 1600, 0.75).catch(() => null))
        }} />
      </header>

      <div className="space-y-5 px-5 lg:px-8">
        {!editId && d.groups.length > 1 && (
          <div className="no-scrollbar -mx-5 flex gap-2 overflow-x-auto px-5 lg:mx-0 lg:px-0">
            {d.groups.filter((s) => !s.group.archived || s.group.id === groupId).map((s) => (
              <Chip key={s.group.id} active={s.group.id === groupId} onClick={() => { setGroupId(s.group.id); setPayerId(''); setExcluded({}); setValues({}) }}>
                {s.group.name}
              </Chip>
            ))}
          </div>
        )}

        <div className="flex flex-col items-center py-2">
          <span className="text-xs font-semibold text-muted">Amount · {currency}</span>
          <div className="flex items-baseline">
            <span className="text-4xl font-extrabold text-muted">{symbol(currency).trim()}</span>
            <input
              inputMode="decimal"
              value={amountText}
              onChange={(e) => /^[\d,]*\.?\d{0,2}$/.test(e.target.value) && e.target.value.length <= 12 && setAmountText(e.target.value)}
              placeholder="0"
              aria-label="Amount"
              className="w-[220px] bg-transparent text-center text-5xl font-extrabold tracking-tight outline-none placeholder:text-line"
            />
          </div>
          {errors.amount && <p className="text-xs font-semibold text-owe">{errors.amount}</p>}
        </div>

        <Field label="Title" error={errors.title}>
          <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Café bill" />
        </Field>

        <div className="no-scrollbar -mx-5 flex gap-2 overflow-x-auto px-5 lg:mx-0 lg:flex-wrap lg:px-0">
          {CATEGORIES.map((c) => (
            <button key={c.id} type="button" onClick={() => setCategory(c.id)} className={cn('inline-flex shrink-0 items-center gap-1.5 rounded-full px-3.5 py-2 text-sm font-semibold transition', c.id === category ? '' : 'bg-card')} style={c.id === category ? { background: `${c.color}26`, color: c.color } : undefined}>
              <c.icon size={15} color={c.color} /> {c.label}
            </button>
          ))}
        </div>

        <div className="grid grid-cols-2 gap-3">
          <Field label="Paid by" error={errors.payerId}>
            <select value={payerId} onChange={(e) => setPayerId(e.target.value)} className="h-[52px] w-full rounded-2xl border border-line bg-card px-4 text-[15px] font-semibold outline-none">
              {g.members.map((m) => <option key={m.id} value={m.id}>{g.name(m.id)}</option>)}
            </select>
          </Field>
          <Field label="Date" error={errors.date}>
            <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          </Field>
        </div>

        <section className="rounded-[22px] bg-card p-4">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="font-bold">Split between</h2>
            <span className="text-xs text-muted">{inputs.filter((i) => i.included).length} of {g.members.length}</span>
          </div>
          <Segmented<SplitMethod> options={SPLIT_METHODS} value={method} onChange={(m) => { setMethod(m); setValues({}) }} className="mb-3" />
          <div className="divide-y divide-line">
            {g.members.map((m) => {
              const inc = !excluded[m.id]
              const share = split.ok ? split.shares[m.id] : undefined
              return (
                <div key={m.id} className="flex items-center gap-3 py-2.5">
                  <Avatar name={m.name} size={36} className={cn(!inc && 'opacity-40')} />
                  <div className="min-w-0 flex-1">
                    <p className={cn('truncate font-semibold', !inc && 'text-muted line-through')}>{g.name(m.id)}</p>
                    {inc && share != null && <p className="text-xs text-muted tabular-nums">{formatMoney(share, currency)}</p>}
                  </div>
                  {inc && method !== 'equal' && (
                    <input
                      inputMode="decimal"
                      value={values[m.id] ?? ''}
                      onChange={(e) => /^\d*\.?\d{0,6}$/.test(e.target.value) && setValues({ ...values, [m.id]: e.target.value })}
                      placeholder={placeholder}
                      aria-label={`${m.name} ${method}`}
                      className="h-10 w-20 rounded-xl bg-cardalt px-3 text-right font-semibold outline-none focus:ring-2 focus:ring-forest dark:focus:ring-lime"
                    />
                  )}
                  <button
                    type="button"
                    aria-label={inc ? `Exclude ${m.name}` : `Include ${m.name}`}
                    onClick={() => setExcluded({ ...excluded, [m.id]: inc })}
                    className={cn('grid h-7 w-7 place-items-center rounded-lg border-2 transition', inc ? 'border-forest bg-forest text-lime dark:border-lime dark:bg-lime dark:text-forest-deep' : 'border-line')}
                  >
                    {inc && <Check size={16} strokeWidth={3} />}
                  </button>
                </div>
              )
            })}
          </div>
          {amount > 0 && !split.ok && <p className="mt-3 rounded-xl bg-owe-bg px-3 py-2 text-xs font-semibold text-owe">{split.error}</p>}
        </section>

        <Field label="Notes (optional)">
          <textarea value={notes} onChange={(e) => setNotes(e.target.value.slice(0, 500))} rows={2} placeholder="Table for 4, incl. tip" className="w-full resize-none rounded-2xl border border-line bg-card p-4 text-[15px] font-medium outline-none focus:border-forest" />
        </Field>

        {receipt && (
          <div className="relative overflow-hidden rounded-[20px]">
            <img src={receipt} alt="Receipt" className="max-h-72 w-full object-cover" />
            <button aria-label="Remove receipt" onClick={() => setReceipt(null)} className="absolute right-2 top-2 grid h-9 w-9 place-items-center rounded-full bg-black/50 text-white"><X size={18} /></button>
          </div>
        )}
      </div>

      <div className="fixed inset-x-0 bottom-0 z-20 bg-gradient-to-t from-bg via-bg to-transparent px-5 pb-safe pt-6 lg:left-64">
        <div className="mx-auto max-w-3xl lg:px-3">
          <Button size="lg" className="mb-4 w-full" onClick={submit} disabled={saving}>{editId ? 'Save changes' : 'Add Expense'}</Button>
        </div>
      </div>
    </div>
  )
}
