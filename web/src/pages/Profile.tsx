import { zodResolver } from '@hookform/resolvers/zod'
import { Download, Trash2, Upload } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { SyncIndicator } from '@/components/Shell'
import { Avatar, Button, Card, Chip, Field, Input, Segmented, Sheet } from '@/components/ui'
import { cloudEnabled } from '@/lib/cloud'
import { deleteAllData, exportBackup, importBackup, syncMyProfileToGroups } from '@/lib/data'
import { useAppData } from '@/lib/hooks'
import { CURRENCIES, symbol } from '@/lib/money'
import { useProfile, type ThemeMode } from '@/lib/profile'
import { downloadFile, isValidUpi } from '@/lib/utils'

const schema = z.object({
  name: z.string().trim().min(1, 'Enter your name').max(30),
  upiId: z.string().trim().refine((v) => v === '' || isValidUpi(v), 'Looks like an invalid UPI ID (e.g. name@okaxis)'),
})

export function ProfilePage() {
  const p = useProfile()
  const d = useAppData()
  const [confirm, setConfirm] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)
  const { register, handleSubmit, formState, reset } = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { name: p.name, upiId: p.upiId },
  })
  useEffect(() => reset({ name: p.name, upiId: p.upiId }), [p.name, p.upiId, reset])

  const save = handleSubmit(async (v) => {
    p.update({ name: v.name, upiId: v.upiId })
    await syncMyProfileToGroups()
    toast.success('Profile saved')
  })

  return (
    <div className="space-y-5 px-5 pb-6 pt-6 pt-safe lg:px-8 lg:pt-10">
      <div className="flex flex-col items-center pt-4 text-center">
        <Avatar name={p.name || '?'} size={88} />
        <h1 className="mt-3 text-2xl font-extrabold">{p.name}</h1>
        <p className="text-xs text-muted">Device {p.deviceId.slice(0, 8).toUpperCase()}</p>
      </div>

      <div className="grid grid-cols-3 gap-3">
        {[
          ['Groups', d?.groups.length ?? 0],
          ['Expenses', d?.groups.reduce((s, g) => s + g.expenses.length, 0) ?? 0],
          ['Pending sync', d?.pendingCount ?? 0],
        ].map(([l, v]) => (
          <Card key={l} className="p-4">
            <p className="text-2xl font-extrabold">{v}</p>
            <p className="text-[11px] font-semibold text-muted">{l}</p>
          </Card>
        ))}
      </div>

      <Card>
        <form onSubmit={save} className="space-y-4">
          <h2 className="font-bold">Profile</h2>
          <Field label="Display name" error={formState.errors.name?.message}>
            <Input {...register('name')} />
          </Field>
          <Field label="UPI ID (optional)" error={formState.errors.upiId?.message} hint="Shared with your groups so friends can pay you. VEYLO never stores payment credentials.">
            <Input {...register('upiId')} placeholder="name@okaxis" autoCapitalize="none" />
          </Field>
          <Button type="submit" variant="forest" className="w-full" disabled={!formState.isDirty}>Save</Button>
        </form>
      </Card>

      <Card className="space-y-4">
        <h2 className="font-bold">Preferences</h2>
        <Field label="Default currency">
          <div className="flex flex-wrap gap-2">
            {CURRENCIES.map((c) => <Chip key={c} active={c === p.currency} onClick={() => p.update({ currency: c })}>{symbol(c).trim()} {c}</Chip>)}
          </div>
        </Field>
        <Field label="Theme">
          <Segmented<ThemeMode>
            options={[{ id: 'light', label: 'Light' }, { id: 'dark', label: 'Dark' }, { id: 'system', label: 'System' }]}
            value={p.theme}
            onChange={(t) => p.update({ theme: t })}
          />
        </Field>
      </Card>

      <Card className="space-y-3">
        <h2 className="font-bold">Data</h2>
        <SyncIndicator compact />
        <p className="text-xs text-muted">
          {cloudEnabled
            ? 'Shared groups sync through VEYLO Cloud when online. Everything is also stored on this device and works offline.'
            : 'Local mode: all data stays in this browser. Shared groups need the cloud keys configured.'}
        </p>
        <div className="grid grid-cols-2 gap-2">
          <Button variant="soft" icon={Download} onClick={async () => downloadFile(`veylo-backup-${new Date().toISOString().slice(0, 10)}.json`, await exportBackup())}>
            Export
          </Button>
          <Button variant="soft" icon={Upload} onClick={() => fileRef.current?.click()}>Import</Button>
        </div>
        <input ref={fileRef} type="file" accept="application/json" hidden onChange={async (e) => {
          const f = e.target.files?.[0]
          e.target.value = ''
          if (!f) return
          try {
            const n = await importBackup(await f.text())
            toast.success(`Imported ${n} group${n === 1 ? '' : 's'} (as local copies)`)
          } catch {
            toast.error("That file isn't a valid VEYLO backup")
          }
        }} />
        <Button variant="danger" icon={Trash2} className="w-full" onClick={() => setConfirm(true)}>Delete all local data</Button>
      </Card>

      <p className="pb-4 text-center text-xs text-muted">VEYLO Web 0.1.0 · Together, expenses made effortless.</p>

      <Sheet open={confirm} onClose={() => setConfirm(false)} title="Delete all local data?">
        <p className="text-sm text-muted">All groups, expenses and settlements are removed from this browser. Other members keep their copies, and shared groups stay in the cloud.</p>
        <div className="mt-5 grid grid-cols-2 gap-2">
          <Button variant="soft" onClick={() => setConfirm(false)}>Cancel</Button>
          <Button variant="danger" onClick={async () => {
            await deleteAllData()
            setConfirm(false)
            toast.success('All local data deleted')
          }}>Delete</Button>
        </div>
      </Sheet>
    </div>
  )
}
