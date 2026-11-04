import { zodResolver } from '@hookform/resolvers/zod'
import { ArrowRight, Lock, WifiOff, Zap } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { z } from 'zod'
import { Button, HillsArt, Logo } from '@/components/ui'
import { useProfile } from '@/lib/profile'

const schema = z.object({ name: z.string().trim().min(1, 'Enter your name').max(30, 'Keep it under 30 characters') })

export function WelcomePage() {
  const update = useProfile((s) => s.update)
  const nav = useNavigate()
  const [params] = useSearchParams()
  const { register, handleSubmit, formState } = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema), mode: 'onChange' })

  const submit = handleSubmit(({ name }) => {
    update({ name, onboarded: true })
    const next = params.get('next')
    nav(next && next.startsWith('/') && !next.startsWith('/welcome') ? next : '/', { replace: true })
  })

  return (
    <div className="relative min-h-screen overflow-hidden bg-forest-deep text-white">
      <HillsArt seed="veylo-welcome" className="absolute inset-0 h-full w-full" />
      <div className="absolute inset-0 bg-gradient-to-b from-forest-deep/30 via-forest-deep/60 to-forest-deep" />
      <div className="relative mx-auto flex min-h-screen max-w-md flex-col px-6 pb-10 pt-16">
        <div className="flex flex-col items-center">
          <Logo size={84} />
          <p className="mt-2 text-3xl font-extrabold tracking-[0.18em]">VEYLO</p>
        </div>
        <div className="flex-1" />
        <div className="animate-rise">
          <h1 className="text-[40px] font-extrabold leading-[1.1] tracking-tight">
            Split. Sync.
            <br />
            <span className="text-lime">Travel Together.</span>
          </h1>
          <p className="mt-3 text-white/75">Track group expenses, split bills and settle up — right from your browser.</p>
          <div className="mt-5 flex flex-wrap gap-2 text-xs font-semibold text-white/80">
            <span className="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1.5"><Lock size={13} /> No account needed</span>
            <span className="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1.5"><WifiOff size={13} /> Works offline</span>
            <span className="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1.5"><Zap size={13} /> Live for everyone</span>
          </div>
          <form onSubmit={submit} className="mt-6">
            <label className="mb-2 block text-xs font-semibold text-white/70">What should friends call you?</label>
            <input
              {...register('name')}
              autoFocus
              autoComplete="given-name"
              placeholder="Your name"
              className="h-14 w-full rounded-[18px] border border-white/20 bg-white/10 px-4 text-base font-semibold text-white outline-none placeholder:text-white/50 focus:border-lime"
            />
            {formState.errors.name && <p className="mt-1 text-xs text-[#FFB4A6]">{formState.errors.name.message}</p>}
            <Button type="submit" size="lg" className="mt-4 w-full" disabled={!formState.isValid}>
              Get Started <ArrowRight size={19} />
            </Button>
          </form>
          <p className="mt-4 text-center text-xs text-white/50">You can start without an account. Your data stays on this device until you share a group.</p>
        </div>
      </div>
    </div>
  )
}
