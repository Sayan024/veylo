/** All money is stored as integer minor units (paise / cents). Never floats. */

export const CURRENCIES = ['INR', 'USD', 'EUR', 'GBP', 'THB', 'AED'] as const
export type Currency = (typeof CURRENCIES)[number]

const SYMBOLS: Record<string, string> = { INR: '₹', USD: '$', EUR: '€', GBP: '£', THB: '฿', AED: 'AED ' }

export const symbol = (c: string) => SYMBOLS[c] ?? `${c} `

function indianGrouping(n: bigint): string {
  const s = n.toString()
  if (s.length <= 3) return s
  const last3 = s.slice(-3)
  let rest = s.slice(0, -3)
  const parts: string[] = []
  while (rest.length > 2) {
    parts.unshift(rest.slice(-2))
    rest = rest.slice(0, -2)
  }
  if (rest) parts.unshift(rest)
  return `${parts.join(',')},${last3}`
}

function westernGrouping(n: bigint): string {
  return n.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

/** 123456 → "₹1,234.56". `decimals: false` drops ".00" for whole amounts. */
export function formatMoney(minor: number, currency = 'INR', decimals = true): string {
  if (!Number.isSafeInteger(minor)) throw new Error('Money must be an integer number of minor units')
  const neg = minor < 0
  const abs = BigInt(Math.abs(minor))
  const whole = abs / 100n
  const frac = abs % 100n
  const wholeStr = currency === 'INR' ? indianGrouping(whole) : westernGrouping(whole)
  const body = decimals || frac !== 0n ? `${wholeStr}.${frac.toString().padStart(2, '0')}` : wholeStr
  return `${neg ? '-' : ''}${symbol(currency)}${body}`
}

/**
 * Parses user text ("1,200.5") into minor units without float math.
 * Returns null for anything invalid or negative.
 */
export function parseMoney(text: string): number | null {
  const t = text.replace(/,/g, '').trim()
  if (!/^\d+(\.\d*)?$|^\.\d+$/.test(t)) return null
  const [w = '0', f = ''] = t.split('.')
  const frac2 = (f + '000').slice(0, 3) // keep 3 digits to round half-up
  let minor = BigInt(w || '0') * 100n + BigInt(frac2.slice(0, 2))
  if (Number(frac2[2]) >= 5) minor += 1n
  if (minor > BigInt(Number.MAX_SAFE_INTEGER)) return null
  return Number(minor)
}

/** Minor units → editable text: 120050 → "1200.5". */
export function plainMoney(minor: number): string {
  const whole = Math.trunc(minor / 100)
  const frac = Math.abs(minor % 100)
  if (frac === 0) return String(whole)
  return `${whole}.${frac.toString().padStart(2, '0').replace(/0$/, '')}`
}

/** Minor units → "1200.50" for UPI links. */
export function decimalString(minor: number): string {
  return `${Math.trunc(minor / 100)}.${Math.abs(minor % 100).toString().padStart(2, '0')}`
}
