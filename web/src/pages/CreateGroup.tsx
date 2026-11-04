import { zodResolver } from '@hookform/resolvers/zod'
import { ArrowLeft, ImagePlus, Plus, X } from 'lucide-react'
import { useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { z } from 'zod'
import { Avatar, Button, Card, Chip, Field, HillsArt, IconButton, Input } from '@/components/ui'
import { createGroup } from '@/lib/data'
import { CURRENCIES, symbol } from '@/lib/money'
import { useProfile } from '@/lib/profile'
import { compressImage, today } from '@/lib/utils'

const schema = z
  .object({
    name: z.string().trim().min(1, 'Give it a name').max(60),
    destination: z.string().trim().max(80),
    startDate: z.string(),
    endDate: z.string(),
    currency: z.string(),
  })
  .refine((v) => !v.startDate || !v.endDate || v.endDate >= v.startDate, { path: ['endDate'], message: 'End date is before the start date' })

type Form = z.infer<typeof schema>

export function CreateGroupPage() {
  const { type } = useParams()
  const isTrip = type !== 'outing'
  const nav = useNavigate()
  const profile = useProfile()
  const [cover, setCover] = useState<string | null>(null)
  const [members, setMembers] = useState<string[]>([])
  const [memberInput, setMemberInput] = useState('')
  const [saving, setSaving] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)

  const { register, handleSubmit, watch, setValue, formState } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: { name: '', destination: '', startDate: isTrip ? '' : today(), endDate: '', currency: profile.currency },
  })
  const currency = watch('currency')
  const name = watch('name')

  const addMember = () => {
    const n = memberInput.trim()
    if (!n) return
    if (n.toLowerCase() === profile.name.toLowerCase() || members.some((m) => m.toLowerCase() === n.toLowerCase())) {
      toast.error(`${n} is already in the group`)
    } else if (n.length > 40) {
      toast.error('Name is too long')
    } else setMembers([...members, n])
    setMemberInput('')
  }

  const submit = handleSubmit(async (v) => {
    setSaving(true)
    try {
      const all = memberInput.trim() ? [...members, memberInput.trim()] : members
      const id = await createGroup({ ...v, type: isTrip ? 'trip' : 'outing', cover, members: all, endDate: isTrip ? v.endDate : null })
      toast.success(isTrip ? 'Trip created' : 'Outing started')
      nav(`/g/${id}`, { replace: true })
    } catch (e) {
      toast.error((e as Error).message)
      setSaving(false)
    }
  })

  return (
    <form onSubmit={submit} className="pb-28">
      <div className="relative h-56 cursor-pointer overflow-hidden lg:mx-8 lg:mt-8 lg:rounded-[28px]" onClick={() => fileRef.current?.click()}>
        {cover ? <img src={cover} alt="" className="absolute inset-0 h-full w-full object-cover" /> : <HillsArt seed={name || type || 'x'} outing={!isTrip} className="absolute inset-0 h-full w-full" />}
        <div className="absolute inset-x-0 top-0 flex p-4 pt-safe" onClick={(e) => e.stopPropagation()}>
          <IconButton icon={ArrowLeft} label="Back" dark onClick={() => nav(-1)} />
        </div>
        <span className="absolute bottom-4 right-4 inline-flex items-center gap-1.5 rounded-full bg-black/55 px-3 py-2 text-xs font-bold text-white backdrop-blur">
          <ImagePlus size={15} /> {cover ? 'Change photo' : 'Add cover photo'}
        </span>
        <input ref={fileRef} type="file" accept="image/*" hidden onChange={async (e) => {
          const f = e.target.files?.[0]
          if (f) setCover(await compressImage(f, 1400).catch(() => null))
        }} />
      </div>

      <div className="space-y-5 px-5 pt-5 lg:px-8">
        <h1 className="text-3xl font-extrabold tracking-tight">{isTrip ? 'Create Trip' : 'Start Outing'}</h1>

        <Field label={isTrip ? 'Trip name' : 'Outing name'} error={formState.errors.name?.message}>
          <Input {...register('name')} placeholder={isTrip ? 'Darjeeling Trip' : 'Café Weekend'} autoFocus />
        </Field>

        {isTrip && (
          <Field label="Destination">
            <Input {...register('destination')} placeholder="Darjeeling, West Bengal" />
          </Field>
        )}

        <div className="grid grid-cols-2 gap-3">
          <Field label={isTrip ? 'Start date' : 'Date'}>
            <Input type="date" {...register('startDate')} />
          </Field>
          {isTrip && (
            <Field label="End date" error={formState.errors.endDate?.message}>
              <Input type="date" {...register('endDate')} />
            </Field>
          )}
        </div>

        <Field label="Currency">
          <div className="flex flex-wrap gap-2">
            {CURRENCIES.map((c) => (
              <Chip key={c} active={c === currency} onClick={() => setValue('currency', c)}>
                {symbol(c).trim()} {c}
              </Chip>
            ))}
          </div>
        </Field>

        <Card className="space-y-3">
          <div>
            <h2 className="font-bold">Members</h2>
            <p className="text-xs text-muted">Add friends by name. When they join with the invite code, they pick their name to link it.</p>
          </div>
          <div className="flex items-center gap-3 py-1">
            <Avatar name={profile.name} size={34} />
            <span className="flex-1 font-semibold">{profile.name} <span className="text-muted">(you)</span></span>
          </div>
          {members.map((m) => (
            <div key={m} className="flex items-center gap-3 py-1">
              <Avatar name={m} size={34} />
              <span className="flex-1 font-semibold">{m}</span>
              <button type="button" aria-label={`Remove ${m}`} onClick={() => setMembers(members.filter((x) => x !== m))} className="grid h-8 w-8 place-items-center rounded-full text-muted hover:bg-cardalt">
                <X size={16} />
              </button>
            </div>
          ))}
          <div className="flex gap-2">
            <Input
              value={memberInput}
              onChange={(e) => setMemberInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault()
                  addMember()
                }
              }}
              placeholder="Friend's name"
              className="flex-1"
            />
            <Button type="button" icon={Plus} onClick={addMember} aria-label="Add member" className="w-[52px] !px-0" />
          </div>
        </Card>
      </div>

      <div className="fixed inset-x-0 bottom-0 z-20 bg-gradient-to-t from-bg via-bg to-transparent px-5 pb-safe pt-6 lg:static lg:mt-6 lg:bg-none lg:px-8">
        <Button type="submit" size="lg" className="mb-4 w-full" disabled={saving}>
          {isTrip ? 'Create Trip' : 'Start Outing'}
        </Button>
      </div>
    </form>
  )
}
