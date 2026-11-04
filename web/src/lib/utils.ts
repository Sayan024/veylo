import { clsx, type ClassValue } from 'clsx'
import { decimalString } from './money'

export const cn = (...v: ClassValue[]) => clsx(v)

export const uid = () => crypto.randomUUID()
export const nowIso = () => new Date().toISOString()
export const today = () => new Date().toISOString().slice(0, 10)

export function haptic(ms = 12) {
  try {
    navigator.vibrate?.(ms)
  } catch {
    /* not supported */
  }
}

export function greeting(): string {
  const h = new Date().getHours()
  if (h >= 5 && h < 12) return 'Good morning'
  if (h >= 12 && h < 17) return 'Good afternoon'
  return 'Good evening'
}

export function initials(name: string): string {
  const p = name.trim().split(/\s+/).filter(Boolean)
  if (!p.length) return '?'
  return (p.length === 1 ? p[0][0] : p[0][0] + p[1][0]).toUpperCase()
}

const AVATAR = ['#2B5A40', '#B4683B', '#4A6FA5', '#8A4F7D', '#3F7F74', '#A0522D', '#5B6B2F', '#7A5C3E']
export function hash(s: string): number {
  let h = 0
  for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0
  return Math.abs(h)
}
export const avatarColor = (name: string) => AVATAR[hash(name.toLowerCase()) % AVATAR.length]

export const fmtDate = (iso: string) => new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
export const fmtDateTime = (iso: string) =>
  new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' })
export const fmtTime = (iso: string) => new Date(iso).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })

export function dayLabel(iso: string): string {
  const d = new Date(iso)
  const t = new Date()
  const y = new Date()
  y.setDate(t.getDate() - 1)
  if (d.toDateString() === t.toDateString()) return 'Today'
  if (d.toDateString() === y.toDateString()) return 'Yesterday'
  return d.toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'short' })
}

/** Resizes an image file to a compact JPEG data URL so it fits comfortably in IndexedDB. */
export function compressImage(file: File, max = 1280, quality = 0.78): Promise<string> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    const url = URL.createObjectURL(file)
    img.onload = () => {
      const scale = Math.min(1, max / Math.max(img.width, img.height))
      const c = document.createElement('canvas')
      c.width = Math.round(img.width * scale)
      c.height = Math.round(img.height * scale)
      c.getContext('2d')!.drawImage(img, 0, 0, c.width, c.height)
      URL.revokeObjectURL(url)
      resolve(c.toDataURL('image/jpeg', quality))
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('Could not read image'))
    }
    img.src = url
  })
}

/** Builds a UPI deep link: upi://pay?pa=…&pn=…&am=…&cu=INR&tn=… */
export function upiLink(opts: { upiId: string; name: string; amount: number; note: string; ref?: string }): string {
  const q = new URLSearchParams({
    pa: opts.upiId,
    pn: opts.name,
    am: decimalString(opts.amount),
    cu: 'INR',
    tn: opts.note.slice(0, 40),
  })
  if (opts.ref) q.set('tr', opts.ref.slice(0, 35))
  return `upi://pay?${q.toString()}`
}

export const isValidUpi = (v: string) => /^[a-zA-Z0-9._-]{2,200}@[a-zA-Z]{2,64}$/.test(v.trim())

export function downloadFile(name: string, content: string, type = 'application/json') {
  const a = document.createElement('a')
  a.href = URL.createObjectURL(new Blob([content], { type }))
  a.download = name
  a.click()
  setTimeout(() => URL.revokeObjectURL(a.href), 1000)
}
