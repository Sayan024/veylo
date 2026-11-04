package com.veylo.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/** All amounts are stored as Long minor units (paise / cents) to avoid rounding bugs. */
object Money {
    val currencies = listOf("INR", "USD", "EUR", "GBP", "THB", "AED")

    fun symbol(currency: String): String = when (currency) {
        "INR" -> "₹"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "THB" -> "฿"
        "AED" -> "AED "
        else -> "$currency "
    }

    fun format(minor: Long, currency: String = "INR", decimals: Boolean = true): String {
        val absValue = abs(minor)
        val whole = absValue / 100
        val frac = absValue % 100
        val wholeStr = if (currency == "INR") indianGrouping(whole) else "%,d".format(whole)
        val body = if (decimals || frac != 0L) "$wholeStr.${frac.toString().padStart(2, '0')}" else wholeStr
        return (if (minor < 0) "-" else "") + symbol(currency) + body
    }

    /** Minor units → plain editable text, e.g. 120050 → "1200.5". */
    fun plain(minor: Long): String =
        BigDecimal.valueOf(minor).movePointLeft(2).stripTrailingZeros().toPlainString()

    /** Parses user input like "1,200.5" into minor units. Returns null for invalid input. */
    fun parse(text: String): Long? {
        val cleaned = text.replace(",", "").trim()
        if (cleaned.isEmpty()) return null
        val bd = cleaned.toBigDecimalOrNull() ?: return null
        if (bd.signum() < 0) return null
        return try {
            bd.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (e: ArithmeticException) {
            null
        }
    }

    private fun indianGrouping(n: Long): String {
        val s = n.toString()
        if (s.length <= 3) return s
        val last3 = s.takeLast(3)
        var rest = s.dropLast(3)
        val parts = ArrayList<String>()
        while (rest.length > 2) {
            parts.add(0, rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) parts.add(0, rest)
        return parts.joinToString(",") + "," + last3
    }
}
