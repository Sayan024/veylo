package com.veylo.app.domain

data class Transfer(val fromId: String, val toId: String, val amount: Long)

data class LedgerExpense(val payerId: String, val amount: Long, val shares: Map<String, Long>)
data class LedgerPayment(val fromId: String, val toId: String, val amount: Long)

object SettlementCalculator {

    /** Positive balance = the group owes this member. Negative = this member owes the group. */
    fun balances(
        memberIds: Collection<String>,
        expenses: List<LedgerExpense>,
        payments: List<LedgerPayment>,
    ): Map<String, Long> {
        val b = LinkedHashMap<String, Long>()
        memberIds.forEach { b[it] = 0L }
        for (e in expenses) {
            b[e.payerId] = (b[e.payerId] ?: 0L) + e.amount
            e.shares.forEach { (id, share) -> b[id] = (b[id] ?: 0L) - share }
        }
        for (p in payments) {
            b[p.fromId] = (b[p.fromId] ?: 0L) + p.amount
            b[p.toId] = (b[p.toId] ?: 0L) - p.amount
        }
        return b
    }

    /** Greedy largest-debtor → largest-creditor matching. Produces at most n-1 transfers. */
    fun minimize(balances: Map<String, Long>): List<Transfer> {
        class Acc(val id: String, var amount: Long)
        val creditors = balances.filter { it.value > 0 }.map { Acc(it.key, it.value) }.toMutableList()
        val debtors = balances.filter { it.value < 0 }.map { Acc(it.key, -it.value) }.toMutableList()
        val out = ArrayList<Transfer>()
        while (creditors.isNotEmpty() && debtors.isNotEmpty()) {
            creditors.sortByDescending { it.amount }
            debtors.sortByDescending { it.amount }
            val c = creditors.first()
            val d = debtors.first()
            val x = minOf(c.amount, d.amount)
            out.add(Transfer(d.id, c.id, x))
            c.amount -= x
            d.amount -= x
            if (c.amount == 0L) creditors.removeAt(0)
            if (d.amount == 0L) debtors.removeAt(0)
        }
        return out
    }
}
