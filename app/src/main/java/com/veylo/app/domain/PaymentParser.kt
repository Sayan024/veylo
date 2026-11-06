package com.veylo.app.domain

/** Result of a UPI payment, from the UPI app's response or read from a payment screenshot. */
data class PaymentInfo(
    val status: PayStatus,
    val txnId: String? = null,
    /** Minor units (paise), when known. */
    val amount: Long? = null,
)

enum class PayStatus(val key: String, val label: String) {
    SUCCESS("success", "Paid"),
    PENDING("pending", "Pending"),
    FAILED("failed", "Failed"),
    UNKNOWN("unknown", "Not sure");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key?.lowercase() } ?: SUCCESS
    }
}

object PaymentParser {

    /**
     * Parses the string a UPI app returns to the calling app, e.g.
     * "txnId=AXI123&responseCode=00&Status=SUCCESS&txnRef=VEYLO1&ApprovalRefNo=412345678901".
     * Keys vary in case between apps.
     */
    fun parseUpiResponse(response: String?): PaymentInfo? {
        if (response.isNullOrBlank()) return null
        val map = response.split('&').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else part.substring(0, i).trim().lowercase() to java.net.URLDecoder.decode(part.substring(i + 1).trim(), "UTF-8")
        }.toMap()
        if (map.isEmpty()) return null
        val status = when (map["status"]?.uppercase()) {
            "SUCCESS" -> PayStatus.SUCCESS
            "SUBMITTED", "PENDING" -> PayStatus.PENDING
            "FAILURE", "FAILED" -> PayStatus.FAILED
            else -> if (map["responsecode"] == "00") PayStatus.SUCCESS else PayStatus.UNKNOWN
        }
        // Prefer the bank reference (UTR) people can look up, then the app's transaction id.
        val txn = listOf("approvalrefno", "approvalref", "txnid", "txnref").firstNotNullOfOrNull { k -> map[k]?.takeIf { it.isNotBlank() && it != "null" } }
        return PaymentInfo(status, txn)
    }

    private val AMOUNT = Regex("""(?:₹|Rs\.?|INR)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val LABELLED_ID = Regex(
        """(?:UPI\s*(?:transaction|txn|ref(?:erence)?)\s*(?:id|no\.?|number)?|UTR(?:\s*(?:no\.?|number))?|Transaction\s*(?:id|no\.?|number)|Txn\s*(?:id|no\.?)|Ref(?:erence)?\s*(?:id|no\.?|number))\s*[:#-]?\s*([A-Z0-9]{8,35})""",
        RegexOption.IGNORE_CASE,
    )
    private val TWELVE_DIGITS = Regex("""(?<![0-9])([0-9]{12})(?![0-9])""")
    /** A line that is only an amount, allowing up to two stray symbols in front (a misread ₹). */
    private val AMOUNT_LINE = Regex("""[^0-9A-Za-z\s]{0,2}\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]{1,9}(?:\.[0-9]{1,2})?)""")

    /** Extracts status, transaction id and amount from the text of a payment-app screenshot. */
    fun parseScreenshotText(text: String): PaymentInfo {
        val t = text.replace(' ', ' ')
        val lower = t.lowercase()
        val status = when {
            Regex("""\b(failed|failure|declined|unsuccessful|rejected)\b""").containsMatchIn(lower) -> PayStatus.FAILED
            Regex("""\b(pending|processing|submitted|in progress)\b""").containsMatchIn(lower) -> PayStatus.PENDING
            Regex("""\b(successful|success|paid|completed|sent|money sent|payment done)\b""").containsMatchIn(lower) -> PayStatus.SUCCESS
            else -> PayStatus.UNKNOWN
        }
        // UPI bank references (UTR) are 12 digits; payment apps label them differently.
        val labelled = LABELLED_ID.findAll(t).map { it.groupValues[1] }.toList()
        val txn = labelled.firstOrNull { TWELVE_DIGITS.matches(it) }
            ?: labelled.firstOrNull { it.any(Char::isDigit) }
            ?: TWELVE_DIGITS.find(t)?.groupValues?.get(1)
        // On-device text recognition often misreads "₹", so also accept the first short line that is just an
        // amount (payment apps show it big at the top), e.g. "100", "₹ 1,250.50" or "%100".
        val amount = AMOUNT.findAll(t).mapNotNull { Money.parse(it.groupValues[1]) }.firstOrNull { it > 0 }
            ?: t.lines().asSequence().map { it.trim() }
                .mapNotNull { AMOUNT_LINE.matchEntire(it)?.groupValues?.get(1) }
                .filterNot { TWELVE_DIGITS.matches(it) }
                .mapNotNull { Money.parse(it) }
                .firstOrNull { it in 1..10_000_000_00L }
        return PaymentInfo(status, txn, amount)
    }
}
