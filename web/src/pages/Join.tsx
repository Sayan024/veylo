import { ArrowLeft, Check, CloudOff, QrCode, ScanLine, UserPlus } from 'lucide-react'
import QrScanner from 'qr-scanner'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { Avatar, Button, Card, EmptyState, HillsArt, IconButton, Sheet } from '@/components/ui'
import { cloudEnabled } from '@/lib/cloud'
import { useOnline } from '@/lib/hooks'
import { useProfile } from '@/lib/profile'
import { joinGroup, previewInvite, type InvitePreview } from '@/lib/sync'
import { cn, fmtDate } from '@/lib/utils'

const CODE_RE = /^[A-HJ-NP-Z2-9]{6}$/

function extractCode(text: string): string | null {
  try {
    const u = new URL(text)
    const c = u.searchParams.get('code')
    if (c) return c.toUpperCase()
  } catch {
    /* not a URL */
  }
  const m = text.toUpperCase().match(/[A-HJ-NP-Z2-9]{6}/)
  return m ? m[0] : null
}

export function JoinPage() {
  const nav = useNavigate()
  const [params] = useSearchParams()
  const online = useOnline()
  const name = useProfile((s) => s.name)
  const [code, setCode] = useState((params.get('code') ?? '').toUpperCase().slice(0, 6))
  const [preview, setPreview] = useState<InvitePreview | null>(null)
  const [claim, setClaim] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [scanning, setScanning] = useState(false)

  const lookup = useCallback(async (c: string) => {
    if (!CODE_RE.test(c)) return
    setBusy(true)
    setPreview(null)
    try {
      const p = await previewInvite(c)
      setPreview(p)
      const mine = p.members.find((m) => !m.claimed && m.name.trim().toLowerCase() === name.trim().toLowerCase())
      setClaim(mine?.id ?? null)
    } catch (e) {
      toast.error(/not found/i.test((e as Error).message) ? 'No group found for that code' : (e as Error).message)
    } finally {
      setBusy(false)
    }
  }, [name])

  useEffect(() => {
    if (cloudEnabled && online && CODE_RE.test(code) && !preview) void lookup(code)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const join = async () => {
    if (!preview) return
    setBusy(true)
    try {
      const id = await joinGroup(code, claim)
      toast.success(`Joined ${preview.name}`)
      nav(`/g/${id}`, { replace: true })
    } catch (e) {
      toast.error((e as Error).message)
      setBusy(false)
    }
  }

  return (
    <div className="pb-10">
      <div className="relative h-48 overflow-hidden lg:mx-8 lg:mt-8 lg:rounded-[28px]">
        <HillsArt seed={preview?.group_id ?? 'join'} className="absolute inset-0 h-full w-full" />
        <div className="absolute inset-0 bg-gradient-to-b from-black/30 to-forest-deep/80" />
        <div className="absolute inset-x-0 top-0 p-4 pt-safe"><IconButton icon={ArrowLeft} label="Back" dark onClick={() => nav(-1)} /></div>
        <div className="absolute bottom-5 left-5 text-white">
          <h1 className="text-3xl font-extrabold">Join Group</h1>
          <p className="text-sm text-white/80">Scan a QR, open an invite link, or type the code.</p>
        </div>
      </div>

      <div className="space-y-4 px-5 pt-5 lg:px-8">
        {!cloudEnabled ? (
          <EmptyState icon={CloudOff} title="Sharing isn't set up" body="This copy of VEYLO runs in local mode. Connect a free Supabase project (see README) to share groups." />
        ) : (
          <>
            <Card className="space-y-4">
              <label className="block text-xs font-semibold text-muted">6-character invite code</label>
              <input
                value={code}
                onChange={(e) => {
                  setCode(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 6))
                  setPreview(null)
                }}
                placeholder="ABC123"
                autoCapitalize="characters"
                className="h-16 w-full rounded-2xl border border-line bg-bg text-center text-3xl font-extrabold tracking-[0.4em] outline-none focus:border-forest dark:focus:border-lime"
              />
              <div className="grid grid-cols-2 gap-2">
                <Button variant="soft" icon={ScanLine} onClick={() => setScanning(true)}>Scan QR</Button>
                <Button icon={QrCode} disabled={!CODE_RE.test(code) || busy || !online} onClick={() => lookup(code)}>{busy && !preview ? 'Checking…' : 'Find group'}</Button>
              </div>
              {!online && <p className="text-xs font-semibold text-warn">You're offline. Joining needs an internet connection.</p>}
            </Card>

            {preview && (
              <Card className="space-y-4 animate-rise">
                <div>
                  <span className="rounded-full bg-lime px-2.5 py-1 text-[10px] font-extrabold tracking-wider text-forest-deep">{preview.type.toUpperCase()}</span>
                  <h2 className="mt-2 text-2xl font-extrabold">{preview.name}</h2>
                  <p className="text-sm text-muted">
                    {[preview.destination, preview.start_date && fmtDate(preview.start_date), `${preview.members.length} members`, preview.currency].filter(Boolean).join(' · ')}
                  </p>
                </div>
                <div>
                  <p className="mb-2 text-sm font-bold">Which one is you?</p>
                  <div className="space-y-2">
                    {preview.members.filter((m) => !m.claimed).map((m) => (
                      <button key={m.id} onClick={() => setClaim(m.id)} className={cn('flex w-full items-center gap-3 rounded-2xl border-2 p-3 text-left transition', claim === m.id ? 'border-forest bg-lime-tint dark:border-lime' : 'border-line')}>
                        <Avatar name={m.name} size={34} />
                        <span className="flex-1 font-semibold">{m.name}</span>
                        {claim === m.id && <Check size={18} className="text-forest dark:text-lime" />}
                      </button>
                    ))}
                    <button onClick={() => setClaim(null)} className={cn('flex w-full items-center gap-3 rounded-2xl border-2 p-3 text-left transition', claim === null ? 'border-forest bg-lime-tint dark:border-lime' : 'border-line')}>
                      <span className="grid h-[34px] w-[34px] place-items-center rounded-full bg-cardalt"><UserPlus size={16} /></span>
                      <span className="flex-1 font-semibold">I'm new — join as “{name}”</span>
                      {claim === null && <Check size={18} className="text-forest dark:text-lime" />}
                    </button>
                  </div>
                  {preview.members.some((m) => m.claimed) && (
                    <p className="mt-2 text-xs text-muted">Already joined: {preview.members.filter((m) => m.claimed).map((m) => m.name).join(', ')}</p>
                  )}
                </div>
                <Button size="lg" className="w-full" disabled={busy} onClick={join}>{busy ? 'Joining…' : `Join ${preview.name}`}</Button>
              </Card>
            )}
          </>
        )}
      </div>

      <ScannerSheet
        open={scanning}
        onClose={() => setScanning(false)}
        onResult={(text) => {
          const c = extractCode(text)
          setScanning(false)
          if (!c) return toast.error("That QR isn't a VEYLO invite")
          setCode(c)
          void lookup(c)
        }}
      />
    </div>
  )
}

function ScannerSheet({ open, onClose, onResult }: { open: boolean; onClose: () => void; onResult: (text: string) => void }) {
  const video = useRef<HTMLVideoElement>(null)
  const [error, setError] = useState<string | null>(null)
  const done = useRef(false)
  const cb = useRef(onResult)
  cb.current = onResult

  useEffect(() => {
    if (!open || !video.current) return
    done.current = false
    setError(null)
    const scanner = new QrScanner(video.current, (r) => {
      if (done.current) return
      done.current = true
      cb.current(r.data)
    }, { highlightScanRegion: true, preferredCamera: 'environment', returnDetailedScanResult: true })
    scanner.start().catch(() => setError('Camera unavailable. Allow camera access, or type the code instead.'))
    return () => {
      scanner.stop()
      scanner.destroy()
    }
  }, [open])

  return (
    <Sheet open={open} onClose={onClose} title="Scan invite QR">
      <div className="relative aspect-square overflow-hidden rounded-[22px] bg-black">
        <video ref={video} className="h-full w-full object-cover" muted playsInline />
      </div>
      {error ? <p className="mt-3 text-sm font-semibold text-owe">{error}</p> : <p className="mt-3 text-center text-sm text-muted">Point your camera at the group's QR code.</p>}
    </Sheet>
  )
}
