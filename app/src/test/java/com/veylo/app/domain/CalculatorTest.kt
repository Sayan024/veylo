package com.veylo.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorTest {

    private fun ok(r: SplitResult) = (r as SplitResult.Ok).shares

    @Test fun equalSplitNeverLosesPaise() {
        val s = ok(SplitCalculator.calculate(100_00, SplitMethod.EQUAL,
            listOf(SplitInput("a", true), SplitInput("b", true), SplitInput("c", true))))
        assertEquals(100_00L, s.values.sum())
        assertEquals(setOf(3334L, 3333L), s.values.toSet())
    }

    @Test fun excludedMembersGetNothing() {
        val s = ok(SplitCalculator.calculate(1200_00, SplitMethod.EQUAL,
            listOf(SplitInput("a", true), SplitInput("b", false), SplitInput("c", true))))
        assertEquals(mapOf("a" to 600_00L, "c" to 600_00L), s)
    }

    @Test fun percentMustAddTo100() {
        val r = SplitCalculator.calculate(1000, SplitMethod.PERCENT,
            listOf(SplitInput("a", true, 50.0), SplitInput("b", true, 40.0)))
        assertTrue(r is SplitResult.Error)
        val s = ok(SplitCalculator.calculate(1001, SplitMethod.PERCENT,
            listOf(SplitInput("a", true, 50.0), SplitInput("b", true, 50.0))))
        assertEquals(1001L, s.values.sum())
    }

    @Test fun sharesAreWeighted() {
        val s = ok(SplitCalculator.calculate(900, SplitMethod.SHARES,
            listOf(SplitInput("a", true, 2.0), SplitInput("b", true, 1.0))))
        assertEquals(mapOf("a" to 600L, "b" to 300L), s)
    }

    @Test fun exactMustMatchTotal() {
        val bad = SplitCalculator.calculate(1000, SplitMethod.EXACT,
            listOf(SplitInput("a", true, 400.0), SplitInput("b", true, 500.0)))
        assertTrue(bad is SplitResult.Error)
        val s = ok(SplitCalculator.calculate(1000, SplitMethod.EXACT,
            listOf(SplitInput("a", true, 400.0), SplitInput("b", true, 600.0))))
        assertEquals(1000L, s.values.sum())
    }

    @Test fun balancesAndMinimalSettlement() {
        val members = listOf("sayan", "rahul", "priya", "aman")
        val eq = { total: Long -> members.associateWith { total / 4 } }
        val expenses = listOf(LedgerExpense("sayan", 1200, eq(1200)), LedgerExpense("rahul", 400, eq(400)))
        val b = SettlementCalculator.balances(members, expenses, emptyList())
        assertEquals(0L, b.values.sum())
        assertEquals(800L, b["sayan"])
        val t = SettlementCalculator.minimize(b)
        assertTrue(t.size <= members.size - 1)
        val after = SettlementCalculator.balances(members, expenses,
            t.map { LedgerPayment(it.fromId, it.toId, it.amount) })
        assertTrue(after.values.all { it == 0L })
    }

    @Test fun moneyParsingAndFormatting() {
        assertEquals(120050L, Money.parse("1,200.5"))
        assertNull(Money.parse("abc"))
        assertEquals("₹12,34,567.00", Money.format(1234567_00, "INR"))
        assertEquals("-$5.25", Money.format(-525, "USD"))
    }
}
