export interface LedgerExpense {
  payerId: string
  amount: number
  shares: Record<string, number>
}
export interface LedgerPayment {
  fromId: string
  toId: string
  amount: number
}
export interface Transfer {
  fromId: string
  toId: string
  amount: number
}

export interface MemberTotals {
  paid: number
  share: number
  /** paid − share ± settlements. Positive = receives money. */
  net: number
}

/** Net balance = total paid − total share (adjusted by settlements already made). */
export function memberTotals(memberIds: string[], expenses: LedgerExpense[], payments: LedgerPayment[]): Record<string, MemberTotals> {
  const t: Record<string, MemberTotals> = {}
  const get = (id: string) => (t[id] ??= { paid: 0, share: 0, net: 0 })
  memberIds.forEach(get)
  for (const e of expenses) {
    get(e.payerId).paid += e.amount
    get(e.payerId).net += e.amount
    for (const [id, s] of Object.entries(e.shares)) {
      get(id).share += s
      get(id).net -= s
    }
  }
  for (const p of payments) {
    get(p.fromId).net += p.amount
    get(p.toId).net -= p.amount
  }
  return t
}

export function balances(memberIds: string[], expenses: LedgerExpense[], payments: LedgerPayment[]): Record<string, number> {
  const t = memberTotals(memberIds, expenses, payments)
  return Object.fromEntries(Object.entries(t).map(([k, v]) => [k, v.net]))
}

/**
 * Minimises the number of payments.
 * 1. Members with zero balance are ignored (never pay or receive).
 * 2. Exact matches (debtor owes exactly what a creditor is owed) are paired first.
 * 3. Remaining balances are matched greedily, largest debtor → largest creditor.
 * Each step zeroes at least one member, so there are at most n−1 payments and
 * no circular transfers (money only flows from debtors to creditors).
 */
export function minimizeTransfers(bal: Record<string, number>): Transfer[] {
  const cred = Object.entries(bal).filter(([, v]) => v > 0).map(([id, v]) => ({ id, v }))
  const debt = Object.entries(bal).filter(([, v]) => v < 0).map(([id, v]) => ({ id, v: -v }))
  const out: Transfer[] = []

  for (const d of debt) {
    const c = cred.find((x) => x.v === d.v && x.v > 0)
    if (c) {
      out.push({ fromId: d.id, toId: c.id, amount: d.v })
      c.v = 0
      d.v = 0
    }
  }

  const byAmount = (a: { id: string; v: number }, b: { id: string; v: number }) => b.v - a.v || a.id.localeCompare(b.id)
  for (;;) {
    const cs = cred.filter((x) => x.v > 0).sort(byAmount)
    const ds = debt.filter((x) => x.v > 0).sort(byAmount)
    if (!cs.length || !ds.length) break
    const x = Math.min(cs[0].v, ds[0].v)
    out.push({ fromId: ds[0].id, toId: cs[0].id, amount: x })
    cs[0].v -= x
    ds[0].v -= x
  }
  return out
}
