package com.veylo.app.domain

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

enum class SplitMethod(val label: String) {
    EQUAL("Equally"),
    EXACT("Exact amounts"),
    PERCENT("Percentage"),
    SHARES("Shares");

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: EQUAL
    }
}

/**
 * One row of the split editor.
 * [value] means: EXACT → minor units, PERCENT → percent, SHARES → weight. Ignored for EQUAL.
 */
data class SplitInput(val memberId: String, val included: Boolean, val value: Double = 0.0)

sealed interface SplitResult {
    data class Ok(val shares: Map<String, Long>) : SplitResult
    data class Error(val message: String) : SplitResult
}

object SplitCalculator {

    fun calculate(
        total: Long,
        method: SplitMethod,
        inputs: List<SplitInput>,
        currency: String = "INR",
    ): SplitResult {
        if (total <= 0) return SplitResult.Error("Enter an amount greater than zero")
        val included = inputs.filter { it.included }
        if (included.isEmpty()) return SplitResult.Error("Select at least one person")
        if (included.any { it.value < 0 || it.value.isNaN() }) {
            return SplitResult.Error("Values can't be negative")
        }

        return when (method) {
            SplitMethod.EQUAL -> SplitResult.Ok(distribute(total, included.map { it.memberId to 1.0 }))

            SplitMethod.SHARES -> {
                if (included.sumOf { it.value } <= 0.0) SplitResult.Error("Give at least one person a share")
                else SplitResult.Ok(distribute(total, included.map { it.memberId to it.value }))
            }

            SplitMethod.PERCENT -> {
                val sum = included.sumOf { it.value }
                if (abs(sum - 100.0) > 0.01) {
                    val shown = String.format("%.2f", sum).trimEnd('0').trimEnd('.')
                    SplitResult.Error("Percentages add up to $shown%, need 100%")
                } else {
                    SplitResult.Ok(distribute(total, included.map { it.memberId to it.value }))
                }
            }

            SplitMethod.EXACT -> {
                val shares = included.associate { it.memberId to it.value.roundToLong() }
                val diff = total - shares.values.sum()
                when {
                    diff > 0 -> SplitResult.Error("${Money.format(diff, currency)} left to assign")
                    diff < 0 -> SplitResult.Error("${Money.format(-diff, currency)} over the total")
                    else -> SplitResult.Ok(shares)
                }
            }
        }
    }

    /**
     * Splits [total] by [weights] using the largest-remainder method so the
     * shares always add up to exactly [total] (no lost paise).
     */
    fun distribute(total: Long, weights: List<Pair<String, Double>>): Map<String, Long> {
        val sumW = weights.sumOf { it.second }
        if (weights.isEmpty() || sumW <= 0.0) return emptyMap()
        val raw = weights.map { it.first to total.toDouble() * it.second / sumW }
        val result = LinkedHashMap<String, Long>()
        raw.forEach { (id, v) -> result[id] = floor(v).toLong() }
        var remainder = total - result.values.sum()
        val order = raw.indices
            .filter { raw[it].second > 0.0 }
            .sortedWith(compareByDescending<Int> { raw[it].second - floor(raw[it].second) }.thenBy { it })
        var i = 0
        while (remainder > 0 && order.isNotEmpty()) {
            val id = raw[order[i % order.size]].first
            result[id] = result.getValue(id) + 1
            remainder--
            i++
        }
        return result
    }
}
