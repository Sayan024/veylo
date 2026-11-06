import { describe, expect, it } from 'vitest'
import { decimalString, formatMoney, parseMoney, plainMoney } from './money'
import { calculateSplit, distribute } from './split'
import { balances, memberTotals, minimizeTransfers } from './settle'
import { detectTheme } from './theme'

describe('trip style', () => {
  it('detects from destination like the Android app', () => {
    expect(detectTheme('Darjeeling, West Bengal', 'Trip', false)).toBe('hills')
    expect(detectTheme('Goa', 'Friends', false)).toBe('beach')
    expect(detectTheme('Goa', 'Sales conference', false)).toBe('business')
    expect(detectTheme('Seattle', '', false)).toBe('vacation')
    expect(detectTheme('Blue Tokai', 'Weekend', true)).toBe('cafe')
  })
})

const ok = (r: ReturnType<typeof calculateSplit>) => {
  if (!r.ok) throw new Error(r.error)
  return r.shares
}
const sum = (o: Record<string, number>) => Object.values(o).reduce((a, b) => a + b, 0)

describe('money', () => {
  it('parses without float drift', () => {
    expect(parseMoney('1,200.5')).toBe(120050)
    expect(parseMoney('0.1')).toBe(10)
    expect(parseMoney('19.99')).toBe(1999)
    expect(parseMoney('1.005')).toBe(101)
    expect(parseMoney('.5')).toBe(50)
    expect(parseMoney('abc')).toBeNull()
    expect(parseMoney('-5')).toBeNull()
    expect(parseMoney('')).toBeNull()
  })
  it('formats with Indian grouping', () => {
    expect(formatMoney(123456700, 'INR')).toBe('₹12,34,567.00')
    expect(formatMoney(99, 'INR')).toBe('₹0.99')
    expect(formatMoney(-525, 'USD')).toBe('-$5.25')
    expect(formatMoney(100000, 'USD', false)).toBe('$1,000')
  })
  it('round-trips editable text', () => {
    expect(plainMoney(120050)).toBe('1200.5')
    expect(plainMoney(120000)).toBe('1200')
    expect(decimalString(30005)).toBe('300.05')
  })
})

describe('split', () => {
  it('equal split never loses paise and is deterministic', () => {
    const s = ok(calculateSplit(10000, 'equal', ['a', 'b', 'c'].map((id) => ({ memberId: id, included: true, value: 0 }))))
    expect(sum(s)).toBe(10000)
    expect(s).toEqual({ a: 3334, b: 3333, c: 3333 })
  })
  it('excludes members', () => {
    const s = ok(calculateSplit(120000, 'equal', [
      { memberId: 'a', included: true, value: 0 },
      { memberId: 'b', included: false, value: 0 },
      { memberId: 'c', included: true, value: 0 },
    ]))
    expect(s).toEqual({ a: 60000, c: 60000 })
  })
  it('exact must equal total', () => {
    const bad = calculateSplit(1000, 'exact', [
      { memberId: 'a', included: true, value: 400 },
      { memberId: 'b', included: true, value: 500 },
    ])
    expect(bad.ok).toBe(false)
    expect(sum(ok(calculateSplit(1000, 'exact', [
      { memberId: 'a', included: true, value: 400 },
      { memberId: 'b', included: true, value: 600 },
    ])))).toBe(1000)
  })
  it('percent must equal 100 and handles thirds', () => {
    expect(calculateSplit(1000, 'percent', [
      { memberId: 'a', included: true, value: 50 },
      { memberId: 'b', included: true, value: 40 },
    ]).ok).toBe(false)
    const s = ok(calculateSplit(1001, 'percent', [
      { memberId: 'a', included: true, value: 33.333333 },
      { memberId: 'b', included: true, value: 33.333333 },
      { memberId: 'c', included: true, value: 33.333334 },
    ]))
    expect(sum(s)).toBe(1001)
  })
  it('shares are weighted', () => {
    expect(ok(calculateSplit(900, 'shares', [
      { memberId: 'a', included: true, value: 2 },
      { memberId: 'b', included: true, value: 1 },
    ]))).toEqual({ a: 600, b: 300 })
  })
  it('rejects zero amount and empty selection', () => {
    expect(calculateSplit(0, 'equal', [{ memberId: 'a', included: true, value: 0 }]).ok).toBe(false)
    expect(calculateSplit(100, 'equal', [{ memberId: 'a', included: false, value: 0 }]).ok).toBe(false)
  })
  it('distribute handles zero weights', () => {
    expect(distribute(5, [{ id: 'a', w: 0n }, { id: 'b', w: 1n }])).toEqual({ a: 0, b: 5 })
  })
})

describe('settlement', () => {
  const members = ['sayan', 'rahul', 'priya', 'aman']
  const eq = (t: number) => Object.fromEntries(members.map((m) => [m, t / 4]))
  const expenses = [
    { payerId: 'sayan', amount: 120000, shares: eq(120000) },
    { payerId: 'rahul', amount: 40000, shares: eq(40000) },
  ]

  it('net = paid − share', () => {
    const t = memberTotals(members, expenses, [])
    expect(t.sayan).toEqual({ paid: 120000, share: 40000, net: 80000 })
    expect(Object.values(t).reduce((s, x) => s + x.net, 0)).toBe(0)
  })

  it('clears every balance with at most n−1 payments', () => {
    const b = balances(members, expenses, [])
    const t = minimizeTransfers(b)
    expect(t.length).toBeLessThanOrEqual(members.length - 1)
    expect(t.reduce((s, x) => s + x.amount, 0)).toBe(Object.values(b).filter((v) => v > 0).reduce((a, c) => a + c, 0))
    const after = balances(members, expenses, t.map((x) => ({ ...x })))
    expect(Object.values(after).every((v) => v === 0)).toBe(true)
  })

  it('never involves settled members and pairs exact matches', () => {
    const t = minimizeTransfers({ a: 500, b: -500, c: 0, d: 300, e: -300 })
    expect(t).toHaveLength(2)
    expect(t.some((x) => x.fromId === 'c' || x.toId === 'c')).toBe(false)
  })

  it('handles odd paise', () => {
    const t = minimizeTransfers({ a: 1, b: 1, c: -2 })
    expect(t.reduce((s, x) => s + x.amount, 0)).toBe(2)
    expect(t.every((x) => x.fromId === 'c')).toBe(true)
  })
})
