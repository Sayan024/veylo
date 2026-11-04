import { formatMoney } from './money'

export type SplitMethod = 'equal' | 'exact' | 'percent' | 'shares'

export const SPLIT_METHODS: { id: SplitMethod; label: string }[] = [
  { id: 'equal', label: 'Equally' },
  { id: 'exact', label: 'Exact amounts' },
  { id: 'percent', label: 'Percentage' },
  { id: 'shares', label: 'Shares' },
]

/**
 * One row of the split editor.
 * value: exact → minor units, percent → percentage (e.g. 33.33), shares → weight. Ignored for equal.
 */
export interface SplitInput {
  memberId: string
  included: boolean
  value: number
}

export type SplitResult = { ok: true; shares: Record<string, number> } | { ok: false; error: string }

/**
 * Splits `total` in proportion to integer `weights` (largest-remainder method).
 * Pure integer math; ties broken by input order, so results are deterministic
 * and always sum to exactly `total`.
 */
export function distribute(total: number, weights: { id: string; w: bigint }[]): Record<string, number> {
  const sumW = weights.reduce((s, x) => s + x.w, 0n)
  const out: Record<string, number> = {}
  if (sumW <= 0n) return out
  const T = BigInt(total)
  const rems: { i: number; rem: bigint }[] = []
  let assigned = 0n
  weights.forEach((x, i) => {
    const q = (T * x.w) / sumW
    out[x.id] = Number(q)
    assigned += q
    rems.push({ i, rem: (T * x.w) % sumW })
  })
  let left = Number(T - assigned)
  rems.sort((a, b) => (b.rem === a.rem ? a.i - b.i : b.rem > a.rem ? 1 : -1))
  for (let k = 0; left > 0; k++, left--) {
    const r = rems[k % rems.length]
    if (weights[r.i].w === 0n) continue
    out[weights[r.i].id] += 1
  }
  return out
}

/** Converts a decimal like 33.335 into an integer scaled by 10^6 without float drift. */
function scaled(v: number): bigint {
  return BigInt(Math.round(v * 1_000_000))
}

export function calculateSplit(total: number, method: SplitMethod, inputs: SplitInput[], currency = 'INR'): SplitResult {
  if (!Number.isSafeInteger(total) || total <= 0) return { ok: false, error: 'Enter an amount greater than zero' }
  const inc = inputs.filter((i) => i.included)
  if (inc.length === 0) return { ok: false, error: 'Select at least one person' }
  if (inc.some((i) => !Number.isFinite(i.value) || i.value < 0)) return { ok: false, error: "Values can't be negative" }

  switch (method) {
    case 'equal':
      return { ok: true, shares: distribute(total, inc.map((i) => ({ id: i.memberId, w: 1n }))) }

    case 'shares': {
      const w = inc.map((i) => ({ id: i.memberId, w: scaled(i.value) }))
      if (w.every((x) => x.w === 0n)) return { ok: false, error: 'Give at least one person a share' }
      return { ok: true, shares: distribute(total, w) }
    }

    case 'percent': {
      const w = inc.map((i) => ({ id: i.memberId, w: scaled(i.value) }))
      const sum = w.reduce((s, x) => s + x.w, 0n)
      if (sum !== 100_000_000n) {
        const shown = (Number(sum) / 1_000_000).toFixed(2).replace(/\.?0+$/, '')
        return { ok: false, error: `Percentages add up to ${shown}%, need 100%` }
      }
      return { ok: true, shares: distribute(total, w) }
    }

    case 'exact': {
      if (inc.some((i) => !Number.isSafeInteger(i.value))) return { ok: false, error: 'Amounts must be in whole paise' }
      const shares = Object.fromEntries(inc.map((i) => [i.memberId, i.value]))
      const diff = total - inc.reduce((s, i) => s + i.value, 0)
      if (diff > 0) return { ok: false, error: `${formatMoney(diff, currency)} left to assign` }
      if (diff < 0) return { ok: false, error: `${formatMoney(-diff, currency)} over the total` }
      return { ok: true, shares }
    }
  }
}
